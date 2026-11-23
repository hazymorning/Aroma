package app.aroma.feature.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.core.format.MeasureSystem
import app.aroma.core.model.MealType
import app.aroma.core.model.ShoppingListInfo
import app.aroma.data.AromaError
import app.aroma.data.attempt
import app.aroma.data.repository.MealPlanRepository
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.repository.ShoppingRepository
import app.aroma.data.session.SessionManager
import app.aroma.data.settings.SettingsRepository
import app.aroma.data.toAromaError
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate

/** One-off feedback shown as a snackbar. */
sealed interface RecipeEvent {
    data class AddedToList(val listName: String) : RecipeEvent

    data class Planned(val date: LocalDate) : RecipeEvent

    data object Deleted : RecipeEvent

    data class Failed(val error: AromaError) : RecipeEvent
}

class RecipeViewModel(
    private val slug: String,
    private val recipes: RecipeRepository,
    private val shopping: ShoppingRepository,
    private val mealPlans: MealPlanRepository,
    private val sessions: SessionManager,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RecipeUiState())
    val state: StateFlow<RecipeUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<RecipeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RecipeEvent> = mutableEvents.asSharedFlow()

    /** Null while the lists are loading. */
    private val mutableLists = MutableStateFlow<List<ShoppingListInfo>?>(null)
    val shoppingLists: StateFlow<List<ShoppingListInfo>?> = mutableLists.asStateFlow()

    /** The list used last, offered first. */
    private val mutablePreferredList = MutableStateFlow<String?>(null)
    val preferredListId: StateFlow<String?> = mutablePreferredList.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            recipes.ratings.collect { ratings ->
                mutableState.update { state ->
                    val rating = state.recipe?.let { ratings[it.id] }
                    state.copy(isFavorite = rating?.isFavorite ?: state.isFavorite, userRating = rating?.rating ?: state.userRating)
                }
            }
        }
        viewModelScope.launch { attempt { recipes.loadRatings() } }
        viewModelScope.launch {
            settings.settings.collect { prefs ->
                mutableState.update { it.copy(measures = prefs.measureSystem, ingredientsInSteps = prefs.ingredientsInSteps) }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = it.recipe == null, error = null) }
            recipes
                .recipe(slug)
                .catch { error -> mutableState.update { it.copy(isLoading = false, error = error.toAromaError()) } }
                .collect { loadable ->
                    val rating = recipes.ratings.value[loadable.data.id]
                    mutableState.update {
                        it.copy(
                            recipe = loadable.data,
                            isLoading = false,
                            isOffline = loadable.isOffline,
                            isFavorite = rating?.isFavorite ?: it.isFavorite,
                            userRating = rating?.rating ?: it.userRating,
                        )
                    }
                }
        }
    }

    /**
     * Recipe name plus the original page, which anyone can open. Only recipes without one get a
     * link into Mealie, which hands out the server's address and opens for its own users only.
     */
    fun shareText(): String? {
        val recipe = state.value.recipe ?: return null
        val session = sessions.current
        val link = recipe.sourceUrl ?: session?.groupSlug?.let { group -> "${session.serverUrl}/g/$group/r/${recipe.slug}" }
        return listOfNotNull(recipe.name, link).joinToString("\n")
    }

    fun setScale(scale: Double) = mutableState.update { it.copy(scale = scale) }

    /** Shows amounts in [system] here and everywhere else, as the setting of the same name does. */
    fun setMeasures(system: MeasureSystem) {
        viewModelScope.launch { settings.setMeasureSystem(system) }
    }

    fun toggleIngredient(index: Int) =
        mutableState.update {
            val checked = it.checkedIngredients
            it.copy(checkedIngredients = if (index in checked) checked - index else checked + index)
        }

    fun toggleFavorite() = act { recipe -> recipes.setFavorite(recipe.id, recipe.slug, !state.value.isFavorite) }

    fun rate(stars: Int) = act { recipe -> recipes.rate(recipe.id, recipe.slug, stars) }

    fun loadShoppingLists() =
        viewModelScope.launch {
            mutablePreferredList.value = settings.settings.first().lastShoppingList
            attempt { shopping.lists().first { !it.isRefreshing }.data }
                .onSuccess { mutableLists.value = it }
                .onFailure {
                    mutableLists.value = emptyList()
                    mutableEvents.emit(RecipeEvent.Failed(it.toAromaError()))
                }
        }

    /** Adds the chosen ingredients (by position in the recipe) to a shopping list. */
    fun addToList(list: ShoppingListInfo, ingredientIndexes: Set<Int>) =
        act { recipe ->
            val raw =
                recipes
                    .recipeJson(recipe.slug)["recipeIngredient"]
                    ?.jsonArray
                    ?.map { it.jsonObject }
                    .orEmpty()
            val chosen: List<JsonObject> = raw.filterIndexed { index, _ -> index in ingredientIndexes }
            shopping.addRecipe(list.id, recipe.id, state.value.scale, chosen)
            settings.setLastShoppingList(list.id)
            mutableEvents.emit(RecipeEvent.AddedToList(list.name))
            confirm(QuickAction.Shop)
        }

    fun plan(date: LocalDate, type: MealType) =
        act { recipe ->
            mealPlans.add(date, type, recipe.id, title = "", note = "")
            mutableEvents.emit(RecipeEvent.Planned(date))
            confirm(QuickAction.Plan)
        }

    /** Shows a check on the action's button for a moment. */
    private suspend fun confirm(action: QuickAction) {
        mutableState.update { it.copy(confirmed = action) }
        delay(CONFIRM_MILLIS)
        mutableState.update { if (it.confirmed == action) it.copy(confirmed = null) else it }
    }

    fun delete() =
        act { recipe ->
            recipes.delete(recipe.slug)
            mutableEvents.emit(RecipeEvent.Deleted)
        }

    private fun act(block: suspend (app.aroma.core.model.Recipe) -> Unit) {
        val recipe = state.value.recipe ?: return
        viewModelScope.launch {
            try {
                block(recipe)
            } catch (error: AromaError) {
                mutableEvents.emit(RecipeEvent.Failed(error))
            }
        }
    }

    private companion object {
        const val CONFIRM_MILLIS = 1500L
    }
}
