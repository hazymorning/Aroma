package app.aroma.data.repository

import app.aroma.core.model.CookbookCover
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
import app.aroma.data.isConnectivity
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
import app.aroma.data.remote.dto.OrganizerDto
import app.aroma.data.remote.dto.RatingDto
import app.aroma.data.remote.dto.RatingUpdateDto
import app.aroma.data.remote.dto.RecipeDto
import app.aroma.data.remote.dto.RecipeSummaryDto
import app.aroma.data.remote.toModel
import app.aroma.data.session.Session
import app.aroma.data.session.SessionManager
import app.aroma.data.toAromaError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class RecipeRepository(private val sessions: SessionManager, private val dao: RecipeDao, private val cache: JsonCache) {
    private val mutableRatings = MutableStateFlow<Map<String, UserRating>>(emptyMap())

    /** The signed-in user's ratings and favorites by recipe id. */
    val ratings: StateFlow<Map<String, UserRating>> = mutableRatings.asStateFlow()

    /** The session [ratings] were loaded for; every change made here keeps them current after that. */
    @Volatile
    private var ratingsSession: Session? = null

    @Volatile
    private var aiImport: Pair<Session, Boolean>? = null

    private val changeCount = MutableStateFlow(0)

    /**
     * Fires once recipes were created, imported, changed or deleted here, so lists load again. An
     * import changes several recipes within moments; they settle into one signal.
     */
    @OptIn(FlowPreview::class)
    val changes: Flow<Unit> = changeCount.drop(1).debounce(CHANGES_SETTLE).map { }

    private fun changed() = changeCount.update { it + 1 }

    suspend fun page(query: RecipeQuery, page: Int, perPage: Int = PAGE_SIZE): RecipePage {
        val server = sessions.requireServerUrl()
        return try {
            val favoriteFilter =
                if (query.favoritesOnly) {
                    val ids = loadRatings().filterValues { it.isFavorite }.keys
                    if (ids.isEmpty()) return RecipePage(emptyList(), 0, page, hasMore = false, isOffline = false)
                    idsFilter(ids)
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
            // Screens ask from the main thread; encoding the offline copy and mapping are not for it.
            val (entities, items) =
                withContext(Dispatchers.Default) {
                    result.items.map { it.toEntity() } to
                        result.items.map { it.toModel(server) }
                }
            dao.upsertSummaries(entities)
            RecipePage(items, result.total, page, page < result.totalPages, isOffline = false)
        } catch (error: Throwable) {
            val failure = error.toAromaError()
            // The stored summaries know neither cookbooks nor tools, so those cannot be answered offline.
            if (!failure.isConnectivity || page > 1 || query.cookbook != null || query.tools.isNotEmpty()) throw failure
            offlinePage(query, server)
        }
    }

    private suspend fun offlinePage(query: RecipeQuery, server: String): RecipePage {
        val stored = dao.search(query.search.trim(), limit = 500)
        val local = withContext(Dispatchers.Default) { stored.map { decodeSummary(it).toModel(server) } }
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

    /** Recipes marked as favorites; [fresh] fetches the favorites again, as they may have changed elsewhere. */
    fun favorites(fresh: Boolean, limit: Int = 12): Flow<Loadable<List<RecipeSummary>>> =
        summaries("home:favorites") {
            val ids = loadRatings(fresh).filterValues { it.isFavorite }.keys
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
                        queryFilter = idsFilter(ids),
                    ).items
            }
        }

    /** A choice of recipes filed under [names] as category or tag, or of all recipes without names; new each [day]. */
    fun suggestions(names: List<String>, day: LocalDate, limit: Int = 12): Flow<Loadable<List<RecipeSummary>>> =
        summaries("home:suggestions:${names.sorted().joinToString(",")}") {
            val list = names.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "\\\"")}\"" }
            sessions
                .api()
                .recipes(
                    page = 1,
                    perPage = limit,
                    orderBy = "random",
                    orderDirection = "asc",
                    queryFilter = if (names.isEmpty()) null else "recipe_category.name IN $list OR tags.name IN $list",
                    paginationSeed = day.toString(),
                ).items
        }

    /** A choice of recipes cooked before, but not since [since]; new each [day]. */
    fun cookedLongAgo(since: LocalDate, day: LocalDate, limit: Int = 12): Flow<Loadable<List<RecipeSummary>>> =
        summaries("home:cooked-long-ago") {
            sessions
                .api()
                .recipes(
                    page = 1,
                    perPage = limit,
                    orderBy = "random",
                    orderDirection = "asc",
                    // Recipes never cooked compare as earliest in Mealie 3, so a lower bound keeps them out.
                    queryFilter = "lastMade > \"2000-01-01\" AND lastMade < \"$since\"",
                    paginationSeed = day.toString(),
                ).items
        }

    /** Summaries of [ids], from the stored copy where it has them and from Mealie for the rest. */
    suspend fun summaries(ids: Set<String>): Map<String, RecipeSummary> {
        if (ids.isEmpty()) return emptyMap()
        val server = sessions.requireServerUrl()
        val stored = dao.summaries(ids).map(::decodeSummary)
        val missing = ids - stored.mapTo(mutableSetOf()) { it.id }
        val fetched =
            if (missing.isEmpty()) {
                emptyList()
            } else {
                sessions.api().recipes(page = 1, perPage = missing.size, queryFilter = idsFilter(missing)).items.also { items ->
                    dao.upsertSummaries(items.map { it.toEntity() })
                }
            }
        return withContext(Dispatchers.Default) { (stored + fetched).associate { it.id to it.toModel(server) } }
    }

    private fun idsFilter(ids: Collection<String>) = ids.joinToString(prefix = "id IN [", postfix = "]") { "\"$it\"" }

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
        }.flowOn(Dispatchers.Default)

    private suspend fun fetchAndStore(slug: String): RecipeDto {
        val dto = sessions.api().recipe(slug)
        dao.upsertDetail(
            RecipeDetailEntity(dto.slug, dto.id, MealieJson.encodeToString(RecipeDto.serializer(), dto), System.currentTimeMillis()),
        )
        return dto
    }

    /** Raw recipe JSON, used by the editor so fields Aroma does not know survive a save. */
    suspend fun recipeJson(slug: String): JsonObject = mealieCall { sessions.api().recipeJson(slug) }

    /**
     * The user's ratings and favorites, fetched once a session: Mealie sends them all in one answer,
     * and changes made here keep them current. [fresh] fetches them again, for a pull to refresh.
     */
    suspend fun loadRatings(fresh: Boolean = false): Map<String, UserRating> {
        val session = sessions.current
        if (!fresh && session != null && session == ratingsSession) return ratings.value
        var latest: List<RatingDto> = emptyList()
        cache.stream("ratings", ListSerializer(RatingDto.serializer())) { sessions.api().ratings().ratings }.collect { latest = it.data }
        ratingsSession = session
        return latest
            .associate { it.recipeId to UserRating(it.rating?.takeIf { r -> r > 0 }, it.isFavorite) }
            .also { mutableRatings.value = it }
    }

    suspend fun setFavorite(recipeId: String, slug: String, favorite: Boolean) =
        changeRating(recipeId, slug, RatingUpdateDto(isFavorite = favorite)) { it.copy(isFavorite = favorite) }

    suspend fun rate(recipeId: String, slug: String, rating: Int) =
        changeRating(recipeId, slug, RatingUpdateDto(rating = rating.toDouble())) { it.copy(rating = rating.toDouble()) }

    /**
     * Shows [change] at once and sends [body]. If Mealie does not take it, the fields [body] sets
     * go back to what they were, and a change of the other field made meanwhile stays.
     */
    private suspend fun changeRating(recipeId: String, slug: String, body: RatingUpdateDto, change: (UserRating) -> UserRating) {
        val before = mutableRatings.value[recipeId] ?: UserRating(null, false)
        mutableRatings.update { it + (recipeId to change(it[recipeId] ?: UserRating(null, false))) }
        try {
            mealieCall { sessions.api().setRating(userId(), slug, body) }
        } catch (failure: AromaError) {
            mutableRatings.update { ratings ->
                val now = ratings[recipeId] ?: UserRating(null, false)
                val restored =
                    now.copy(
                        rating = if (body.rating != null) before.rating else now.rating,
                        isFavorite = if (body.isFavorite != null) before.isFavorite else now.isFavorite,
                    )
                ratings + (recipeId to restored)
            }
            throw failure
        }
        cache.write("ratings", ListSerializer(RatingDto.serializer()), ratingDtos())
    }

    private fun ratingDtos() = mutableRatings.value.map { (id, rating) -> RatingDto(id, rating.rating, rating.isFavorite) }

    private fun userId(): String = sessions.current?.userId ?: throw AromaError.Unauthorized()

    suspend fun delete(slug: String) {
        mealieCall { sessions.api().deleteRecipe(slug) }
        dao.deleteDetail(slug)
        dao.deleteSummary(slug)
        changed()
    }

    /** Imports a recipe from a link. [onProgress] gets Mealie's description of each step where the server reports them. */
    suspend fun importFromUrl(url: String, onProgress: (String) -> Unit): String {
        val body = ImportRecipeDto(url.trim(), includeTags = true)
        return importing(onProgress, stream = { importRecipeStream(body) }, plain = { importRecipe(body) }).also { changed() }
    }

    /** Lets the group's AI provider build a recipe from a link, a text or both, optionally translated. */
    suspend fun importWithAi(url: String?, text: String?, translateTo: String?, onProgress: (String) -> Unit): String {
        val parts =
            buildList {
                text?.takeIf { it.isNotBlank() }?.let { add(MultipartBody.Part.createFormData("content", it.trim())) }
                url?.takeIf { it.isNotBlank() }?.let { add(MultipartBody.Part.createFormData("url", it.trim())) }
                translateTo?.let { add(MultipartBody.Part.createFormData("translateLanguage", it)) }
            }
        return importing(onProgress, stream = { importRecipeWithAiStream(parts) }, plain = { importRecipeWithAi(parts) }).also { changed() }
    }

    /**
     * Whether the server may read recipes with AI: Mealie 3.23, which brought the AI import, with a
     * provider set up for the group. Asked once a session, as only an admin changes it.
     */
    suspend fun canImportWithAi(): Boolean {
        val session = sessions.current ?: return false
        aiImport?.let { (askedFor, answer) -> if (askedFor == session) return answer }
        if (!MealieVersion.isAtLeast(session.serverVersion, 3, 23)) return false
        val enabled =
            try {
                sessions
                    .api()
                    .group()
                    .aiProviderSettings
                    ?.aiEnabled == true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Asked again next time: the server may just have been out of reach.
                return false
            }
        aiImport = session to enabled
        return enabled
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
                failure is AromaError.Server && failure.code in 400..499 -> {
                    AromaError.ImportFailed()
                }

                // Mealie 2 answers 500 when it cannot load the page at all. Other 5xx codes come
                // from a proxy in front of Mealie and stay as they are: Mealie may still finish.
                failure is AromaError.Server && failure.code == 500 -> {
                    AromaError.ImportFailed(
                        AromaError.ImportFailed.Reason.PageUnreachable,
                    )
                }

                else -> {
                    failure
                }
            }
        }
    }

    /** The newest recipes' ids, so that one Mealie adds later can be told apart from them. */
    suspend fun newestRecipeIds(): Set<String> = mealieCall { newest().mapTo(mutableSetOf()) { it.id } }

    /** The slug of a recipe the signed-in user added since [known] was taken, best one from [source], or null. */
    suspend fun addedSince(known: Set<String>, source: String?): String? =
        mealieCall {
            val user = userId()
            val added = newest().filter { it.id !in known && (it.userId == null || it.userId == user) }
            (added.firstOrNull { source != null && it.orgURL == source } ?: added.firstOrNull())?.slug
        }

    private suspend fun newest(): List<RecipeSummaryDto> =
        sessions
            .api()
            .recipes(page = 1, perPage = NEWEST_WINDOW, orderBy = "created_at", orderDirection = "desc")
            .items

    suspend fun create(name: String): String = mealieCall { sessions.api().createRecipe(CreateRecipeDto(name.trim())) }.also { changed() }

    /** Saves [patch]; a new name gives the recipe a new slug, which is returned. */
    suspend fun update(slug: String, patch: JsonObject): String {
        val dto = mealieCall { sessions.api().patchRecipe(slug, patch) }
        dao.upsertDetail(
            RecipeDetailEntity(dto.slug, dto.id, MealieJson.encodeToString(RecipeDto.serializer(), dto), System.currentTimeMillis()),
        )
        if (dto.slug != slug) dao.deleteDetail(slug)
        changed()
        return dto.slug
    }

    suspend fun uploadImage(slug: String, bytes: ByteArray, extension: String) {
        mealieCall {
            val image = MultipartBody.Part.createFormData("image", "image.$extension", bytes.toRequestBody("image/*".toMediaType()))
            val ext = MultipartBody.Part.createFormData("extension", extension)
            sessions.api().uploadImage(slug, image, ext)
        }
        changed()
    }

    fun organizers(): Flow<Loadable<Organizers>> =
        cache
            .stream("organizers", OrganizersDto.serializer()) {
                val api = sessions.api()
                coroutineScope {
                    val categories = async { api.categories().items }
                    val tags = async { api.tags().items }
                    val tools = async { api.tools().items }
                    OrganizersDto(categories.await(), tags.await(), tools.await())
                }
            }.map { loadable ->
                val dto = loadable.data
                Loadable(
                    Organizers(dto.categories.map { it.toModel() }, dto.tags.map { it.toModel() }, dto.tools.map { it.toModel() }),
                    loadable.isRefreshing,
                    loadable.error,
                )
            }

    /** The household's cookbooks, each with its number of recipes and the photo of its newest one. */
    fun cookbooks(): Flow<Loadable<List<CookbookCover>>> {
        val server = sessions.requireServerUrl()
        return cache
            .stream("cookbook-covers", ListSerializer(CookbookCoverDto.serializer())) {
                val api = sessions.api()
                val requests = Semaphore(PARALLEL_DOWNLOADS)
                coroutineScope {
                    api
                        .cookbooks()
                        .items
                        .sortedBy { it.position }
                        .map { book ->
                            async {
                                requests.withPermit {
                                    val newest =
                                        api.recipes(
                                            page = 1,
                                            perPage = 4,
                                            orderBy = "created_at",
                                            orderDirection = "desc",
                                            cookbook = book.id,
                                        )
                                    CookbookCoverDto(book, newest.total, newest.items.firstOrNull { it.toModel(server).image != null })
                                }
                            }
                        }.awaitAll()
                }
            }.map { loadable ->
                val covers = loadable.data.map { CookbookCover(it.cookbook.toModel(), it.recipeCount, it.cover?.toModel(server)?.image) }
                Loadable(covers, loadable.isRefreshing, loadable.error)
            }
    }

    /**
     * Keeps a copy of every recipe for offline use: fetches new and changed ones a few at a time
     * and forgets the ones deleted or renamed on the server. Returns how many are stored.
     */
    suspend fun downloadAll() {
        val listing = mealieCall { sessions.api().recipes(page = 1, perPage = -1, orderBy = "name", orderDirection = "asc") }.items
        dao.upsertSummaries(listing.map { it.toEntity() })
        val stored =
            withContext(Dispatchers.Default) {
                dao.details().associate { it.slug to decodeRecipe(it.json)?.updatedAt }
            }
        val current = listing.mapTo(mutableSetOf()) { it.slug }
        (stored.keys - current).forEach { dao.deleteDetail(it) }
        val ids = listing.mapTo(mutableSetOf()) { it.id }
        dao.summaryIds().filterNot { it in ids }.forEach { dao.deleteSummaryById(it) }

        val stale = listing.filter { it.slug !in stored || (it.updatedAt != null && stored[it.slug] != it.updatedAt) }
        val downloads = Semaphore(PARALLEL_DOWNLOADS)
        coroutineScope {
            stale.forEach { summary ->
                launch {
                    downloads.withPermit {
                        try {
                            fetchAndStore(summary.slug)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // The next run tries again; one failing recipe must not stop the others.
                        }
                    }
                }
            }
        }
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

        private const val PARALLEL_DOWNLOADS = 4

        /** Enough of the newest recipes to spot one added meanwhile, even if others arrive at the same time. */
        private const val NEWEST_WINDOW = 10

        private val CHANGES_SETTLE = 1.seconds
    }
}

@Serializable
internal data class CookbookCoverDto(val cookbook: CookbookDto, val recipeCount: Int, val cover: RecipeSummaryDto?)

@Serializable
internal data class OrganizersDto(
    val categories: List<OrganizerDto> = emptyList(),
    val tags: List<OrganizerDto> = emptyList(),
    val tools: List<OrganizerDto> = emptyList(),
)
