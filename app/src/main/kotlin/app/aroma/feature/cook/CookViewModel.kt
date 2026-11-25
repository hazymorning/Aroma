package app.aroma.feature.cook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.data.AromaError
import app.aroma.data.reminder.RatingReminder
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.repository.TimelineRepository
import app.aroma.data.settings.IngredientsInSteps
import app.aroma.data.settings.SettingsRepository
import app.aroma.data.toAromaError
import app.aroma.timer.CookingTimer
import app.aroma.timer.CookingTimers
import kotlinx.coroutines.Job
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
    private val timeline: TimelineRepository,
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
            settings.settings,
            saved.getStateFlow(CHECKED, IntArray(0)),
        ) { state, all, now, prefs, checked ->
            val ownTimers = all.filter { it.recipeName == state.recipe?.name }
            state.copy(
                timers = ownTimers.map { it.toUi(now) },
                exactAlarmHint = ownTimers.any { it.isRunning } && !timers.canUseExactAlarms,
                ingredientsInSteps = prefs.ingredientsInSteps,
                amountsInSteps = prefs.amountsInSteps,
                showNextStep = prefs.showNextStep,
                measures = prefs.measureSystem,
                checkedIngredients = checked.toSet(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), base.value)

    private var loadJob: Job? = null

    init {
        load()
    }

    /** Loads the recipe anew; a load still under way gives way, so an older answer never shows over a newer one. */
    fun load() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                base.update { it.copy(isLoading = it.recipe == null, error = null) }
                recipes
                    .recipe(slug)
                    .catch { error -> base.update { it.copy(isLoading = false, error = error.toAromaError()) } }
                    .collect { loadable ->
                        val rating = recipes.ratings.value[loadable.data.id]
                        base.update {
                            it.copy(
                                recipe = loadable.data,
                                isLoading = false,
                                userRating = if (rating != null) rating.rating else it.userRating,
                            )
                        }
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

    fun setIngredientsInSteps(shown: IngredientsInSteps) = viewModelScope.launch { settings.setIngredientsInSteps(shown) }

    fun toggleTimer(id: Long) = timers.toggle(id)

    fun dismissTimer(id: Long) = timers.dismiss(id)

    fun useExactAlarms() = timers.useExactAlarms()

    /** Only a saved rating ends the reminder; otherwise Home still asks later. 0 takes the rating back. */
    fun rate(stars: Int) {
        val recipe = base.value.recipe ?: return
        val previous = base.value.userRating
        base.update { it.copy(userRating = stars.takeIf { s -> s > 0 }?.toDouble()) }
        viewModelScope.launch {
            try {
                recipes.rate(recipe.id, recipe.slug, stars)
                base.update { it.copy(ratedHere = stars > 0) }
                if (stars > 0) reminder.clear(recipe.id)
            } catch (_: AromaError) {
                base.update { it.copy(userRating = previous) }
            }
        }
    }

    /** The last step is done: unless it was rated already, Home asks later how it tasted. */
    fun finished() {
        val recipe = base.value.recipe ?: return
        val user = userId ?: return
        if (base.value.ratedHere || recipes.ratings.value[recipe.id]?.rating != null) return
        viewModelScope.launch { reminder.cooked(recipe, user) }
    }

    /** [subject] is the timeline's line for it, such as "Mara made this". */
    fun markCooked(subject: String) {
        val recipe = base.value.recipe ?: return
        viewModelScope.launch {
            try {
                timeline.cooked(recipe.id, recipe.slug, subject)
                base.update { it.copy(markedCooked = true, markFailed = false) }
            } catch (_: AromaError) {
                base.update { it.copy(markFailed = true) }
            }
        }
    }
}

private const val CHECKED = "checked"

/** Seconds are rounded up so a timer shows 0:01 until it is really done. */
private fun CookingTimer.toUi(now: Long) = CookTimerUi(id, label, (remaining(now) + 999) / 1000, isRunning)
