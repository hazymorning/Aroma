package app.kochi.feature.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kochi.core.model.MealPlanEntry
import app.kochi.core.model.MealType
import app.kochi.core.model.RecipeQuery
import app.kochi.core.model.RecipeSummary
import app.kochi.data.KochiError
import app.kochi.data.repository.MealPlanRepository
import app.kochi.data.repository.RecipeRepository
import app.kochi.data.repository.ShoppingRepository
import app.kochi.data.settings.SettingsRepository
import app.kochi.data.toKochiError
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** Recipe search inside the "plan a meal" sheet. */
data class PlanSearchState(val query: String = "", val results: List<RecipeSummary> = emptyList(), val isLoading: Boolean = false)

sealed interface MealPlanEvent {
    data class AddedToList(val listName: String) : MealPlanEvent

    data class Failed(val error: KochiError) : MealPlanEvent
}

/** Shows a rolling week that starts today, which is what matters when planning ahead. */
class MealPlanViewModel(
    private val mealPlans: MealPlanRepository,
    private val recipes: RecipeRepository,
    private val shopping: ShoppingRepository,
    private val settings: SettingsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val today get() = LocalDate.now(clock)

    private val mutableState = MutableStateFlow(MealPlanUiState(today = today, weekStart = today))
    val state: StateFlow<MealPlanUiState> = mutableState.asStateFlow()

    private val mutableSearch = MutableStateFlow(PlanSearchState())
    val search: StateFlow<PlanSearchState> = mutableSearch.asStateFlow()

    private val mutableEvents = MutableSharedFlow<MealPlanEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<MealPlanEvent> = mutableEvents.asSharedFlow()

    private val searchQuery = MutableStateFlow("")
    private var loadJob: Job? = null

    init {
        load(refreshing = false)
        observeSearch()
    }

    fun previousWeek() = showWeek(state.value.weekStart.minusDays(7))

    fun nextWeek() = showWeek(state.value.weekStart.plusDays(7))

    fun thisWeek() = showWeek(today)

    fun refresh() = load(refreshing = true)

    private fun showWeek(start: LocalDate) {
        mutableState.update { it.copy(weekStart = start, today = today, isLoading = true, days = emptyList(), error = null) }
        load(refreshing = false)
    }

    private fun load(refreshing: Boolean) {
        loadJob?.cancel()
        val start = state.value.weekStart
        mutableState.update { it.copy(isRefreshing = refreshing) }
        loadJob =
            viewModelScope.launch {
                mealPlans
                    .range(start, 7)
                    .catch { error ->
                        mutableState.update { it.copy(isLoading = false, isRefreshing = false, error = error.toKochiError()) }
                    }.collect { loadable ->
                        val byDay = loadable.data.groupBy { it.date }
                        val week = (0L until 7L).map { start.plusDays(it) }.map { MealPlanDay(it, byDay[it].orEmpty()) }
                        mutableState.update {
                            it.copy(
                                days = week,
                                isLoading = false,
                                isRefreshing = loadable.isRefreshing && refreshing,
                                isOffline = loadable.isOffline,
                                error = loadable.error,
                            )
                        }
                    }
            }
    }

    fun delete(entry: MealPlanEntry) =
        viewModelScope.launch {
            val before = state.value.days
            mutableState.update { state -> state.copy(days = state.days.map { day -> day.copy(entries = day.entries - entry) }) }
            runCatching { mealPlans.delete(entry.id) }
                .onSuccess { load(refreshing = false) }
                .onFailure { error ->
                    mutableState.update { it.copy(days = before) }
                    mutableEvents.emit(MealPlanEvent.Failed(error.toKochiError()))
                }
        }

    fun add(
        date: LocalDate,
        type: MealType,
        recipe: RecipeSummary?,
        title: String,
        note: String,
    ) = viewModelScope.launch {
        runCatching { mealPlans.add(date, type, recipe?.id, title, note) }
            .onSuccess { load(refreshing = false) }
            .onFailure { mutableEvents.emit(MealPlanEvent.Failed(it.toKochiError())) }
    }

    /** Puts all ingredients of a planned recipe on the most recently used shopping list. */
    fun shop(entry: MealPlanEntry) =
        viewModelScope.launch {
            val recipe = entry.recipe ?: return@launch
            runCatching {
                val lists = shopping.lists().first { !it.isRefreshing }.data
                val preferred = settings.settings.first().lastShoppingList
                val list = lists.firstOrNull { it.id == preferred } ?: lists.firstOrNull() ?: throw KochiError.NotFound()
                shopping.addRecipe(list.id, recipe.id, 1.0, ingredients = null)
                list.name
            }.onSuccess { mutableEvents.emit(MealPlanEvent.AddedToList(it)) }
                .onFailure { mutableEvents.emit(MealPlanEvent.Failed(it.toKochiError())) }
        }

    fun onSearchChange(text: String) {
        mutableSearch.update { it.copy(query = text) }
        searchQuery.value = text
    }

    @OptIn(FlowPreview::class)
    private fun observeSearch() =
        viewModelScope.launch {
            searchQuery.debounce(250).collect { text ->
                mutableSearch.update { it.copy(isLoading = true) }
                val results =
                    runCatching { recipes.page(RecipeQuery(search = text), page = 1, perPage = 20).items }
                        .getOrDefault(emptyList())
                mutableSearch.update { it.copy(results = results, isLoading = false) }
            }
        }
}
