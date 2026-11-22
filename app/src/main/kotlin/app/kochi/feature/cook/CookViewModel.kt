package app.kochi.feature.cook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kochi.data.repository.RecipeRepository
import app.kochi.data.settings.SettingsRepository
import app.kochi.data.toKochiError
import app.kochi.timer.CookingTimer
import app.kochi.timer.CookingTimers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CookViewModel(
    private val slug: String,
    scale: Double,
    private val recipes: RecipeRepository,
    private val timers: CookingTimers,
    settings: SettingsRepository,
) : ViewModel() {
    val keepScreenOn: StateFlow<Boolean> =
        settings.settings
            .map { it.keepScreenOn }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val base = MutableStateFlow(CookUiState(scale = scale))

    val state: StateFlow<CookUiState> =
        combine(base, timers.timers, timers.now) { state, all, now ->
            val ownTimers = all.filter { it.recipeName == state.recipe?.name }
            state.copy(
                timers = ownTimers.map { it.toUi(now) },
                exactAlarmHint = ownTimers.any { it.isRunning } && !timers.canUseExactAlarms,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), base.value)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            base.update { it.copy(isLoading = it.recipe == null, error = null) }
            recipes
                .recipe(slug)
                .catch { error -> base.update { it.copy(isLoading = false, error = error.toKochiError()) } }
                .collect { loadable ->
                    val rating = recipes.ratings.value[loadable.data.id]?.rating
                    base.update { it.copy(recipe = loadable.data, isLoading = false, rating = rating ?: it.rating ?: loadable.data.rating) }
                }
        }
    }

    fun startTimer(seconds: Int, label: String) {
        val name = base.value.recipe?.name ?: return
        timers.start(seconds, label, name)
    }

    fun toggleTimer(id: Long) = timers.toggle(id)

    fun dismissTimer(id: Long) = timers.dismiss(id)

    fun rate(stars: Int) {
        val recipe = base.value.recipe ?: return
        base.update { it.copy(rating = stars.toDouble()) }
        viewModelScope.launch { runCatching { recipes.rate(recipe.id, recipe.slug, stars) } }
    }

    fun markCooked() {
        viewModelScope.launch {
            runCatching { recipes.markCooked(slug) }
                .onSuccess { base.update { it.copy(markedCooked = true, markFailed = false) } }
                .onFailure { base.update { it.copy(markFailed = true) } }
        }
    }
}

/** Seconds are rounded up so a timer shows 0:01 until it is really done. */
private fun CookingTimer.toUi(now: Long) = CookTimerUi(id, label, (remaining(now) + 999) / 1000, isRunning)
