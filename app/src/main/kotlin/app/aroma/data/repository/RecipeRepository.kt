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
import app.aroma.data.attempt
import app.aroma.data.isConnectivity
import app.aroma.data.local.RecipeDao
import app.aroma.data.local.RecipeDetailEntity
import app.aroma.data.local.RecipeSummaryEntity
import app.aroma.data.mealieCall
import app.aroma.data.network.ServerConnection
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
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.io.File
import java.text.Collator
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class RecipeRepository(
    private val sessions: SessionManager,
    private val dao: RecipeDao,
    private val cache: JsonCache,
    private val connection: ServerConnection,
) {
    private val mutableRatings = MutableStateFlow<Map<String, UserRating>>(emptyMap())

    /** The signed-in user's ratings and favorites by recipe id. */
    val ratings: StateFlow<Map<String, UserRating>> = mutableRatings.asStateFlow()

    /** The session [ratings] were loaded for; every change made here keeps them current after that. */
    @Volatile
    private var ratingsSession: Session? = null

    @Volatile
    private var aiImport: Pair<Session, AiImport>? = null

    private val changeCount = MutableStateFlow(0)

    /**
     * Fires once recipes were created, imported, changed or deleted here, so lists load again. An
     * import changes several recipes within moments; they settle into one signal.
     */
    @OptIn(FlowPreview::class)
    val changes: Flow<Unit> = changeCount.drop(1).debounce(CHANGES_SETTLE).map { }

    private fun changed() = changeCount.update { it + 1 }

    /**
     * A page of the recipes [query] asks for. While Mealie is out of reach, the first page holds all
     * the stored recipes that match, at once; see [storedPage].
     */
    suspend fun page(query: RecipeQuery, page: Int, perPage: Int = PAGE_SIZE): RecipePage {
        val server = sessions.requireServerUrl()
        return try {
            connection.whileReachable { serverPage(query, page, perPage, server) }
        } catch (error: Throwable) {
            val failure = error.toAromaError()
            if (!failure.isConnectivity || page > 1) throw failure
            storedPage(query) ?: throw failure
        }
    }

    private suspend fun serverPage(query: RecipeQuery, page: Int, perPage: Int, server: String): RecipePage {
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
                queryFilter =
                    listOfNotNull(favoriteFilter, query.mealNames.takeIf { it.isNotEmpty() }?.let(::namesFilter))
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(" AND ") { "($it)" },
                paginationSeed = query.seed.takeIf { query.sort == RecipeSort.Random },
            )
        // Screens ask from the main thread; encoding the offline copy and mapping are not for it.
        val (entities, items) =
            withContext(Dispatchers.Default) {
                result.items.map { it.toEntity() } to
                    result.items.map { it.toModel(server) }
            }
        dao.upsertSummaries(entities)
        if (query.cookbook != null && query.isWholeCollection) {
            rememberCookbook(query.cookbook, result.items.map { it.id }, whole = page == 1 && result.totalPages <= 1)
        }
        return RecipePage(items, result.total, page, page < result.totalPages, isOffline = false)
    }

    /**
     * All the stored recipes [query] asks for, filtered and sorted on the phone as Mealie would, or
     * null for a cookbook the phone does not know the recipes of. It never waits for Mealie.
     */
    suspend fun storedPage(query: RecipeQuery): RecipePage? {
        val server = sessions.requireServerUrl()
        val cookbook = query.cookbook?.let { id -> cache.read(cookbookKey(id), IdsSerializer)?.toSet() ?: return null }
        val favorites =
            if (query.favoritesOnly) {
                attempt { loadRatings() }.getOrDefault(ratings.value).filterValues { it.isFavorite }.keys
            } else {
                emptySet()
            }
        val stored = dao.search(query.search.trim(), limit = STORED_LIMIT)
        val items =
            withContext(Dispatchers.Default) {
                stored
                    .map(::decodeSummary)
                    .filter { recipe ->
                        (cookbook == null || recipe.id in cookbook) &&
                            (!query.favoritesOnly || recipe.id in favorites) &&
                            (query.mealNames.isEmpty() || (recipe.recipeCategory + recipe.tags).any { it.name in query.mealNames }) &&
                            query.categories.all { wanted -> recipe.recipeCategory.any { it.slug == wanted || it.id == wanted } } &&
                            query.tags.all { wanted -> recipe.tags.any { it.slug == wanted || it.id == wanted } } &&
                            query.tools.all { wanted -> recipe.tools.any { it.slug == wanted || it.id == wanted } }
                    }.sortedFor(query)
                    .map { it.toModel(server) }
            }
        return RecipePage(items, items.size, 1, hasMore = false, isOffline = true)
    }

    /** Mealie's order for [query]; Mealie's random order cannot be known here, so the copy shuffles by the same seed. */
    private fun List<RecipeSummaryDto>.sortedFor(query: RecipeQuery): List<RecipeSummaryDto> =
        when (query.sort) {
            RecipeSort.Newest -> sortedByDescending { it.createdAt ?: it.dateAdded }
            RecipeSort.Name -> sortedWith(compareBy(Collator.getInstance()) { it.name })
            RecipeSort.Rating -> sortedByDescending { it.rating ?: 0.0 }
            RecipeSort.LastMade -> sortedByDescending { it.lastMade }
            RecipeSort.Random -> shuffled(Random(query.seed.hashCode()))
        }

    /** Whether [this] asks for a whole cookbook, not for some of its recipes. */
    private val RecipeQuery.isWholeCollection: Boolean
        get() = search.isBlank() && !favoritesOnly && categories.isEmpty() && tags.isEmpty() && tools.isEmpty() && mealNames.isEmpty()

    /**
     * Notes which recipes a cookbook holds, for the stored copy to show it offline. A page of a
     * larger cookbook only adds to what is noted; the offline download notes every cookbook whole.
     */
    private suspend fun rememberCookbook(id: String, recipeIds: List<String>, whole: Boolean) {
        val noted = if (whole) emptyList() else cache.read(cookbookKey(id), IdsSerializer).orEmpty()
        cache.write(cookbookKey(id), IdsSerializer, (noted + recipeIds).distinct())
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
            sessions
                .api()
                .recipes(
                    page = 1,
                    perPage = limit,
                    orderBy = "random",
                    orderDirection = "asc",
                    queryFilter = names.takeIf { it.isNotEmpty() }?.let(::namesFilter),
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

    /** Recipes with a category or tag of one of these [names]. */
    private fun namesFilter(names: List<String>): String {
        val list = names.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "\\\"")}\"" }
        return "recipe_category.name IN $list OR tags.name IN $list"
    }

    private fun summaries(key: String, fetch: suspend () -> List<RecipeSummaryDto>): Flow<Loadable<List<RecipeSummary>>> {
        val server = sessions.requireServerUrl()
        return cache
            .stream(key, ListSerializer(RecipeSummaryDto.serializer())) {
                fetch().also { items -> dao.upsertSummaries(items.map { it.toEntity() }) }
            }.map { loadable -> Loadable(loadable.data.map { it.toModel(server) }, loadable.isRefreshing, loadable.error) }
    }

    /**
     * Cached copy first, then the server's. Keeps working offline for recipes opened before, and
     * shows the cached copy alone, at once, while Mealie is out of reach.
     */
    fun recipe(slug: String): Flow<Loadable<Recipe>> =
        flow {
            val server = sessions.requireServerUrl()
            val cached = dao.detail(slug)?.let { decodeRecipe(it.json)?.toModel(server) }
            if (cached != null && connection.isReachable.value) emit(Loadable(cached, isRefreshing = true))
            val fresh =
                try {
                    connection.whileReachable { fetchAndStore(slug) }
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
     * Ratings from the phone's copy, kept while Mealie was out of reach, are asked for again next time.
     */
    suspend fun loadRatings(fresh: Boolean = false): Map<String, UserRating> {
        val session = sessions.current
        if (!fresh && session != null && session == ratingsSession) return ratings.value
        val latest = cache.stream("ratings", ListSerializer(RatingDto.serializer())) { sessions.api().ratings().ratings }.last()
        if (latest.error == null) ratingsSession = session
        return latest.data
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

    /**
     * Mealie reads a text holding both "<" and ">" as a web page and asks its AI to transcribe it
     * first, one more pass in a row; a recipe's text loses nothing by their look-alikes.
     */
    private fun plainText(text: String): String = if ('<' in text && '>' in text) text.replace('<', '‹').replace('>', '›') else text

    /**
     * Lets the group's AI provider build a recipe from a link, a text, [photos] or several of them,
     * optionally translated. The photos are JPEGs, each with a file name of its own: Mealie keeps
     * them by name while it reads them.
     */
    suspend fun importWithAi(
        url: String?,
        text: String?,
        translateTo: String?,
        photos: List<File> = emptyList(),
        onProgress: (String) -> Unit,
    ): String {
        val parts =
            buildList {
                text?.takeIf { it.isNotBlank() }?.let { add(MultipartBody.Part.createFormData("content", plainText(it.trim()))) }
                url?.takeIf { it.isNotBlank() }?.let { add(MultipartBody.Part.createFormData("url", it.trim())) }
                translateTo?.let { add(MultipartBody.Part.createFormData("translateLanguage", it)) }
                photos.forEach { add(MultipartBody.Part.createFormData("images", it.name, it.asRequestBody(JPEG))) }
            }
        return importing(onProgress, stream = { importRecipeWithAiStream(parts) }, plain = { importRecipeWithAi(parts) }).also { changed() }
    }

    /** Whether the server may read recipes with AI: Mealie 3.23, which brought the AI import, with a provider set up for the group. */
    suspend fun canImportWithAi(): Boolean = aiImport().enabled

    /**
     * Whether the server's AI may read photos of a recipe too. Mealie's AI import takes them from
     * 3.23 on, where the group also has a provider for images; without one Mealie refuses them.
     */
    suspend fun canImportPhotosWithAi(): Boolean = aiImport().photos

    /** What the group's AI may read, asked once a session, as only an admin changes it. */
    private suspend fun aiImport(): AiImport {
        val session = sessions.current ?: return AiImport.None
        aiImport?.let { (askedFor, answer) -> if (askedFor == session) return answer }
        if (!MealieVersion.isAtLeast(session.serverVersion, 3, 23)) return AiImport.None
        val settings =
            try {
                sessions.api().group().aiProviderSettings
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Asked again next time: the server may just have been out of reach.
                return AiImport.None
            }
        val enabled = settings?.aiEnabled == true
        val answer = AiImport(enabled, photos = enabled && settings?.imageProviderEnabled == true)
        aiImport = session to answer
        return answer
    }

    private data class AiImport(val enabled: Boolean, val photos: Boolean) {
        companion object {
            val None = AiImport(enabled = false, photos = false)
        }
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

    /** The slugs of the recipes linking to [url] that the signed-in user added since [since] was taken. */
    suspend fun addedFrom(url: String, since: Set<String>): List<String> =
        mealieCall {
            val user = userId()
            newest().filter { it.id !in since && (it.userId == null || it.userId == user) && it.orgURL == url }.map { it.slug }
        }

    /** The photo of the recipe with [recipeId] as Mealie stores it, or null when it has none. */
    suspend fun photo(recipeId: String): ByteArray? =
        try {
            mealieCall { sessions.api().recipePhoto(recipeId).use { body -> withContext(Dispatchers.IO) { body.bytes() } } }
        } catch (_: AromaError.NotFound) {
            null
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
     * and forgets the ones deleted or renamed on the server. Notes which recipes each cookbook holds,
     * so cookbooks open offline too.
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
        // The next run tries again; the recipes are kept all the same.
        attempt {
            val api = sessions.api()
            api.cookbooks().items.forEach { book ->
                rememberCookbook(book.id, api.recipes(page = 1, perPage = -1, cookbook = book.id).items.map { it.id }, whole = true)
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

        /** More recipes than a household keeps, so the stored copy shows them all. */
        private const val STORED_LIMIT = 10_000

        private val IdsSerializer = ListSerializer(String.serializer())

        private fun cookbookKey(id: String) = "cookbook-recipes:$id"

        private const val PARALLEL_DOWNLOADS = 4

        /** Enough of the newest recipes to spot one added meanwhile, even if others arrive at the same time. */
        private const val NEWEST_WINDOW = 10

        private val CHANGES_SETTLE = 1.seconds

        private val JPEG = "image/jpeg".toMediaType()
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
