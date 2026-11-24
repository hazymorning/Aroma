package app.aroma.feature.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.Cookbook
import app.aroma.core.model.CookbookCover
import app.aroma.core.model.Meal
import app.aroma.core.model.Organizer
import app.aroma.core.model.OrganizerKind
import app.aroma.core.model.Organizers
import app.aroma.core.model.RecipeSort
import app.aroma.core.model.RecipeSummary
import app.aroma.data.AromaError
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.AromaPullToRefreshBox
import app.aroma.ui.components.ChoiceRow
import app.aroma.ui.components.EmptyState
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.GroupHead
import app.aroma.ui.components.HeaderState
import app.aroma.ui.components.IconButton
import app.aroma.ui.components.LargeHeader
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PillField
import app.aroma.ui.components.RecipeGrid
import app.aroma.ui.components.RecipeRow
import app.aroma.ui.components.ScrollToTopOnReselect
import app.aroma.ui.components.Segment
import app.aroma.ui.components.SegmentGap
import app.aroma.ui.components.SegmentInset
import app.aroma.ui.components.SelectionRow
import app.aroma.ui.components.Subtitle
import app.aroma.ui.components.TextAction
import app.aroma.ui.components.TopBar
import app.aroma.ui.components.hideThen
import app.aroma.ui.components.mealLabel
import app.aroma.ui.components.recipeGridSkeletons
import app.aroma.ui.components.rememberFullSheetState
import app.aroma.ui.components.rememberHeaderState
import app.aroma.ui.theme.EndSpace
import app.aroma.ui.theme.HeaderGap
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget
import java.time.LocalTime

data class RecipeFilter(
    val query: String = "",
    val meal: Meal? = null,
    val quick: Boolean = false,
    val favoritesOnly: Boolean = false,
    /** Only recipes cooked before, but not in the last four weeks. */
    val longAgo: Boolean = false,
    val categories: Set<Organizer> = emptySet(),
    val tags: Set<Organizer> = emptySet(),
    val tools: Set<Organizer> = emptySet(),
    val sort: RecipeSort = RecipeSort.Newest,
) {
    val hasFilters: Boolean
        get() = favoritesOnly || quick || longAgo || meal != null || categories.isNotEmpty() || tags.isNotEmpty() || tools.isNotEmpty()

    fun selected(kind: OrganizerKind): Set<Organizer> =
        when (kind) {
            OrganizerKind.Category -> categories
            OrganizerKind.Tag -> tags
            OrganizerKind.Tool -> tools
        }
}

data class RecipesUiState(
    val filter: RecipeFilter = RecipeFilter(),
    val recipes: List<RecipeSummary> = emptyList(),
    val total: Int? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val canLoadMore: Boolean = false,
    val isOffline: Boolean = false,
    val error: AromaError? = null,
    val categories: List<Organizer> = emptyList(),
    val tags: List<Organizer> = emptyList(),
    val cookbooks: List<CookbookCover> = emptyList(),
    /** The recipes the user saved, by id. */
    val saved: Set<String> = emptySet(),
) {
    /**
     * The meals to filter by: those the day runs through, offered even before the household files a
     * recipe under one, so the filter is there to be found (one chosen without recipes says how to
     * file them); any other kind of dish, such as dessert, once something is filed under it.
     */
    val meals: List<Meal>
        get() {
            val organizers = Organizers(categories, tags)
            return Meal.entries.filter { it in MealsOfTheDay || it.namesIn(organizers).isNotEmpty() }
        }

    /** The names of the categories and tags the chosen meal goes by in Mealie; null while no meal is chosen. */
    fun mealNames(): List<String>? = filter.meal?.namesIn(Organizers(categories, tags))

    /** Categories and tags to choose from; the tab offers no tools. */
    fun options(kind: OrganizerKind): List<Organizer> =
        when (kind) {
            OrganizerKind.Category -> categories
            OrganizerKind.Tag -> tags
            OrganizerKind.Tool -> emptyList()
        }

    /** Whether anything beyond the kind of dish narrows the library: what the filter's dot says. */
    val filtersOtherThanMeal: Boolean
        get() =
            filter.quick || filter.favoritesOnly || filter.categories.isNotEmpty() || filter.tags.isNotEmpty() ||
                filter.sort != RecipeSort.Newest
}

data class RecipesActions(
    val onQueryChange: (String) -> Unit = {},
    val onToggleMeal: (Meal) -> Unit = {},
    val onToggleQuick: () -> Unit = {},
    val onToggleFavorites: () -> Unit = {},
    val onToggleLongAgo: () -> Unit = {},
    val onToggleOrganizer: (OrganizerKind, Organizer) -> Unit = { _, _ -> },
    val onSortChange: (RecipeSort) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onOpenRecipe: (RecipeSummary) -> Unit = {},
    val onRecipeMenu: (RecipeSummary) -> Unit = {},
    val onOpenCookbook: (Cookbook) -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onAddRecipe: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** The meals the day runs through, one for each time of day (see [Meal.at]). */
private val MealsOfTheDay: Set<Meal> = (0..23).mapTo(mutableSetOf()) { Meal.at(LocalTime.of(it, 0)) }

/**
 * The library, for finding something to cook: its count under the title, a search, the kinds of
 * dish as a row of words, and every recipe below as cards. The filter sheet holds the rest: the
 * order, quick dishes, saved ones, categories, tags and the household's cookbooks. Adding a recipe
 * is in the head.
 */
@Composable
fun RecipesScreen(state: RecipesUiState, actions: RecipesActions, modifier: Modifier = Modifier, searchFocus: FocusRequester? = null) {
    var filtering by rememberSaveable { mutableStateOf(false) }
    var organizers by rememberSaveable { mutableStateOf<OrganizerKind?>(null) }
    val list = rememberLazyListState()
    ScrollToTopOnReselect(list)
    val header = rememberHeaderState()
    val barInset = LocalBottomBarInset.current
    val headActions: @Composable () -> Unit = {
        IconButton(R.drawable.ic_sliders, stringResource(R.string.recipes_filter), { filtering = true }, dot = state.filtersOtherThanMeal)
        IconButton(R.drawable.ic_add, stringResource(R.string.recipes_add), actions.onAddRecipe)
    }
    Box(modifier.fillMaxSize()) {
        AromaPullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh, modifier = Modifier.fillMaxSize()) {
            RecipeGrid(
                recipes = state.recipes,
                onOpen = actions.onOpenRecipe,
                onLongPress = actions.onRecipeMenu,
                state = list,
                isLoadingMore = state.isLoadingMore,
                canLoadMore = state.canLoadMore,
                onLoadMore = actions.onLoadMore,
                saved = state.saved,
                // What is searched for reads best as a list of names.
                asRows = state.filter.query.isNotBlank(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = barInset + EndSpace),
                header = {
                    item(key = "header") { Header(state, actions, header, searchFocus, headActions) }
                    body(state, actions)
                },
            )
        }
        TopBar(
            title = stringResource(R.string.nav_recipes),
            collapsed = header.collapsed,
            raised = list.canScrollBackward,
            actions = { headActions() },
        )
    }
    if (filtering) {
        FilterSheet(
            state = state,
            actions = actions,
            onOrganizers = { organizers = it },
            onDismiss = { filtering = false },
        )
    }
    organizers?.let { kind ->
        OrganizerSheet(
            kind = kind,
            options = state.options(kind),
            selected = state.filter.selected(kind),
            onToggle = { actions.onToggleOrganizer(kind, it) },
            onDismiss = { organizers = null },
        )
    }
}

private fun LazyListScope.body(state: RecipesUiState, actions: RecipesActions) {
    when {
        state.isLoading -> {
            recipeGridSkeletons()
        }

        state.error != null && state.recipes.isEmpty() -> {
            item(key = "error") { ErrorState(state.error, onRetry = actions.onRetry) }
        }

        state.recipes.isEmpty() && state.mealNames()?.isEmpty() == true -> {
            item(key = "mealUnfiled") {
                EmptyState(
                    text = stringResource(R.string.recipes_meal_empty_title),
                    actionLabel = stringResource(R.string.recipes_clear_filters),
                    onAction = actions.onClearFilters,
                )
            }
        }

        state.recipes.isEmpty() && (state.filter.hasFilters || state.filter.query.isNotBlank()) -> {
            item(key = "noResults") {
                EmptyState(
                    text = stringResource(R.string.recipes_no_results_title),
                    actionLabel = stringResource(R.string.recipes_clear_filters),
                    onAction = {
                        actions.onQueryChange("")
                        actions.onClearFilters()
                    },
                )
            }
        }

        state.recipes.isEmpty() -> {
            item(key = "empty") {
                EmptyState(
                    text = stringResource(R.string.recipes_empty_title),
                    actionLabel = stringResource(R.string.recipes_add),
                    onAction = actions.onAddRecipe,
                )
            }
        }
    }
}

/** The title with the library's count, the search 16 dp under it, and the kinds of dish 12 dp under that. */
@Composable
private fun Header(
    state: RecipesUiState,
    actions: RecipesActions,
    header: HeaderState,
    searchFocus: FocusRequester?,
    headActions: @Composable () -> Unit,
) {
    val meals = state.meals
    Column(Modifier.padding(bottom = HeaderGap)) {
        LargeHeader(
            title = stringResource(R.string.nav_recipes),
            state = header,
            subtitle = state.total?.let { total -> { Subtitle(pluralStringResource(R.plurals.recipe_count, total, total)) } },
            actions = { headActions() },
        )
        PillField(
            value = state.filter.query,
            onValueChange = actions.onQueryChange,
            placeholder = stringResource(R.string.search_recipes_hint),
            focusRequester = searchFocus,
            modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s16),
        )
        SelectionRow(
            options = listOf(stringResource(R.string.recipes_all_kinds)) + meals.map { mealLabel(it) },
            selectedIndex = state.filter.meal?.let { meals.indexOf(it) + 1 } ?: 0,
            onSelect = { index ->
                if (index == 0) state.filter.meal?.let(actions.onToggleMeal) else actions.onToggleMeal(meals[index - 1])
            },
            modifier = Modifier.padding(top = Space.s12 - (TouchTarget - SegmentPill) / 2),
        )
        if (state.isOffline) OfflineNotice(Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s12))
    }
}

/** The pill of a chosen kind of dish, to set the row 12 dp under the search by the pill rather than by its target. */
private val SegmentPill = 36.dp

/**
 * Everything that narrows or orders the library beyond the kind of dish, one level down: the order,
 * quick dishes and saved ones, categories and tags, and the household's cookbooks, which each open
 * their own page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(state: RecipesUiState, actions: RecipesActions, onOrganizers: (OrganizerKind) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    AromaBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.recipes_filter), sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.s24),
        ) {
            GroupHead(stringResource(R.string.sort_title), Modifier.padding(horizontal = PageMargin))
            SelectionRow(
                options = RecipeSort.entries.map { stringResource(it.labelRes()) },
                selectedIndex = RecipeSort.entries.indexOf(state.filter.sort),
                onSelect = { actions.onSortChange(RecipeSort.entries[it]) },
            )
            GroupHead(stringResource(R.string.recipes_narrow), Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s16))
            FlowRow(
                Modifier.padding(start = PageMargin - SegmentInset, end = PageMargin),
                horizontalArrangement = Arrangement.spacedBy(SegmentGap),
            ) {
                Segment(stringResource(R.string.recipes_quick), state.filter.quick, actions.onToggleQuick)
                Segment(stringResource(R.string.recipes_saved), state.filter.favoritesOnly, actions.onToggleFavorites)
                Segment(stringResource(R.string.recipes_long_ago), state.filter.longAgo, actions.onToggleLongAgo)
            }
            listOf(OrganizerKind.Category, OrganizerKind.Tag).filter { state.options(it).isNotEmpty() }.forEach { kind ->
                val chosen = state.filter.selected(kind)
                ChoiceRow(
                    label = stringResource(kind.labelRes()),
                    chosen = false,
                    description = chosen.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name },
                    modifier = Modifier.clickable(role = Role.Button) { scope.hideThen(sheetState) { onOrganizers(kind) } },
                )
            }
            if (state.cookbooks.isNotEmpty()) {
                GroupHead(
                    stringResource(R.string.recipes_cookbooks),
                    Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s16),
                )
                state.cookbooks.forEach { cover ->
                    RecipeRow(
                        title = cover.cookbook.name,
                        image = cover.photo,
                        onClick = {
                            scope.hideThen(sheetState) {
                                onDismiss()
                                actions.onOpenCookbook(cover.cookbook)
                            }
                        },
                        meta = listOf(pluralStringResource(R.plurals.recipe_count, cover.recipeCount, cover.recipeCount)),
                        fallbackIcon = R.drawable.ic_recipes,
                        modifier = Modifier.padding(horizontal = PageMargin, vertical = Space.s8),
                    )
                }
            }
            if (state.filter.hasFilters || state.filter.sort != RecipeSort.Newest) {
                TextAction(
                    stringResource(R.string.recipes_clear_filters),
                    onClick = {
                        actions.onClearFilters()
                        actions.onSortChange(RecipeSort.Newest)
                    },
                    modifier = Modifier.padding(start = PageMargin, top = Space.s16),
                )
            }
        }
    }
}

private fun OrganizerKind.labelRes() =
    when (this) {
        OrganizerKind.Category -> R.string.recipes_categories
        OrganizerKind.Tag -> R.string.recipes_tags
        OrganizerKind.Tool -> R.string.collection_tool
    }

private fun RecipeSort.labelRes() =
    when (this) {
        RecipeSort.Newest -> R.string.sort_newest
        RecipeSort.Name -> R.string.sort_name
        RecipeSort.Rating -> R.string.sort_rating
        RecipeSort.LastMade -> R.string.sort_last_made
        RecipeSort.Random -> R.string.sort_random
    }

/** The categories or tags to narrow by, any number of them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrganizerSheet(
    kind: OrganizerKind,
    options: List<Organizer>,
    selected: Set<Organizer>,
    onToggle: (Organizer) -> Unit,
    onDismiss: () -> Unit,
) {
    AromaBottomSheet(onDismissRequest = onDismiss, title = stringResource(kind.labelRes())) {
        LazyColumn(contentPadding = PaddingValues(bottom = Space.s16)) {
            items(options, key = { it.id }) { option ->
                val checked = selected.any { it.id == option.id }
                ChoiceRow(
                    label = option.name,
                    chosen = checked,
                    modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(option) }),
                )
            }
        }
    }
}
