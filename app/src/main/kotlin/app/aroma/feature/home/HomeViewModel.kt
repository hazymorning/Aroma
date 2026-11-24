package app.aroma.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.core.model.Meal
import app.aroma.core.model.MealType
import app.aroma.core.model.RecipeImage
import app.aroma.core.model.RecipeSummary
import app.aroma.data.AromaError
import app.aroma.data.Loadable
import app.aroma.data.isConnectivity
import app.aroma.data.network.ServerConnection
import app.aroma.data.reminder.RatingReminder
import app.aroma.data.repository.AuthRepository
import app.aroma.data.repository.MealPlanRepository
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.repository.ShoppingRepository
import app.aroma.data.session.SessionManager
import app.aroma.data.session.SessionState
import app.aroma.data.settings.SettingsRepository
import app.aroma.data.toAromaError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** What became of planning a suggestion for today. */
sealed interface HomeEvent {
    data class Planned(val date: LocalDate) : HomeEvent

    data class Failed(val error: AromaError) : HomeEvent
}

class HomeViewModel(
    private val recipes: RecipeRepository,
    private val mealPlans: MealPlanRepository,
    private val shopping: ShoppingRepository,
    private val settings: SettingsRepository,
    private val sessions: SessionManager,
    private val auth: AuthRepository,
    private val reminder: RatingReminder,
    private val connection: ServerConnection,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState(greeting = greetingFor(LocalTime.now(clock)), date = LocalDate.now(clock)))
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<HomeEvent> = mutableEvents.asSharedFlow()

    private var loads: List<Job> = emptyList()

    init {
        load(refreshing = false)
        watchUser()
        watchReminder()
        viewModelScope.launch { recipes.changes.collect { load(refreshing = false) } }
        viewModelScope.launch {
            connection.isReachable.drop(1).collect { reachable ->
                if (reachable) load(refreshing = false) else mutableState.update { it.copy(isOffline = true) }
            }
        }
    }

    private fun watchUser() {
        viewModelScope.launch {
            sessions.state.collect { session ->
                val user = (session as? SessionState.SignedIn)?.session ?: return@collect
                mutableState.update { it.copy(firstName = user.fullName.ifBlank { user.username }.substringBefore(' ')) }
            }
        }
    }

    // The recipe being rated from the card, which the card itself puts away once that is saved.
    private var ratingInFlight: String? = null

    /** Asks for a rating of the last recipe cooked, until it has one from anywhere. */
    private fun watchReminder() {
        viewModelScope.launch {
            combine(reminder.pending, recipes.ratings) { cooked, ratings -> cooked to ratings }
                .collect { (cooked, ratings) ->
                    val session = sessions.current
                    val due = cooked?.takeIf { session != null && it.userId == session.userId }
                    // Rated elsewhere meanwhile; a rating from this card clears it itself once saved.
                    if (due != null && due.recipeId != ratingInFlight && ratings[due.recipeId]?.rating != null) {
                        reminder.clear(due.recipeId)
                        return@collect
                    }
                    mutableState.update { state ->
                        state.copy(
                            toRate =
                                due?.let {
                                    RateReminder(
                                        recipeId = it.recipeId,
                                        slug = it.slug,
                                        name = it.name,
                                        image = it.imageVersion?.let { version -> RecipeImage(session!!.serverUrl, it.recipeId, version) },
                                        stars = state.toRate?.takeIf { shown -> shown.recipeId == it.recipeId }?.stars,
                                    )
                                },
                        )
                    }
                }
        }
    }

    fun rateCooked(stars: Int) {
        val toRate = state.value.toRate ?: return
        mutableState.update { it.copy(toRate = toRate.copy(stars = stars)) }
        ratingInFlight = toRate.recipeId
        viewModelScope.launch {
            try {
                recipes.rate(toRate.recipeId, toRate.slug, stars)
            } catch (_: AromaError) {
                ratingInFlight = null
                mutableState.update { it.copy(toRate = toRate) }
                return@launch
            }
            // Long enough to see the stars fill before the card goes.
            delay(THANKS_MILLIS)
            reminder.clear(toRate.recipeId)
            ratingInFlight = null
        }
    }

    fun dismissReminder() {
        val toRate = state.value.toRate ?: return
        viewModelScope.launch { reminder.clear(toRate.recipeId) }
    }

    fun refresh() = load(refreshing = true)

    /** Plans [recipe] for today, as the meal of the hour. */
    fun planToday(recipe: RecipeSummary) {
        val today = LocalDate.now(clock)
        viewModelScope.launch {
            try {
                mealPlans.add(today, state.value.meal.mealType, recipe.id, "", "")
                mutableEvents.emit(HomeEvent.Planned(today))
                load(refreshing = false)
            } catch (error: AromaError) {
                mutableEvents.emit(HomeEvent.Failed(error))
            }
        }
    }

    private fun load(refreshing: Boolean) {
        loads.forEach { it.cancel() }
        val today = LocalDate.now(clock)
        mutableState.update {
            it.copy(
                isRefreshing = refreshing,
                isOffline = false,
                error = null,
                greeting = greetingFor(LocalTime.now(clock)),
                date = today,
            )
        }
        // The page shows as soon as every section has something to show, from the phone's copy or from
        // Mealie; a pull to refresh ends once every section has heard from Mealie or found it out of reach.
        val sections = Sections(count = 5)
        val meal = Meal.at(LocalTime.now(clock))
        loads =
            listOf(
                viewModelScope.launch { refreshUser() },
                collect(mealPlans.range(today.with(DayOfWeek.MONDAY), DAYS_OF_WEEK), sections) { copy(week = it) },
                collect(suggestions(meal, today), sections) { (forMeal, list) ->
                    copy(meal = meal, suggestionsForMeal = forMeal, suggestions = list)
                },
                collect(recipes.quick(today), sections) { copy(quick = it) },
                collect(recipes.recent(), sections) { copy(recent = it) },
                collect(openItems(), sections) { copy(openItems = it) },
            )
    }

    /** Counts down the sections of one load, all on the main thread. */
    private inner class Sections(count: Int) {
        private var unshown = count
        private var unsettled = count

        fun shown() {
            if (--unshown == 0) mutableState.update { it.copy(isLoading = false) }
        }

        fun settled() {
            if (--unsettled == 0) mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    /** Today's choice for [meal]; any recipes, and false, where the household has no category or tag for it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun suggestions(meal: Meal, today: LocalDate): Flow<Loadable<Pair<Boolean, List<RecipeSummary>>>> =
        recipes
            .organizers()
            .map { meal.namesIn(it.data) }
            .distinctUntilChanged()
            .flatMapLatest { names ->
                recipes.suggestions(names, today).map { loadable ->
                    Loadable(names.isNotEmpty() to loadable.data, loadable.isRefreshing, loadable.error)
                }
            }

    /** What is still to buy on the list in use: the one last opened, or the first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun openItems(): Flow<Loadable<Int>> =
        settings.settings
            .map { it.lastShoppingList }
            .distinctUntilChanged()
            .flatMapLatest { preferred ->
                shopping.lists().flatMapLatest { lists ->
                    val id = (lists.data.firstOrNull { it.id == preferred } ?: lists.data.firstOrNull())?.id
                    if (id == null) {
                        flowOf(Loadable(0, lists.isRefreshing, lists.error))
                    } else {
                        shopping.observe(id).map { list ->
                            Loadable(list?.items?.count { !it.checked } ?: 0, lists.isRefreshing, lists.error)
                        }
                    }
                }
            }

    /** A new name set in Mealie; the page neither waits for it nor misses it. */
    private suspend fun refreshUser() {
        try {
            auth.refreshUser()
        } catch (_: AromaError) {
            // Offline or signed out meanwhile: the stored name stays.
        }
    }

    private fun <T> collect(source: Flow<Loadable<T>>, sections: Sections, apply: HomeUiState.(T) -> HomeUiState): Job =
        viewModelScope.launch {
            var shown = false
            var settled = false
            source
                .catch { error ->
                    val failure = error.toAromaError()
                    mutableState.update { it.copy(error = failure, isOffline = it.isOffline || failure.isConnectivity) }
                }.collect { loadable ->
                    mutableState.update { it.apply(loadable.data).copy(isOffline = it.isOffline || loadable.isOffline) }
                    if (!shown) {
                        shown = true
                        sections.shown()
                    }
                    if (!loadable.isRefreshing && !settled) {
                        settled = true
                        sections.settled()
                    }
                }
            if (!shown) sections.shown()
            if (!settled) sections.settled()
        }

    companion object {
        private const val THANKS_MILLIS = 700L
        private const val DAYS_OF_WEEK = 7L

        fun greetingFor(time: LocalTime): Greeting =
            when (time.hour) {
                in 5..10 -> Greeting.Morning
                in 11..17 -> Greeting.Afternoon
                else -> Greeting.Evening
            }
    }
}

/** The plan's meal for a meal of the day. */
private val Meal.mealType: MealType
    get() =
        when (this) {
            Meal.Breakfast -> MealType.Breakfast
            Meal.Lunch -> MealType.Lunch
            Meal.Snack -> MealType.Snack
            Meal.Dinner -> MealType.Dinner
            Meal.Dessert -> MealType.Dessert
            Meal.Side -> MealType.Side
            Meal.Drink -> MealType.Drink
        }
