package app.aroma.feature.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.core.model.Meal
import app.aroma.core.model.Organizer
import app.aroma.core.model.OrganizerKind
import app.aroma.core.model.QUICK_MINUTES
import app.aroma.core.model.RecipeQuery
import app.aroma.core.model.RecipeSort
import app.aroma.data.attempt
import app.aroma.data.network.ServerConnection
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.toAromaError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Search and filters for the recipe library. [initial] lets other screens open it pre-filtered; with
 * [withCookbooks], as on the Recipes tab, the household's cookbooks come along.
 */
class RecipesViewModel(
    private val recipes: RecipeRepository,
    private val connection: ServerConnection,
    initial: RecipeFilter = RecipeFilter(),
    private val cookbook: String? = null,
    private val withCookbooks: Boolean = false,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RecipesUiState(filter = initial))
    val state: StateFlow<RecipesUiState> = mutableState.asStateFlow()

    private val query = MutableStateFlow(initial.query)
    private var loadJob: Job? = null
    private var organizersJob: Job? = null
    private var cookbooksJob: Job? = null
    private var nextPage = 1
    private var randomSeed = UUID.randomUUID().toString()

    /** Done once the categories and tags are known, from the phone's copy or from Mealie, or could not be. */
    private val organizersKnown = CompletableDeferred<Unit>()

    /** The names the shown meal was looked up by, to look again when Mealie knows other ones. */
    private var mealNames: List<String>? = null

    init {
        loadOrganizers()
        if (withCookbooks) loadCookbooks()
        reload()
        observeQuery()
        observeSaved()
        viewModelScope.launch { recipes.changes.collect { reload() } }
        viewModelScope.launch {
            // Mealie answers again: load anew. Out of reach: what shows is the phone's copy.
            connection.isReachable.drop(1).collect { reachable ->
                if (reachable) loadAll() else mutableState.update { it.copy(isOffline = true) }
            }
        }
    }

    /** The recipes the user saved, which wear the bookmark ribbon on their cards. */
    private fun observeSaved() {
        viewModelScope.launch { attempt { recipes.loadRatings() } }
        viewModelScope.launch {
            recipes.ratings.collect { ratings ->
                mutableState.update { state -> state.copy(saved = ratings.filterValues { it.isFavorite }.keys) }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeQuery() =
        viewModelScope.launch {
            query
                .drop(1)
                .debounce(300)
                .distinctUntilChanged()
                .collect { reload() }
        }

    private fun loadOrganizers() {
        organizersJob?.cancel()
        organizersJob =
            viewModelScope.launch {
                recipes
                    .organizers()
                    .catch { }
                    .collect { loadable ->
                        val data = loadable.data
                        mutableState.update { it.copy(categories = data.categories, tags = data.tags) }
                        organizersKnown.complete(Unit)
                        // A meal filed under a category Mealie has only now: look again.
                        val looked = mealNames
                        if (looked != null && looked != state.value.mealNames()) reload()
                    }
                organizersKnown.complete(Unit)
            }
    }

    private fun loadCookbooks() {
        cookbooksJob?.cancel()
        cookbooksJob =
            viewModelScope.launch {
                recipes
                    .cookbooks()
                    .catch { }
                    .collect { loadable -> mutableState.update { it.copy(cookbooks = loadable.data) } }
            }
    }

    private fun loadAll(freshRatings: Boolean = false) {
        loadOrganizers()
        if (withCookbooks) loadCookbooks()
        reload(freshRatings)
    }

    /** A pull to refresh: all of it anew from Mealie; the indicator shows until the recipes are back. */
    fun refresh() {
        mutableState.update { it.copy(isRefreshing = true) }
        loadAll(freshRatings = true)
    }

    fun onQueryChange(text: String) {
        mutableState.update { it.copy(filter = it.filter.copy(query = text)) }
        query.value = text
    }

    fun toggleFavorites() = changeFilter { it.copy(favoritesOnly = !it.favoritesOnly) }

    fun toggleQuick() = changeFilter { it.copy(quick = !it.quick) }

    fun toggleLongAgo() = changeFilter { it.copy(longAgo = !it.longAgo) }

    /** One meal at a time: choosing another replaces it, choosing it again clears it. */
    fun toggleMeal(meal: Meal) = changeFilter { it.copy(meal = meal.takeIf { _ -> it.meal != meal }) }

    fun toggleOrganizer(kind: OrganizerKind, organizer: Organizer) =
        changeFilter { filter ->
            fun Set<Organizer>.toggle(): Set<Organizer> {
                val present = any { it.id == organizer.id }
                return if (present) filterNot { it.id == organizer.id }.toSet() else this + organizer
            }
            when (kind) {
                OrganizerKind.Category -> filter.copy(categories = filter.categories.toggle())
                OrganizerKind.Tag -> filter.copy(tags = filter.tags.toggle())
                OrganizerKind.Tool -> filter.copy(tools = filter.tools.toggle())
            }
        }

    fun setSort(sort: RecipeSort) {
        if (sort == RecipeSort.Random) randomSeed = UUID.randomUUID().toString()
        changeFilter { it.copy(sort = sort) }
    }

    fun clearFilters() = changeFilter { RecipeFilter(query = it.query, sort = it.sort) }

    private fun changeFilter(change: (RecipeFilter) -> RecipeFilter) {
        mutableState.update { it.copy(filter = change(it.filter)) }
        reload()
    }

    fun reload() = reload(freshRatings = false)

    private fun reload(freshRatings: Boolean) {
        loadJob?.cancel()
        nextPage = 1
        mutableState.update { it.copy(isLoading = it.recipes.isEmpty(), error = null) }
        loadJob =
            viewModelScope.launch {
                // Favourites may have changed on another device.
                if (freshRatings && state.value.filter.favoritesOnly) attempt { recipes.loadRatings(fresh = true) }
                loadPage(replace = true)
            }
    }

    fun loadMore() {
        val current = mutableState.value
        if (current.isLoadingMore || !current.canLoadMore || loadJob?.isActive == true) return
        mutableState.update { it.copy(isLoadingMore = true) }
        loadJob = viewModelScope.launch { loadPage(replace = false) }
    }

    private suspend fun loadPage(replace: Boolean) {
        val filter = mutableState.value.filter
        // A meal is found by the names of its categories and tags, which the first page waits for.
        if (filter.meal != null) organizersKnown.await()
        val names = mutableState.value.mealNames()
        mealNames = names
        if (names != null && names.isEmpty()) {
            // Nothing is filed under this meal yet, so nothing can match; the page says how to file it.
            mutableState.update {
                it.copy(
                    recipes = emptyList(),
                    total = 0,
                    canLoadMore = false,
                    isLoading = false,
                    isLoadingMore = false,
                    isRefreshing = false,
                )
            }
            return
        }
        val query =
            RecipeQuery(
                search = filter.query,
                favoritesOnly = filter.favoritesOnly,
                categories = filter.categories.map { it.id },
                tags = filter.tags.map { it.id },
                tools = filter.tools.map { it.id },
                cookbook = cookbook,
                mealNames = names.orEmpty(),
                readyWithin = QUICK_MINUTES.takeIf { filter.quick },
                cookedBefore =
                    java.time.LocalDate
                        .now()
                        .minusWeeks(LONG_AGO_WEEKS)
                        .takeIf { filter.longAgo },
                sort = filter.sort,
                seed = randomSeed,
            )
        try {
            val page = recipes.page(query, nextPage)
            nextPage = page.page + 1
            mutableState.update {
                it.copy(
                    recipes = if (replace) page.items else (it.recipes + page.items).distinctBy { recipe -> recipe.id },
                    total = page.total,
                    canLoadMore = page.hasMore,
                    isOffline = page.isOffline,
                    isLoading = false,
                    isLoadingMore = false,
                    isRefreshing = false,
                    error = null,
                )
            }
        } catch (error: Throwable) {
            val failure = error.toAromaError()
            mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    isRefreshing = false,
                    error = failure,
                    recipes = if (replace) emptyList() else it.recipes,
                    canLoadMore = false,
                )
            }
        }
    }
}

/** How long a recipe has not been cooked to count as cooked long ago. */
private const val LONG_AGO_WEEKS = 4L
