package app.aroma.data.repository

import app.aroma.core.model.Cookbook
import app.aroma.core.model.Organizers
import app.aroma.core.model.Recipe
import app.aroma.core.model.RecipePage
import app.aroma.core.model.RecipeQuery
import app.aroma.core.model.RecipeSort
import app.aroma.core.model.RecipeSummary
import app.aroma.core.model.UserRating
import app.aroma.data.AromaError
import app.aroma.data.JsonCache
import app.aroma.data.Loadable
import app.aroma.data.local.RecipeDao
import app.aroma.data.local.RecipeDetailEntity
import app.aroma.data.local.RecipeSummaryEntity
import app.aroma.data.mealieCall
import app.aroma.data.remote.ImportStream
import app.aroma.data.remote.MealieApi
import app.aroma.data.remote.MealieJson
import app.aroma.data.remote.dto.CookbookDto
import app.aroma.data.remote.dto.CreateRecipeDto
import app.aroma.data.remote.dto.ImportRecipeDto
import app.aroma.data.remote.dto.LastMadeDto
import app.aroma.data.remote.dto.OrganizerDto
import app.aroma.data.remote.dto.RatingDto
import app.aroma.data.remote.dto.RatingUpdateDto
import app.aroma.data.remote.dto.RecipeDto
import app.aroma.data.remote.dto.RecipeSummaryDto
import app.aroma.data.remote.toModel
import app.aroma.data.session.SessionManager
import app.aroma.data.toAromaError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

class RecipeRepository(private val sessions: SessionManager, private val dao: RecipeDao, private val cache: JsonCache) {
    private val mutableRatings = MutableStateFlow<Map<String, UserRating>>(emptyMap())

    /** The signed-in user's ratings and favorites by recipe id. */
    val ratings: StateFlow<Map<String, UserRating>> = mutableRatings.asStateFlow()

    suspend fun page(query: RecipeQuery, page: Int, perPage: Int = PAGE_SIZE): RecipePage {
        val server = sessions.requireServerUrl()
        return try {
            val favoriteFilter =
                if (query.favoritesOnly) {
                    val ids = refreshRatings().filterValues { it.isFavorite }.keys
                    if (ids.isEmpty()) return RecipePage(emptyList(), 0, page, hasMore = false, isOffline = false)
                    ids.joinToString(prefix = "id IN [", postfix = "]") { "\"$it\"" }
                } else {
                    null
                }
            val (orderBy, direction) =
                when (query.sort) {
                    RecipeSort.Newest -> "created_at" to "desc"
                    RecipeSort.Name -> "name" to "asc"
                    RecipeSort.Rating -> "rating" to "desc"
                    RecipeSort.LastMade -> "last_made" to "desc"
                    RecipeSort.Random -> "random" to "asc"
                }
            val result =
                sessions.api().recipes(
                    page = page,
                    perPage = perPage,
                    orderBy = orderBy,
                    orderDirection = direction,
                    search = query.search.trim().ifEmpty { null },
                    categories = query.categories.ifEmpty { null },
                    tags = query.tags.ifEmpty { null },
                    tools = query.tools.ifEmpty { null },
                    cookbook = query.cookbook,
                    queryFilter = favoriteFilter,
                    paginationSeed = query.seed.takeIf { query.sort == RecipeSort.Random },
                )
            dao.upsertSummaries(result.items.map { it.toEntity() })
            RecipePage(result.items.map { it.toModel(server) }, result.total, page, page < result.totalPages, isOffline = false)
        } catch (error: Throwable) {
            val failure = error.toAromaError()
            val canFallBack = failure is AromaError.Offline || failure is AromaError.HostNotFound
            if (!canFallBack || page > 1) throw failure
            offlinePage(query, server)
        }
    }

    private suspend fun offlinePage(query: RecipeQuery, server: String): RecipePage {
        val local = dao.search(query.search.trim(), limit = 500).map { decodeSummary(it).toModel(server) }
        val favorites = ratings.value.filterValues { it.isFavorite }.keys
        val filtered =
            local.filter { recipe ->
                (!query.favoritesOnly || recipe.id in favorites) &&
                    query.categories.all { slug -> recipe.categories.any { it.slug == slug || it.id == slug } } &&
                    query.tags.all { slug -> recipe.tags.any { it.slug == slug || it.id == slug } }
            }
        return RecipePage(filtered, filtered.size, 1, hasMore = false, isOffline = true)
    }

    fun recent(limit: Int = 12): Flow<Loadable<List<RecipeSummary>>> =
        summaries(
            "home:recent",
        ) { sessions.api().recipes(page = 1, perPage = limit, orderBy = "created_at", orderDirection = "desc").items }

    fun favorites(limit: Int = 12): Flow<Loadable<List<RecipeSummary>>> =
        summaries("home:favorites") {
            val ids = refreshRatings().filterValues { it.isFavorite }.keys
            if (ids.isEmpty()) {
                emptyList()
            } else {
                sessions
                    .api()
                    .recipes(
                        page = 1,
                        perPage = limit,
                        orderBy = "rating",
                        orderDirection = "desc",
                        queryFilter = ids.joinToString(prefix = "id IN [", postfix = "]") { "\"$it\"" },
                    ).items
            }
        }

    private fun summaries(key: String, fetch: suspend () -> List<RecipeSummaryDto>): Flow<Loadable<List<RecipeSummary>>> {
        val server = sessions.requireServerUrl()
        return cache
            .stream(key, ListSerializer(RecipeSummaryDto.serializer())) {
                fetch().also { items -> dao.upsertSummaries(items.map { it.toEntity() }) }
            }.map { loadable -> Loadable(loadable.data.map { it.toModel(server) }, loadable.isRefreshing, loadable.error) }
    }

    /** Cached copy first, then the server's. Keeps working offline for recipes opened before. */
    fun recipe(slug: String): Flow<Loadable<Recipe>> =
        flow {
            val server = sessions.requireServerUrl()
            val cached = dao.detail(slug)?.let { decodeRecipe(it.json)?.toModel(server) }
            if (cached != null) emit(Loadable(cached, isRefreshing = true))
            val fresh =
                try {
                    fetchAndStore(slug)
                } catch (error: Throwable) {
                    val failure = error.toAromaError()
                    if (cached == null) throw failure
                    emit(Loadable(cached, error = failure))
                    return@flow
                }
            emit(Loadable(fresh.toModel(server)))
        }

    private suspend fun fetchAndStore(slug: String): RecipeDto {
        val dto = sessions.api().recipe(slug)
        dao.upsertDetail(
            RecipeDetailEntity(dto.slug, dto.id, MealieJson.encodeToString(RecipeDto.serializer(), dto), System.currentTimeMillis()),
        )
        return dto
    }

    /** Raw recipe JSON, used by the editor so fields Aroma does not know survive a save. */
    suspend fun recipeJson(slug: String): JsonObject = mealieCall { sessions.api().recipeJson(slug) }

    suspend fun refreshRatings(): Map<String, UserRating> {
        val fresh = cache.stream("ratings", ListSerializer(RatingDto.serializer())) { sessions.api().ratings().ratings }
        var latest: List<RatingDto> = emptyList()
        fresh.collect { latest = it.data }
        return latest
            .associate { it.recipeId to UserRating(it.rating?.takeIf { r -> r > 0 }, it.isFavorite) }
            .also { mutableRatings.value = it }
    }

    suspend fun setFavorite(recipeId: String, slug: String, favorite: Boolean) {
        updateRatingLocally(recipeId) { it.copy(isFavorite = favorite) }
        mealieCall { sessions.api().setRating(userId(), slug, RatingUpdateDto(isFavorite = favorite)) }
        cache.write("ratings", ListSerializer(RatingDto.serializer()), ratingDtos())
    }

    suspend fun rate(recipeId: String, slug: String, rating: Int) {
        updateRatingLocally(recipeId) { it.copy(rating = rating.toDouble()) }
        mealieCall { sessions.api().setRating(userId(), slug, RatingUpdateDto(rating = rating.toDouble())) }
        cache.write("ratings", ListSerializer(RatingDto.serializer()), ratingDtos())
    }

    private fun updateRatingLocally(recipeId: String, change: (UserRating) -> UserRating) {
        val current = mutableRatings.value[recipeId] ?: UserRating(null, false)
        mutableRatings.value = mutableRatings.value + (recipeId to change(current))
    }

    private fun ratingDtos() = mutableRatings.value.map { (id, rating) -> RatingDto(id, rating.rating, rating.isFavorite) }

    private fun userId(): String = sessions.current?.userId ?: throw AromaError.Unauthorized()

    suspend fun markCooked(slug: String) =
        mealieCall {
            sessions.api().markMade(slug, LastMadeDto(Instant.now().toString()))
        }

    suspend fun delete(slug: String) {
        mealieCall { sessions.api().deleteRecipe(slug) }
        dao.deleteDetail(slug)
        dao.deleteSummary(slug)
    }

    /** Imports a recipe from a link. [onProgress] gets Mealie's description of each step where the server reports them. */
    suspend fun importFromUrl(url: String, onProgress: (String) -> Unit = {}): String {
        val body = ImportRecipeDto(url.trim())
        return importing(onProgress, stream = { importRecipeStream(body) }, plain = { importRecipe(body) })
    }

    /** Lets the group's AI provider build a recipe from a link, a text or both, optionally translated. */
    suspend fun importWithAi(url: String?, text: String?, translateTo: String?, onProgress: (String) -> Unit = {}): String {
        val parts =
            buildList {
                text?.takeIf { it.isNotBlank() }?.let { add(MultipartBody.Part.createFormData("content", it.trim())) }
                url?.takeIf { it.isNotBlank() }?.let { add(MultipartBody.Part.createFormData("url", it.trim())) }
                translateTo?.let { add(MultipartBody.Part.createFormData("translateLanguage", it)) }
            }
        return importing(onProgress, stream = { importRecipeWithAiStream(parts) }, plain = { importRecipeWithAi(parts) })
    }

    /** Whether the server may read recipes with AI: Mealie 3 with an AI provider set up for the group. */
    suspend fun canImportWithAi(): Boolean =
        try {
            sessions
                .api()
                .group()
                .aiProviderSettings
                ?.aiEnabled == true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }

    /** The recipe's photo as stored by Mealie, or null if it has none. */
    suspend fun imageBytes(recipeId: String): ByteArray? =
        try {
            sessions.api().recipeImage(recipeId).use { it.bytes() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    /** Uses the streaming endpoint where the server has one and maps rejected sources to [AromaError.ImportFailed]. */
    private suspend fun importing(
        onProgress: (String) -> Unit,
        stream: suspend MealieApi.() -> ResponseBody,
        plain: suspend MealieApi.() -> String,
    ): String {
        val api = sessions.api()
        return try {
            try {
                ImportStream.slugFrom(api.stream(), onProgress)
            } catch (missing: HttpException) {
                if (missing.code() != 404 && missing.code() != 405) throw missing
                api.plain()
            }
        } catch (error: Throwable) {
            val failure = error.toAromaError()
            throw when {
                failure is AromaError.Server && failure.code in 400..499 -> AromaError.ImportFailed()

                // Mealie 2 answers 500 when it cannot load the page at all.
                failure is AromaError.Server -> AromaError.ImportFailed(pageUnreachable = true)

                else -> failure
            }
        }
    }

    suspend fun create(name: String): String = mealieCall { sessions.api().createRecipe(CreateRecipeDto(name.trim())) }

    suspend fun update(slug: String, patch: JsonObject): String {
        val dto = mealieCall { sessions.api().patchRecipe(slug, patch) }
        dao.upsertDetail(
            RecipeDetailEntity(dto.slug, dto.id, MealieJson.encodeToString(RecipeDto.serializer(), dto), System.currentTimeMillis()),
        )
        return dto.slug
    }

    suspend fun uploadImage(slug: String, bytes: ByteArray, extension: String) =
        mealieCall {
            val image = MultipartBody.Part.createFormData("image", "image.$extension", bytes.toRequestBody("image/*".toMediaType()))
            val ext = MultipartBody.Part.createFormData("extension", extension)
            sessions.api().uploadImage(slug, image, ext)
        }

    fun organizers(): Flow<Loadable<Organizers>> =
        cache
            .stream("organizers", OrganizersDto.serializer()) {
                val api = sessions.api()
                OrganizersDto(api.categories().items, api.tags().items, api.tools().items)
            }.map { loadable ->
                val dto = loadable.data
                Loadable(
                    Organizers(dto.categories.map { it.toModel() }, dto.tags.map { it.toModel() }, dto.tools.map { it.toModel() }),
                    loadable.isRefreshing,
                    loadable.error,
                )
            }

    fun cookbooks(): Flow<Loadable<List<Cookbook>>> =
        cache
            .stream("cookbooks", ListSerializer(CookbookDto.serializer())) {
                sessions
                    .api()
                    .cookbooks()
                    .items
                    .sortedBy { it.position }
            }.map { loadable -> Loadable(loadable.data.map { it.toModel() }, loadable.isRefreshing, loadable.error) }

    /** Downloads every recipe for offline use; returns how many are stored. */
    suspend fun downloadAll(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int {
        val first = mealieCall { sessions.api().recipes(page = 1, perPage = -1, orderBy = "name", orderDirection = "asc") }
        dao.upsertSummaries(first.items.map { it.toEntity() })
        val stored = dao.cachedSlugs().toSet()
        first.items.forEachIndexed { index, summary ->
            if (summary.slug !in stored) runCatching { fetchAndStore(summary.slug) }
            onProgress(index + 1, first.items.size)
        }
        return dao.cachedSlugs().size
    }

    fun offlineCount(): Flow<Int> = dao.detailCount()

    private fun RecipeSummaryDto.toEntity() =
        RecipeSummaryEntity(
            id = id,
            slug = slug,
            name = name,
            json = MealieJson.encodeToString(RecipeSummaryDto.serializer(), this),
            addedOn = dateAdded.orEmpty(),
        )

    private fun decodeSummary(entity: RecipeSummaryEntity) = MealieJson.decodeFromString(RecipeSummaryDto.serializer(), entity.json)

    private fun decodeRecipe(json: String) = runCatching { MealieJson.decodeFromString(RecipeDto.serializer(), json) }.getOrNull()

    companion object {
        const val PAGE_SIZE = 30
    }
}

@kotlinx.serialization.Serializable
internal data class OrganizersDto(
    val categories: List<OrganizerDto> = emptyList(),
    val tags: List<OrganizerDto> = emptyList(),
    val tools: List<OrganizerDto> = emptyList(),
)
