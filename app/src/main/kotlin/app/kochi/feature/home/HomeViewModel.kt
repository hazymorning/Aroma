package app.kochi.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kochi.data.Loadable
import app.kochi.data.isConnectivity
import app.kochi.data.repository.MealPlanRepository
import app.kochi.data.repository.RecipeRepository
import app.kochi.data.session.SessionManager
import app.kochi.data.toKochiError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

class HomeViewModel(
    private val recipes: RecipeRepository,
    private val mealPlans: MealPlanRepository,
    sessions: SessionManager,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            HomeUiState(
                greeting = greetingFor(LocalTime.now(clock)),
                firstName = sessions.current?.let { it.fullName.ifBlank { it.username } }?.substringBefore(' '),
            ),
        )
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    private var loads: List<Job> = emptyList()

    init {
        load(refreshing = false)
    }

    fun refresh() = load(refreshing = true)

    private fun load(refreshing: Boolean) {
        loads.forEach { it.cancel() }
        mutableState.update {
            it.copy(
                isRefreshing = refreshing,
                isOffline = false,
                error = null,
                greeting = greetingFor(LocalTime.now(clock)),
            )
        }
        var pending = 4

        fun finished() {
            pending--
            if (pending == 0) mutableState.update { it.copy(isLoading = false, isRefreshing = false) }
        }
        loads =
            listOf(
                collect(mealPlans.range(LocalDate.now(clock), 1), ::finished) { copy(today = it) },
                collect(recipes.recent(), ::finished) { copy(recent = it) },
                collect(recipes.favorites(), ::finished) { copy(favorites = it) },
                collect(recipes.cookbooks(), ::finished) { copy(cookbooks = it) },
            )
    }

    private fun <T> collect(source: Flow<Loadable<T>>, done: () -> Unit, apply: HomeUiState.(T) -> HomeUiState): Job =
        viewModelScope.launch {
            var reported = false
            source
                .catch { error ->
                    val failure = error.toKochiError()
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
        fun greetingFor(time: LocalTime): Greeting =
            when (time.hour) {
                in 5..10 -> Greeting.Morning
                in 11..17 -> Greeting.Afternoon
                else -> Greeting.Evening
            }
    }
}
