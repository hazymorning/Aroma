package app.aroma.data.repository

import app.aroma.core.model.ShoppingItem
import app.aroma.core.model.ShoppingLabel
import app.aroma.core.model.ShoppingList
import app.aroma.core.model.ShoppingListInfo
import app.aroma.data.AromaError
import app.aroma.data.JsonCache
import app.aroma.data.Loadable
import app.aroma.data.local.CacheDao
import app.aroma.data.local.CacheEntity
import app.aroma.data.local.PendingChangeDao
import app.aroma.data.local.PendingChangeEntity
import app.aroma.data.mealieCall
import app.aroma.data.remote.MealieJson
import app.aroma.data.remote.dto.AddRecipeToListDto
import app.aroma.data.remote.dto.CreateShoppingListDto
import app.aroma.data.remote.dto.FoodDto
import app.aroma.data.remote.dto.LabelDto
import app.aroma.data.remote.dto.LabelSettingDto
import app.aroma.data.remote.dto.ShoppingListSummaryDto
import app.aroma.data.remote.dto.UnitDto
import app.aroma.data.remote.toModel
import app.aroma.data.session.SessionManager
import app.aroma.data.toAromaError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Shopping lists work offline: every change is applied to the cached list at once and
 * queued for Mealie. [sync] replays the queue in order once the server is reachable.
 */
class ShoppingRepository(
    private val sessions: SessionManager,
    private val cacheDao: CacheDao,
    private val cache: JsonCache,
    private val pending: PendingChangeDao,
    private val requestSync: () -> Unit,
) {
    private val editLock = Mutex()
    private val syncLock = Mutex()

    fun lists(): Flow<Loadable<List<ShoppingListInfo>>> =
        cache
            .stream(LISTS_KEY, ListSerializer(ShoppingListSummaryDto.serializer())) { sessions.api().shoppingLists().items }
            .map { loadable -> Loadable(loadable.data.map { it.toInfo() }, loadable.isRefreshing, loadable.error) }

    /** The cached list; local edits show up here immediately. */
    fun observe(listId: String): Flow<ShoppingList?> =
        cacheDao
            .observe(key(listId))
            .map { entry -> entry?.let { parse(MealieJson.decodeFromString(ShoppingListDto.serializer(), it.json)) } }
            .flowOn(Dispatchers.Default)

    fun pendingCount(listId: String): Flow<Int> = pending.countFor(listId)

    /** Pushes queued changes, then loads the server's version unless changes are still waiting. */
    suspend fun refresh(listId: String) {
        sync()
        storeIfSettled(listId, mealieCall { sessions.api().shoppingList(listId) })
    }

    suspend fun add(listId: String, text: String) =
        edit(listId) { list ->
            val items = list.items()
            val position = (items.filterNot { it.bool("checked") }.maxOfOrNull { it.int("position") } ?: -1) + 1
            val item =
                buildJsonObject {
                    put("id", UUID.randomUUID().toString())
                    put("shoppingListId", listId)
                    put("note", text.trim())
                    put("quantity", 0)
                    put("checked", false)
                    put("position", position)
                }
            list.withItems(items + item) to listOf(Change.Create(item))
        }

    suspend fun toggle(listId: String, itemId: String) =
        updateItem(listId, itemId) { item ->
            item.with("checked", JsonPrimitive(!item.bool("checked")))
        }

    /** Replaces the item's text; structured amounts are dropped because the text now says it all. */
    suspend fun rename(listId: String, itemId: String, text: String) =
        updateItem(listId, itemId) { item ->
            item
                .with("note", JsonPrimitive(text.trim()))
                .with("quantity", JsonPrimitive(0))
                .with("food", JsonNull)
                .with("foodId", JsonNull)
                .with("unit", JsonNull)
                .with("unitId", JsonNull)
        }

    suspend fun delete(listId: String, itemId: String): JsonObject? {
        var removed: JsonObject? = null
        edit(listId) { list ->
            val items = list.items()
            removed = items.firstOrNull { it.string("id") == itemId }
            val remaining = items.filterNot { it.string("id") == itemId }
            list.withItems(remaining) to listOfNotNull(removed?.let { Change.Delete(itemId) })
        }
        return removed
    }

    suspend fun restore(listId: String, item: JsonObject) =
        edit(listId) { list ->
            list.withItems(list.items() + item) to listOf(Change.Create(item.forCreate()))
        }

    suspend fun clearChecked(listId: String) =
        edit(listId) { list ->
            val (checked, open) = list.items().partition { it.bool("checked") }
            list.withItems(open) to checked.map { Change.Delete(it.string("id").orEmpty()) }
        }

    suspend fun createList(name: String): String {
        val created = mealieCall { sessions.api().createShoppingList(CreateShoppingListDto(name.trim())) }
        runCatching { refresh(created.id) }
        refreshLists()
        return created.id
    }

    suspend fun renameList(listId: String, name: String) {
        val current = mealieCall { sessions.api().shoppingList(listId) }
        val body = current.with("name", JsonPrimitive(name.trim()))
        storeIfSettled(listId, mealieCall { sessions.api().updateShoppingList(listId, body) })
        refreshLists()
    }

    suspend fun deleteList(listId: String) {
        mealieCall { sessions.api().deleteShoppingList(listId) }
        cacheDao.delete(key(listId))
        refreshLists()
    }

    /** Adds a recipe's ingredients: the raw objects the user picked, or all of them when [ingredients] is null. */
    suspend fun addRecipe(listId: String, recipeId: String, scale: Double, ingredients: List<JsonObject>?) {
        val updated =
            mealieCall {
                sessions.api().addRecipeToList(listId, recipeId, AddRecipeToListDto(scale, ingredients))
            }
        storeIfSettled(listId, updated)
    }

    private suspend fun refreshLists() {
        runCatching {
            val lists = sessions.api().shoppingLists().items
            cache.write(LISTS_KEY, ListSerializer(ShoppingListSummaryDto.serializer()), lists)
        }
    }

    /** Replays queued changes in order. Returns false if something is still waiting for the server. */
    suspend fun sync(): Boolean =
        syncLock.withLock {
            for (change in pending.all()) {
                try {
                    val payload = MealieJson.parseToJsonElement(change.payload).jsonObject
                    when (change.kind) {
                        KIND_CREATE -> sessions.api().createShoppingItem(payload)
                        KIND_UPDATE -> sessions.api().updateShoppingItem(change.itemId, payload)
                        KIND_DELETE -> sessions.api().deleteShoppingItem(change.itemId)
                    }
                    pending.delete(change.id)
                } catch (error: Throwable) {
                    when (val failure = error.toAromaError()) {
                        is AromaError.NotFound -> pending.delete(change.id)
                        is AromaError.Server -> if (failure.code in 400..499) pending.delete(change.id) else return@withLock false
                        else -> return@withLock false
                    }
                }
            }
            true
        }

    private suspend fun updateItem(listId: String, itemId: String, change: (JsonObject) -> JsonObject) =
        edit(listId) { list ->
            var updated: JsonObject? = null
            val items =
                list.items().map { item ->
                    if (item.string("id") == itemId) change(item).also { updated = it } else item
                }
            list.withItems(items) to listOfNotNull(updated?.let { Change.Update(itemId, it) })
        }

    private suspend fun edit(listId: String, transform: (JsonObject) -> Pair<JsonObject, List<Change>>) {
        editLock.withLock {
            val entry = cacheDao.get(key(listId)) ?: throw AromaError.NotFound()
            val (list, changes) = withContext(Dispatchers.Default) { transform(MealieJson.parseToJsonElement(entry.json).jsonObject) }
            store(listId, list)
            val now = System.currentTimeMillis()
            changes.forEach { change ->
                pending.insert(
                    PendingChangeEntity(
                        listId = listId,
                        itemId = change.itemId,
                        kind = change.kind,
                        payload = change.payload.toString(),
                        createdAt = now,
                    ),
                )
            }
        }
        requestSync()
    }

    private suspend fun store(listId: String, list: JsonObject) {
        val json = withContext(Dispatchers.Default) { list.toString() }
        cacheDao.put(CacheEntity(key(listId), json, System.currentTimeMillis()))
    }

    /** Keeps the server's copy of a list, unless changes made here still wait: that copy would hide them. */
    private suspend fun storeIfSettled(listId: String, list: JsonObject) =
        editLock.withLock {
            if (!pending.hasAny(listId)) store(listId, list)
        }

    private sealed class Change(val kind: String, val itemId: String, val payload: JsonObject) {
        class Create(item: JsonObject) : Change(KIND_CREATE, item.string("id").orEmpty(), item)

        class Update(itemId: String, item: JsonObject) : Change(KIND_UPDATE, itemId, item)

        class Delete(itemId: String) : Change(KIND_DELETE, itemId, JsonObject(emptyMap()))
    }

    companion object {
        private const val LISTS_KEY = "shopping:lists"
        private const val KIND_CREATE = "create"
        private const val KIND_UPDATE = "update"
        private const val KIND_DELETE = "delete"

        private fun key(listId: String) = "shopping:list:$listId"

        internal fun parse(dto: ShoppingListDto): ShoppingList {
            val recipeNames = dto.recipeReferences.associate { it.recipeId to it.recipe?.name.orEmpty() }
            return ShoppingList(
                id = dto.id,
                name = dto.name.orEmpty(),
                items =
                    dto.listItems.map { item ->
                        ShoppingItem(
                            id = item.id,
                            listId = dto.id,
                            quantity = item.quantity,
                            unit = item.unit?.toModel(),
                            food = item.food?.toModel(),
                            note = item.note.orEmpty(),
                            checked = item.checked,
                            position = item.position,
                            label = item.label?.let { ShoppingLabel(it.id, it.name) },
                            recipeNames =
                                item.recipeReferences
                                    .mapNotNull { ref -> recipeNames[ref.recipeId]?.takeIf { it.isNotBlank() } }
                                    .distinct(),
                        )
                    },
                labelOrder = dto.labelSettings.sortedBy { it.position }.mapNotNull { it.labelId ?: it.label?.id },
            )
        }
    }
}

private fun ShoppingListSummaryDto.toInfo() = ShoppingListInfo(id, name.orEmpty())

private fun JsonObject.items(): List<JsonObject> = this["listItems"]?.jsonArray?.map { it.jsonObject }.orEmpty()

private fun JsonObject.withItems(items: List<JsonObject>) = with("listItems", JsonArray(items))

private fun JsonObject.with(key: String, value: JsonElement) = JsonObject(this + (key to value))

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

private fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: 0

private val REFERENCE_FIELDS = listOf("recipeId", "recipeQuantity", "recipeScale", "recipeNote")

private val CREATE_FIELDS =
    setOf(
        "id",
        "shoppingListId",
        "quantity",
        "unit",
        "food",
        "note",
        "checked",
        "position",
        "foodId",
        "labelId",
        "unitId",
        "extras",
    )

/** Only the fields Mealie accepts when creating an item. */
private fun JsonObject.forCreate(): JsonObject {
    val references =
        this["recipeReferences"]
            ?.jsonArray
            ?.map { ref ->
                val obj = ref.jsonObject
                buildJsonObject {
                    REFERENCE_FIELDS.forEach { field -> obj[field]?.let { put(field, it) } }
                }
            }.orEmpty()
    return JsonObject(filterKeys { it in CREATE_FIELDS } + ("recipeReferences" to JsonArray(references)))
}

@Serializable
internal data class ShoppingListDto(
    val id: String,
    val name: String? = null,
    val listItems: List<ShoppingItemDto> = emptyList(),
    val labelSettings: List<LabelSettingDto> = emptyList(),
    val recipeReferences: List<ListRecipeReferenceDto> = emptyList(),
)

@Serializable
internal data class ShoppingItemDto(
    val id: String,
    val quantity: Double = 0.0,
    val unit: UnitDto? = null,
    val food: FoodDto? = null,
    val note: String? = null,
    val checked: Boolean = false,
    val position: Int = 0,
    val label: LabelDto? = null,
    val recipeReferences: List<ItemRecipeReferenceDto> = emptyList(),
)

@Serializable
internal data class ItemRecipeReferenceDto(val recipeId: String? = null)

@Serializable
internal data class ListRecipeReferenceDto(val recipeId: String, val recipe: RecipeNameDto? = null)

@Serializable
internal data class RecipeNameDto(val name: String? = null)
