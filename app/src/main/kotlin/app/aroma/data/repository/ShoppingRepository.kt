package app.aroma.data.repository

import app.aroma.core.format.IngredientFormatter
import app.aroma.core.model.ShoppingItem
import app.aroma.core.model.ShoppingLabel
import app.aroma.core.model.ShoppingList
import app.aroma.core.model.ShoppingListInfo
import app.aroma.data.AromaError
import app.aroma.data.JsonCache
import app.aroma.data.Loadable
import app.aroma.data.attempt
import app.aroma.data.local.CacheDao
import app.aroma.data.local.CacheEntity
import app.aroma.data.local.PendingChangeDao
import app.aroma.data.local.PendingChangeEntity
import app.aroma.data.mealieCall
import app.aroma.data.network.ServerConnection
import app.aroma.data.remote.MealieJson
import app.aroma.data.remote.dto.AddRecipeToListDto
import app.aroma.data.remote.dto.CreateShoppingListDto
import app.aroma.data.remote.dto.ShoppingListDto
import app.aroma.data.remote.dto.ShoppingListSummaryDto
import app.aroma.data.remote.toModel
import app.aroma.data.session.SessionManager
import app.aroma.data.toAromaError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
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
import java.util.Locale
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
    private val connection: ServerConnection,
    private val requestSync: () -> Unit,
) {
    private val editLock = Mutex()
    private val syncLock = Mutex()
    private val changeCount = MutableStateFlow(0)

    /** Fires once a cached list changed here: an edit, the server's copy of a list, or a list deleted. */
    val changes: Flow<Unit> = changeCount.drop(1).map { }

    fun lists(): Flow<Loadable<List<ShoppingListInfo>>> =
        cache
            .stream(LISTS_KEY, ListSerializer(ShoppingListSummaryDto.serializer())) { sessions.api().shoppingLists().items }
            .map { loadable -> Loadable(loadable.data.map { it.toInfo() }, loadable.isRefreshing, loadable.error) }

    /** The lists as [lists] last fetched them, and again whenever that changes; null before the first fetch. */
    fun cachedLists(): Flow<List<ShoppingListInfo>?> =
        cache.observe(LISTS_KEY, ListSerializer(ShoppingListSummaryDto.serializer())).map { lists -> lists?.map { it.toInfo() } }

    /** The cached list; local edits show up here immediately. */
    fun observe(listId: String): Flow<ShoppingList?> =
        cacheDao
            .observe(key(listId))
            // Room reports every write to the cache, of any key; only a change to this list is worth reading again.
            .distinctUntilChanged { old, new -> old?.json == new?.json }
            .map { entry -> entry?.let { parse(MealieJson.decodeFromString(ShoppingListDto.serializer(), it.json)) } }
            .flowOn(Dispatchers.Default)

    fun pendingCount(listId: String): Flow<Int> = pending.countFor(listId)

    /**
     * Pushes queued changes, then loads the server's version unless changes are still waiting.
     * While Mealie is out of reach, it fails at once: the cached list is all there is.
     */
    suspend fun refresh(listId: String) =
        connection.whileReachable {
            sync()
            storeIfSettled(listId) { sessions.api().shoppingList(listId) }
        }

    /**
     * Adds [text] as an item at the end of the open ones. Where the same thing was filed under an
     * aisle before, it goes into that aisle again, as long as the list still has it.
     */
    suspend fun add(listId: String, text: String) {
        val aisle = rememberedAisles()[aisleKey(text)]
        edit(listId) { list ->
            val items = list.items()
            val position = (items.filterNot { it.bool("checked") }.maxOfOrNull { it.int("position") } ?: -1) + 1
            val label = aisle?.let { list.labelObject(it) }
            val item =
                buildJsonObject {
                    put("id", UUID.randomUUID().toString())
                    put("shoppingListId", listId)
                    put("note", text.trim())
                    put("quantity", 0)
                    put("checked", false)
                    put("position", position)
                    if (label != null) {
                        put("labelId", aisle)
                        put("label", label)
                    }
                }
            list.withItems(items + item) to listOf(Change.Create(item))
        }
    }

    suspend fun toggle(listId: String, itemId: String) =
        updateItem(listId, itemId) { item ->
            item.with("checked", JsonPrimitive(!item.bool("checked")))
        }

    /** Ticks the items [itemIds] off, or puts them back where [checked] is false; the ids of those that changed. */
    suspend fun setChecked(listId: String, itemIds: Collection<String>, checked: Boolean): List<String> {
        val ids = itemIds.toSet()
        var changed: List<String> = emptyList()
        edit(listId) { list ->
            val changes = mutableListOf<Change>()
            val items =
                list.items().map { item ->
                    val id = item.string("id")
                    if (id != null && id in ids && item.bool("checked") != checked) {
                        item.with("checked", JsonPrimitive(checked)).also { changes += Change.Update(id, it) }
                    } else {
                        item
                    }
                }
            changed = changes.map { it.itemId }
            list.withItems(items) to changes
        }
        return changed
    }

    /**
     * Puts [orderedIds], the open items of one aisle, in that order, in the places they held among
     * themselves, so every other item keeps its own. Where two of them shared a place, they take
     * places one after another from the first of them. Only the items that move are sent.
     */
    suspend fun reorder(listId: String, orderedIds: List<String>) =
        edit(listId) { list ->
            val items = list.items()
            val byId = items.associateBy { it.string("id") }
            val places = orderedIds.mapNotNull { byId[it]?.int("position") }.sorted()
            val first = places.firstOrNull() ?: 0
            val distinct = places.distinct().size == places.size
            val placeOf =
                orderedIds.filter { it in byId }.withIndex().associate { (index, id) ->
                    id to
                        if (distinct) places[index] else first + index
                }
            val changes = mutableListOf<Change>()
            val reordered =
                items.map { item ->
                    val id = item.string("id")
                    val place = placeOf[id]
                    if (id != null && place != null && place != item.int("position")) {
                        item.with("position", JsonPrimitive(place)).also { changes += Change.Update(id, it) }
                    } else {
                        item
                    }
                }
            list.withItems(reordered) to changes
        }

    /**
     * Orders the list's aisles as [labelIds] are, as the shelves of a shop follow each other; aisles
     * it leaves out follow in the order they had. Mealie keeps the order for the list.
     */
    suspend fun orderAisles(listId: String, labelIds: List<String>) =
        edit(listId) { list ->
            val rank = labelIds.withIndex().associate { (index, id) -> id to index }
            val settings =
                list
                    .labelSettings()
                    .sortedWith(compareBy({ rank[it.labelId()] ?: Int.MAX_VALUE }, { it.int("position") }))
                    .mapIndexed { index, setting -> setting.with("position", JsonPrimitive(index)) }
            val sent = JsonArray(settings.map { setting -> JsonObject(setting.filterKeys { it in LABEL_SETTING_FIELDS }) })
            list.with("labelSettings", JsonArray(settings)) to listOf(Change.Aisles(listId, sent))
        }

    /**
     * Gives an item [text] where that is new, and files it under [label], or under no aisle. New text
     * says it all, so the amount and the food give way to it, as in [rename]. A new aisle is
     * remembered, so the same thing added again goes into it.
     */
    suspend fun editItem(listId: String, itemId: String, text: String?, label: ShoppingLabel?) {
        var refiled: Pair<String, String?>? = null
        edit(listId) { list ->
            var updated: JsonObject? = null
            val items =
                list.items().map { item ->
                    if (item.string("id") != itemId) return@map item
                    var edited = if (text != null) item.renamed(text) else item
                    if (item.labelId() != label?.id) {
                        val labelObject = label?.let { list.labelObject(it.id) }
                        edited =
                            edited
                                .with("labelId", label?.id?.let(::JsonPrimitive) ?: JsonNull)
                                .with("label", labelObject ?: JsonNull)
                        refiled = aisleKey(edited.itemText()) to label?.id
                    }
                    edited.also { updated = it }
                }
            list.withItems(items) to listOfNotNull(updated?.let { Change.Update(itemId, it) })
        }
        refiled?.let { (key, aisle) -> rememberAisle(key, aisle) }
    }

    /** Replaces the item's text; structured amounts are dropped because the text now says it all. */
    suspend fun rename(listId: String, itemId: String, text: String) = updateItem(listId, itemId) { it.renamed(text) }

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

    /** Puts [items] that were taken off the list back on it, as they were. */
    suspend fun restore(listId: String, items: List<JsonObject>) =
        edit(listId) { list ->
            list.withItems(list.items() + items) to items.map { Change.Create(it.forCreate()) }
        }

    /** Takes what is ticked off the list; what it took, so [restore] can put it back. */
    suspend fun clearChecked(listId: String): List<JsonObject> {
        var removed: List<JsonObject> = emptyList()
        edit(listId) { list ->
            val (checked, open) = list.items().partition { it.bool("checked") }
            removed = checked
            list.withItems(open) to checked.map { Change.Delete(it.string("id").orEmpty()) }
        }
        return removed
    }

    suspend fun createList(name: String): String {
        val created = mealieCall { sessions.api().createShoppingList(CreateShoppingListDto(name.trim())) }
        attempt { refresh(created.id) }
        refreshLists()
        return created.id
    }

    suspend fun renameList(listId: String, name: String) {
        val current = mealieCall { sessions.api().shoppingList(listId) }
        val body = current.with("name", JsonPrimitive(name.trim()))
        storeIfSettled(listId) { sessions.api().updateShoppingList(listId, body) }
        refreshLists()
    }

    suspend fun deleteList(listId: String) {
        mealieCall { sessions.api().deleteShoppingList(listId) }
        cacheDao.delete(key(listId))
        changed()
        refreshLists()
    }

    /** Adds a recipe's ingredients: the raw objects the user picked, or all of them when [ingredients] is null. */
    suspend fun addRecipe(listId: String, recipeId: String, scale: Double, ingredients: List<JsonObject>?) =
        storeIfSettled(listId) { sessions.api().addRecipeToList(listId, recipeId, AddRecipeToListDto(scale, ingredients)) }

    private suspend fun refreshLists() {
        attempt {
            val signIn = sessions.current?.token
            val lists = sessions.api().shoppingLists().items
            if (sessions.current?.token == signIn) cache.write(LISTS_KEY, ListSerializer(ShoppingListSummaryDto.serializer()), lists)
        }
    }

    /** Replays queued changes in order. Returns false if something is still waiting for the server. */
    suspend fun sync(): Boolean =
        syncLock.withLock {
            val signIn = sessions.current?.token
            for (change in pending.all()) {
                // Changes made for one sign-in never go out with the next one.
                if (sessions.current?.token != signIn) return@withLock false
                try {
                    val payload = MealieJson.parseToJsonElement(change.payload).jsonObject
                    when (change.kind) {
                        KIND_CREATE -> {
                            sessions.api().createShoppingItem(payload)
                        }

                        KIND_UPDATE -> {
                            sessions.api().updateShoppingItem(change.itemId, payload)
                        }

                        KIND_DELETE -> {
                            sessions.api().deleteShoppingItem(change.itemId)
                        }

                        KIND_AISLES -> {
                            sessions.api().updateLabelSettings(
                                change.listId,
                                payload[AISLES_FIELD]?.jsonArray ?: JsonArray(emptyList()),
                            )
                        }
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
        changed()
    }

    private fun changed() = changeCount.update { it + 1 }

    /**
     * Keeps the server's copy of a list that [fetch] gets, unless changes made here still wait: that
     * copy would hide them. An answer that arrives after its sign-in ended is dropped, so the next
     * account never sees the list.
     */
    private suspend fun storeIfSettled(listId: String, fetch: suspend () -> JsonObject) {
        val signIn = sessions.current?.token
        val list = mealieCall { fetch() }
        editLock.withLock {
            if (sessions.current?.token == signIn && !pending.hasAny(listId)) store(listId, list)
        }
    }

    /** Where things typed onto a list were filed, by what they are ([aisleKey]): the aisle's id. */
    private suspend fun rememberedAisles(): Map<String, String> = cache.read(AISLES_KEY, AislesSerializer).orEmpty()

    private suspend fun rememberAisle(key: String, labelId: String?) {
        if (key.isEmpty()) return
        aisleLock.withLock {
            val aisles = rememberedAisles()
            cache.write(AISLES_KEY, AislesSerializer, if (labelId != null) aisles + (key to labelId) else aisles - key)
        }
    }

    private val aisleLock = Mutex()

    private sealed class Change(val kind: String, val itemId: String, val payload: JsonObject) {
        class Create(item: JsonObject) : Change(KIND_CREATE, item.string("id").orEmpty(), item)

        class Update(itemId: String, item: JsonObject) : Change(KIND_UPDATE, itemId, item)

        class Delete(itemId: String) : Change(KIND_DELETE, itemId, JsonObject(emptyMap()))

        /** The order of the list's aisles, every label setting with its position. */
        class Aisles(listId: String, settings: JsonArray) : Change(KIND_AISLES, listId, JsonObject(mapOf(AISLES_FIELD to settings)))
    }

    companion object {
        private const val LISTS_KEY = "shopping:lists"
        private const val AISLES_KEY = "shopping:aisles"
        private const val KIND_CREATE = "create"
        private const val KIND_UPDATE = "update"
        private const val KIND_DELETE = "delete"
        private const val KIND_AISLES = "aisles"
        private const val AISLES_FIELD = "settings"
        private val AislesSerializer = MapSerializer(String.serializer(), String.serializer())

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
                labels =
                    dto.labelSettings.sortedBy { it.position }.mapNotNull { setting ->
                        setting.label?.let { label -> ShoppingLabel(setting.labelId ?: label.id, label.name) }
                    },
            )
        }
    }
}

/**
 * What an item is, whatever amount it is written with, to remember its aisle by: "2 Avocados" and
 * "avocados" are one thing, and so are "300 ml Milch" and "Milch".
 */
internal fun aisleKey(text: String): String =
    IngredientFormatter
        .freeText(text, 0.0, 1.0, Locale.ROOT)
        .text
        .lowercase(Locale.ROOT)
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ")

/** The item's text as it reads, without its amount: its food's name where it has one, else its note. */
private fun JsonObject.itemText(): String =
    (this["food"] as? JsonObject)?.string("name")?.takeIf { it.isNotBlank() } ?: string("note").orEmpty()

/** The item with [text] in place of everything it said, as typed: its amount and food give way. */
private fun JsonObject.renamed(text: String): JsonObject =
    with("note", JsonPrimitive(text.trim()))
        .with("quantity", JsonPrimitive(0))
        .with("food", JsonNull)
        .with("foodId", JsonNull)
        .with("unit", JsonNull)
        .with("unitId", JsonNull)

private fun JsonObject.labelSettings(): List<JsonObject> = this["labelSettings"]?.jsonArray?.map { it.jsonObject }.orEmpty()

/** The id of the label an item or a label setting is about. */
private fun JsonObject.labelId(): String? = string("labelId") ?: (this["label"] as? JsonObject)?.string("id")

/** The label [labelId] as the list names it, to file an item under; null where the list does not have it. */
private fun JsonObject.labelObject(labelId: String): JsonObject? =
    labelSettings().firstOrNull { it.labelId() == labelId }?.get("label") as? JsonObject

/** What Mealie takes to put a list's aisle in its place. */
private val LABEL_SETTING_FIELDS = setOf("id", "shoppingListId", "labelId", "position")

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
