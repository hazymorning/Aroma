package app.aroma.feature.recipe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.core.format.MeasureSystem
import app.aroma.core.model.Filing
import app.aroma.core.model.MealType
import app.aroma.core.model.Organizer
import app.aroma.core.model.Organizers
import app.aroma.core.model.Recipe
import app.aroma.core.model.ShoppingListInfo
import app.aroma.core.model.UserRating
import app.aroma.data.AromaError
import app.aroma.data.attempt
import app.aroma.data.network.ServerConnection
import app.aroma.data.repository.MealPlanRepository
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.repository.ShoppingRepository
import app.aroma.data.session.SessionManager
import app.aroma.data.settings.SettingsRepository
import app.aroma.data.toAromaError
import app.aroma.feature.editor.RecipeReworks
import app.aroma.feature.editor.ReworkJob
import app.aroma.feature.editor.ReworkOutcome
import app.aroma.feature.editor.ReworkRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** One-off feedback shown as a snackbar. */
sealed interface RecipeEvent {
    data class AddedToList(val listName: String) : RecipeEvent

    data class Planned(val date: LocalDate) : RecipeEvent

    data object Deleted : RecipeEvent

    /** [recipe] is no longer saved; the snackbar offers to save it again. */
    data class Unsaved(val recipe: Recipe) : RecipeEvent

    data class Failed(val error: AromaError) : RecipeEvent

    /** Mealie's AI reworked the recipe, which [reworked] can undo. */
    data class Reworked(val reworked: ReworkOutcome.Reworked) : RecipeEvent

    data class ReworkFailed(val error: AromaError) : RecipeEvent

    /** The user took back their rating of [stars], which they may put back. */
    data class Unrated(val stars: Int) : RecipeEvent
}

class RecipeViewModel(
    slug: String,
    private val recipes: RecipeRepository,
    private val reworks: RecipeReworks,
    private val shopping: ShoppingRepository,
    private val mealPlans: MealPlanRepository,
    private val sessions: SessionManager,
    private val settings: SettingsRepository,
    private val connection: ServerConnection,
    private val saved: SavedStateHandle,
) : ViewModel() {
    /**
     * The recipe's slug, which a rework that translates its name changes; kept with the page, which
     * Android brings back with the slug it was opened with.
     */
    private var slug: String = saved[SLUG] ?: slug
        set(value) {
            field = value
            saved[SLUG] = value
        }

    private val mutableState = MutableStateFlow(RecipeUiState())
    val state: StateFlow<RecipeUiState> = mutableState.asStateFlow()

    /**
     * The recipe's rework while there is one. Collected by the recipe's page only while it is shown,
     * which is how the rework knows whether to tell in a notification how it ended.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val rework: StateFlow<ReworkJob?> =
        mutableState
            .map { it.recipe?.id }
            .distinctUntilChanged()
            .flatMapLatest { id -> id?.let(reworks::job) ?: flowOf(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private val outbox = Channel<RecipeEvent>(Channel.BUFFERED)
    val events: Flow<RecipeEvent> = outbox.receiveAsFlow()

    /** Null while the lists are loading. */
    private val mutableLists = MutableStateFlow<List<ShoppingListInfo>?>(null)
    val shoppingLists: StateFlow<List<ShoppingListInfo>?> = mutableLists.asStateFlow()

    /** The list used last, offered first. */
    private val mutablePreferredList = MutableStateFlow<String?>(null)
    val preferredListId: StateFlow<String?> = mutablePreferredList.asStateFlow()

    /** The group's categories and tags to file the recipe under; null until asked for. */
    private val mutableOrganizers = MutableStateFlow<Organizers?>(null)
    val organizers: StateFlow<Organizers?> = mutableOrganizers.asStateFlow()

    /** Says the filing on the page changed; Mealie gets the latest, one save at a time. */
    private val filings = Channel<Unit>(Channel.CONFLATED)

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            // Mealie answers again: load anew. Out of reach: what shows is the phone's copy.
            connection.isReachable.drop(1).collect { reachable ->
                if (reachable) load() else mutableState.update { it.copy(isOffline = true) }
            }
        }
        viewModelScope.launch {
            recipes.ratings.collect { ratings ->
                mutableState.update { state -> state.withRating(state.recipe?.let { ratings[it.id] }) }
            }
        }
        viewModelScope.launch { attempt { recipes.loadRatings() } }
        viewModelScope.launch {
            settings.settings.collect { prefs ->
                mutableState.update {
                    it.copy(
                        measures = prefs.measureSystem,
                        ingredientsInSteps = prefs.ingredientsInSteps,
                        amountsInSteps = prefs.amountsInSteps,
                        translate = prefs.translateImports,
                    )
                }
            }
        }
        viewModelScope.launch { filings.receiveAsFlow().collect { saveFiling() } }
        viewModelScope.launch {
            val aiAvailable = recipes.canImportWithAi()
            mutableState.update { it.copy(aiAvailable = aiAvailable) }
        }
    }

    /** Loads the recipe anew; a load still under way gives way, so an older answer never shows over a newer one. */
    fun load() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                mutableState.update { it.copy(isLoading = it.recipe == null, error = null) }
                recipes
                    .recipe(slug)
                    .catch { error -> mutableState.update { it.copy(isLoading = false, error = error.toAromaError()) } }
                    .collect { loadable ->
                        val rating = recipes.ratings.value[loadable.data.id]
                        mutableState.update {
                            it.copy(recipe = loadable.data, isLoading = false, isOffline = loadable.isOffline).withRating(rating)
                        }
                    }
            }
    }

    /**
     * Shows the recipe with [slug] instead, as the menu of a long press does for each recipe pressed:
     * the page starts afresh, keeping only what the settings say, and loads.
     */
    fun show(slug: String) {
        if (slug == this.slug) return
        this.slug = slug
        mutableState.update {
            RecipeUiState(
                measures = it.measures,
                ingredientsInSteps = it.ingredientsInSteps,
                amountsInSteps = it.amountsInSteps,
                aiAvailable = it.aiAvailable,
                translate = it.translate,
            )
        }
        load()
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

    fun toggleFavorite() {
        val saved = state.value.isFavorite
        act { recipe ->
            recipes.setFavorite(recipe.id, recipe.slug, !saved)
            if (saved) outbox.send(RecipeEvent.Unsaved(recipe))
        }
    }

    /** Saves [recipe] again, as the snackbar after letting it go offers; it may no longer be the one shown. */
    fun resave(recipe: Recipe) {
        viewModelScope.launch {
            try {
                recipes.setFavorite(recipe.id, recipe.slug, true)
            } catch (error: AromaError) {
                outbox.send(RecipeEvent.Failed(error))
            }
        }
    }

    /** Rates the recipe [stars] out of five, or with 0 takes the rating back and offers to put it back. */
    fun rate(stars: Int) {
        val before = state.value.userRating
        // The recipe's average still counts the rating taken back, so until Mealie has worked it out again it shows none.
        if (stars == 0) mutableState.update { it.copy(recipe = it.recipe?.copy(rating = null)) }
        act { recipe ->
            recipes.rate(recipe.id, recipe.slug, stars)
            if (stars > 0) return@act
            before?.let { outbox.send(RecipeEvent.Unrated(it.roundToInt())) }
            attempt { recipes.recipe(recipe.slug).last() }.onSuccess { fresh ->
                if (fresh.error == null) mutableState.update { it.copy(recipe = fresh.data) }
            }
        }
    }

    fun loadShoppingLists() =
        viewModelScope.launch {
            mutablePreferredList.value = settings.settings.first().lastShoppingList
            attempt { shopping.lists().first { !it.isRefreshing }.data }
                .onSuccess { mutableLists.value = it }
                .onFailure {
                    mutableLists.value = emptyList()
                    outbox.send(RecipeEvent.Failed(it.toAromaError()))
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
            outbox.send(RecipeEvent.AddedToList(list.name))
            confirm(QuickAction.Shop)
        }

    fun plan(date: LocalDate, type: MealType) =
        act { recipe ->
            mealPlans.add(date, type, recipe.id, title = "", note = "")
            outbox.send(RecipeEvent.Planned(date))
            confirm(QuickAction.Plan)
        }

    fun loadOrganizers() =
        viewModelScope.launch {
            recipes.organizers().catch { }.collect { loadable -> mutableOrganizers.value = loadable.data }
        }

    /** Files the recipe under [filing]: the page shows it at once, Mealie gets it a moment later. */
    fun file(filing: Filing) {
        val recipe = state.value.recipe ?: return
        mutableState.update { it.copy(recipe = recipe.copy(categories = filing.categories, tags = filing.tags)) }
        filings.trySend(Unit)
    }

    /**
     * Saves the filing the page shows. Ones just named come back with their ids, unless they were taken
     * off meanwhile; if Mealie does not take it, the page shows Mealie's again.
     */
    private suspend fun saveFiling() {
        val recipe = state.value.recipe ?: return
        val saved =
            try {
                recipes.file(recipe.slug, Filing(recipe.categories, recipe.tags))
            } catch (error: AromaError) {
                outbox.send(RecipeEvent.Failed(error))
                load()
                return
            }
        val named = (recipe.categories + recipe.tags).filter { it.isNew }
        val made = (saved.categories + saved.tags).filter { organizer -> named.any(organizer::sameAs) }
        if (made.isEmpty()) return
        val withIds = { list: List<Organizer> -> list.map { organizer -> made.firstOrNull(organizer::sameAs) ?: organizer } }
        mutableState.update { state ->
            val now = state.recipe ?: return@update state
            state.copy(recipe = now.copy(categories = withIds(now.categories), tags = withIds(now.tags)))
        }
        mutableOrganizers.update { organizers ->
            organizers?.copy(
                categories = organizers.categories + saved.categories.filter { it in made && organizers.categories.none(it::sameAs) },
                tags = organizers.tags + saved.tags.filter { it in made && organizers.tags.none(it::sameAs) },
            )
        }
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
            outbox.send(RecipeEvent.Deleted)
        }

    /** Has Mealie's AI rework the recipe, into the app's language where [translate] is set, which is remembered for next time. */
    fun rework(translate: Boolean) {
        val recipe = state.value.recipe ?: return
        reworks.start(
            ReworkRequest(recipe.id, recipe.slug, recipe.name, translateTo = Locale.getDefault().toLanguageTag().takeIf { translate }),
        )
        viewModelScope.launch { settings.setTranslateImports(translate) }
    }

    /** The page shows how the rework ended: a reworked recipe is loaded again, under its new slug if it has one. */
    fun onReworkShown(outcome: ReworkOutcome) {
        val recipe = state.value.recipe ?: return
        reworks.shown(recipe.id)
        when (outcome) {
            is ReworkOutcome.Reworked -> {
                slug = outcome.slug
                load()
                outbox.trySend(RecipeEvent.Reworked(outcome))
            }

            is ReworkOutcome.Failed -> {
                outbox.trySend(RecipeEvent.ReworkFailed(outcome.error))
            }
        }
    }

    /** Puts back what the rework replaced, in one change. */
    fun undoRework(reworked: ReworkOutcome.Reworked) =
        act {
            slug = recipes.update(reworked.slug, reworked.undo)
            load()
        }

    private fun act(block: suspend (Recipe) -> Unit) {
        val recipe = state.value.recipe ?: return
        viewModelScope.launch {
            try {
                block(recipe)
            } catch (error: AromaError) {
                outbox.send(RecipeEvent.Failed(error))
            }
        }
    }

    private companion object {
        const val CONFIRM_MILLIS = 1500L
        const val SLUG = "recipeSlug"
    }
}

/** The user's own [rating] of the recipe; a rating taken back is null, so only a recipe Mealie sent nothing for keeps what it showed. */
private fun RecipeUiState.withRating(rating: UserRating?): RecipeUiState =
    copy(
        isFavorite = rating?.isFavorite ?: isFavorite,
        userRating = if (rating != null) rating.rating else userRating,
    )
