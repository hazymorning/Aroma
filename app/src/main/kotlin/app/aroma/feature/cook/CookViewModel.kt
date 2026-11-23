package app.aroma.feature.cook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.data.reminder.RatingReminder
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.settings.SettingsRepository
import app.aroma.data.toAromaError
import app.aroma.timer.CookingTimer
import app.aroma.timer.CookingTimers
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
    private val settings: SettingsRepository,
    private val reminder: RatingReminder,
    private val userId: String?,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val keepScreenOn: StateFlow<Boolean> =
        settings.settings
            .map { it.keepScreenOn }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val base = MutableStateFlow(CookUiState(scale = scale))

    val state: StateFlow<CookUiState> =
        combine(
            base,
            timers.timers,
            timers.now,
            settings.settings.map { it.ingredientsInSteps },
            saved.getStateFlow(CHECKED, IntArray(0)),
        ) { state, all, now, ingredientsInSteps, checked ->
            val ownTimers = all.filter { it.recipeName == state.recipe?.name }
            state.copy(
                timers = ownTimers.map { it.toUi(now) },
                exactAlarmHint = ownTimers.any { it.isRunning } && !timers.canUseExactAlarms,
                ingredientsInSteps = ingredientsInSteps,
                checkedIngredients = checked.toSet(),
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
                .catch { error -> base.update { it.copy(isLoading = false, error = error.toAromaError()) } }
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

    /** Ticked off while cooking; kept until cook mode closes, even if Android ends the app meanwhile. */
    fun toggleIngredient(index: Int) {
        val checked = saved.get<IntArray>(CHECKED)?.toSet().orEmpty()
        saved[CHECKED] = (if (index in checked) checked - index else checked + index).toIntArray()
    }

    fun setIngredientsInSteps(enabled: Boolean) = viewModelScope.launch { settings.setIngredientsInSteps(enabled) }

    fun toggleTimer(id: Long) = timers.toggle(id)

    fun dismissTimer(id: Long) = timers.dismiss(id)

    fun rate(stars: Int) {
        val recipe = base.value.recipe ?: return
        base.update { it.copy(rating = stars.toDouble(), ratedHere = true) }
        viewModelScope.launch {
            // Only a saved rating ends the reminder; otherwise Home still asks later.
            runCatching { recipes.rate(recipe.id, recipe.slug, stars) }.onSuccess { reminder.clear(recipe.id) }
        }
    }

    /** The last step is done: unless it was rated already, Home asks later how it tasted. */
    fun finished() {
        val recipe = base.value.recipe ?: return
        val user = userId ?: return
        if (base.value.ratedHere || recipes.ratings.value[recipe.id]?.rating != null) return
        viewModelScope.launch { reminder.cooked(recipe, user) }
    }

    fun markCooked() {
        viewModelScope.launch {
            runCatching { recipes.markCooked(slug) }
                .onSuccess { base.update { it.copy(markedCooked = true, markFailed = false) } }
                .onFailure { base.update { it.copy(markFailed = true) } }
        }
    }
}

private const val CHECKED = "checked"

/** Seconds are rounded up so a timer shows 0:01 until it is really done. */
private fun CookingTimer.toUi(now: Long) = CookTimerUi(id, label, (remaining(now) + 999) / 1000, isRunning)
