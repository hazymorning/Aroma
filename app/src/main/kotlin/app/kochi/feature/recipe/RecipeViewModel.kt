package app.kochi.feature.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kochi.core.model.MealType
import app.kochi.core.model.ShoppingListInfo
import app.kochi.data.KochiError
import app.kochi.data.repository.MealPlanRepository
import app.kochi.data.repository.RecipeRepository
import app.kochi.data.repository.ShoppingRepository
import app.kochi.data.session.SessionManager
import app.kochi.data.toKochiError
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

    data class Failed(val error: KochiError) : RecipeEvent
}

class RecipeViewModel(
    private val slug: String,
    private val recipes: RecipeRepository,
    private val shopping: ShoppingRepository,
    private val mealPlans: MealPlanRepository,
    private val sessions: SessionManager,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RecipeUiState())
    val state: StateFlow<RecipeUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<RecipeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RecipeEvent> = mutableEvents.asSharedFlow()

    /** Null while the lists are loading. */
    private val mutableLists = MutableStateFlow<List<ShoppingListInfo>?>(null)
    val shoppingLists: StateFlow<List<ShoppingListInfo>?> = mutableLists.asStateFlow()

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
        viewModelScope.launch { runCatching { recipes.refreshRatings() } }
    }

    fun load() {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = it.recipe == null, error = null) }
            recipes
                .recipe(slug)
                .catch { error -> mutableState.update { it.copy(isLoading = false, error = error.toKochiError()) } }
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

    /** Recipe name plus a link to it in Mealie (or to the original page). */
    fun shareText(): String? {
        val recipe = state.value.recipe ?: return null
        val session = sessions.current
        val link = session?.groupSlug?.let { group -> "${session.serverUrl}/g/$group/r/${recipe.slug}" } ?: recipe.sourceUrl
        return listOfNotNull(recipe.name, link).joinToString("\n")
    }

    fun setScale(scale: Double) = mutableState.update { it.copy(scale = scale) }

    fun toggleIngredient(index: Int) =
        mutableState.update {
            val checked = it.checkedIngredients
            it.copy(checkedIngredients = if (index in checked) checked - index else checked + index)
        }

    fun toggleFavorite() = act { recipe -> recipes.setFavorite(recipe.id, recipe.slug, !state.value.isFavorite) }

    fun rate(stars: Int) = act { recipe -> recipes.rate(recipe.id, recipe.slug, stars) }

    fun loadShoppingLists() =
        viewModelScope.launch {
            runCatching { shopping.lists().first { !it.isRefreshing }.data }
                .onSuccess { mutableLists.value = it }
                .onFailure {
                    mutableLists.value = emptyList()
                    mutableEvents.emit(RecipeEvent.Failed(it.toKochiError()))
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
            mutableEvents.emit(RecipeEvent.AddedToList(list.name))
        }

    fun plan(date: LocalDate, type: MealType) =
        act { recipe ->
            mealPlans.add(date, type, recipe.id, title = "", note = "")
            mutableEvents.emit(RecipeEvent.Planned(date))
        }

    fun delete() =
        act { recipe ->
            recipes.delete(recipe.slug)
            mutableEvents.emit(RecipeEvent.Deleted)
        }

    private fun act(block: suspend (app.kochi.core.model.Recipe) -> Unit) {
        val recipe = state.value.recipe ?: return
        viewModelScope.launch {
            try {
                block(recipe)
            } catch (error: Throwable) {
                mutableEvents.emit(RecipeEvent.Failed(error.toKochiError()))
                runCatching { recipes.refreshRatings() }
            }
        }
    }
}
