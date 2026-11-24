package app.aroma.feature.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.core.model.Meal
import app.aroma.core.model.Organizer
import app.aroma.core.model.OrganizerKind
import app.aroma.core.model.Organizers
import app.aroma.core.model.RecipeQuery
import app.aroma.core.model.RecipeSort
import app.aroma.data.network.ServerConnection
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.toAromaError
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

/** Search and filters for the recipe library. [initial] lets other screens open it pre-filtered. */
class RecipesViewModel(
    private val recipes: RecipeRepository,
    private val connection: ServerConnection,
    initial: RecipeFilter = RecipeFilter(),
    private val cookbook: String? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RecipesUiState(filter = initial))
    val state: StateFlow<RecipesUiState> = mutableState.asStateFlow()

    private val query = MutableStateFlow(initial.query)
    private var loadJob: Job? = null
    private var nextPage = 1
    private var randomSeed = UUID.randomUUID().toString()

    init {
        reload()
        loadOrganizers()
        observeQuery()
        viewModelScope.launch { recipes.changes.collect { reload() } }
        viewModelScope.launch {
            // Mealie answers again: load anew. Out of reach: what shows is the phone's copy.
            connection.isReachable.drop(1).collect { reachable ->
                if (reachable) reload() else mutableState.update { it.copy(isOffline = true) }
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

    private fun loadOrganizers() =
        viewModelScope.launch {
            recipes
                .organizers()
                .catch { }
                .collect { loadable ->
                    val data = loadable.data
                    mutableState.update { it.copy(categories = data.categories, tags = data.tags, tools = data.tools) }
                }
        }

    fun onQueryChange(text: String) {
        mutableState.update { it.copy(filter = it.filter.copy(query = text)) }
        query.value = text
    }

    fun toggleFavorites() = changeFilter { it.copy(favoritesOnly = !it.favoritesOnly) }

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

    fun reload() {
        loadJob?.cancel()
        nextPage = 1
        mutableState.update { it.copy(isLoading = it.recipes.isEmpty(), error = null) }
        loadJob = viewModelScope.launch { loadPage(replace = true) }
    }

    fun loadMore() {
        val current = mutableState.value
        if (current.isLoadingMore || !current.canLoadMore || loadJob?.isActive == true) return
        mutableState.update { it.copy(isLoadingMore = true) }
        loadJob = viewModelScope.launch { loadPage(replace = false) }
    }

    private suspend fun loadPage(replace: Boolean) {
        val state = mutableState.value
        val filter = state.filter
        val query =
            RecipeQuery(
                search = filter.query,
                favoritesOnly = filter.favoritesOnly,
                categories = filter.categories.map { it.id },
                tags = filter.tags.map { it.id },
                tools = filter.tools.map { it.id },
                cookbook = cookbook,
                mealNames = filter.meal?.namesIn(Organizers(state.categories, state.tags, state.tools)).orEmpty(),
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
                    error = null,
                )
            }
        } catch (error: Throwable) {
            val failure = error.toAromaError()
            mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = failure,
                    recipes = if (replace) emptyList() else it.recipes,
                    canLoadMore = false,
                )
            }
        }
    }
}
