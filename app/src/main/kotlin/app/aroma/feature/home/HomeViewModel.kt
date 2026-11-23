package app.aroma.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.core.model.Meal
import app.aroma.core.model.RecipeImage
import app.aroma.core.model.RecipeSummary
import app.aroma.data.AromaError
import app.aroma.data.Loadable
import app.aroma.data.isConnectivity
import app.aroma.data.reminder.RatingReminder
import app.aroma.data.repository.AuthRepository
import app.aroma.data.repository.MealPlanRepository
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.session.SessionManager
import app.aroma.data.session.SessionState
import app.aroma.data.toAromaError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

class HomeViewModel(
    private val recipes: RecipeRepository,
    private val mealPlans: MealPlanRepository,
    private val sessions: SessionManager,
    private val auth: AuthRepository,
    private val reminder: RatingReminder,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState(greeting = greetingFor(LocalTime.now(clock))))
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    private var loads: List<Job> = emptyList()

    init {
        load(refreshing = false)
        watchUser()
        watchReminder()
        viewModelScope.launch { recipes.changes.collect { load(refreshing = false) } }
    }

    private fun watchUser() {
        viewModelScope.launch {
            sessions.state.collect { session ->
                val user = (session as? SessionState.SignedIn)?.session ?: return@collect
                mutableState.update {
                    it.copy(firstName = user.fullName.ifBlank { user.username }.substringBefore(' '), photo = user.photoUrl)
                }
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

    private fun load(refreshing: Boolean) {
        loads.forEach { it.cancel() }
        val today = LocalDate.now(clock)
        mutableState.update {
            it.copy(
                isRefreshing = refreshing,
                isOffline = false,
                error = null,
                greeting = greetingFor(LocalTime.now(clock)),
            )
        }
        var pending = 5

        fun finished() {
            pending--
            if (pending == 0) mutableState.update { it.copy(isLoading = false, isRefreshing = false) }
        }
        val meal = Meal.at(LocalTime.now(clock))
        loads =
            listOf(
                viewModelScope.launch { refreshUser() },
                collect(mealPlans.range(today, 1), ::finished) { copy(today = it) },
                collect(suggestions(meal, today), ::finished) { (forMeal, list) ->
                    copy(meal = meal, suggestionsForMeal = forMeal, suggestions = list)
                },
                collect(recipes.cookedLongAgo(since = today.minusWeeks(4), day = today), ::finished) { copy(cookedLongAgo = it) },
                collect(recipes.recent(), ::finished) { copy(recent = it) },
                collect(recipes.favorites(fresh = refreshing), ::finished) { copy(favorites = it) },
            )
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

    /** A new name or photo set in Mealie; the page neither waits for it nor misses it. */
    private suspend fun refreshUser() {
        try {
            auth.refreshUser()
        } catch (_: AromaError) {
            // Offline or signed out meanwhile: the stored name and photo stay.
        }
    }

    private fun <T> collect(source: Flow<Loadable<T>>, done: () -> Unit, apply: HomeUiState.(T) -> HomeUiState): Job =
        viewModelScope.launch {
            var reported = false
            source
                .catch { error ->
                    val failure = error.toAromaError()
                    mutableState.update { it.copy(error = failure, isOffline = it.isOffline || failure.isConnectivity) }
                }.collect { loadable ->
                    mutableState.update { it.apply(loadable.data).copy(isOffline = it.isOffline || loadable.isOffline) }
                    if (!loadable.isRefreshing && !reported) {
                        reported = true
                        done()
                    }
                }
            if (!reported) done()
        }

    companion object {
        private const val THANKS_MILLIS = 700L

        fun greetingFor(time: LocalTime): Greeting =
            when (time.hour) {
                in 5..10 -> Greeting.Morning
                in 11..17 -> Greeting.Afternoon
                else -> Greeting.Evening
            }
    }
}
