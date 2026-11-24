package app.aroma.feature.recipes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
import app.aroma.ui.components.AromaFilterChip
import app.aroma.ui.components.AromaPullToRefreshBox
import app.aroma.ui.components.ChoiceRow
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.HeaderAction
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.MessageState
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PageHeader
import app.aroma.ui.components.PhotoCard
import app.aroma.ui.components.PillTextField
import app.aroma.ui.components.RecipeGrid
import app.aroma.ui.components.ScrollToTopOnReselect
import app.aroma.ui.components.SectionHeader
import app.aroma.ui.components.StatusBarCover
import app.aroma.ui.components.Text
import app.aroma.ui.components.hideThen
import app.aroma.ui.components.mealLabel
import app.aroma.ui.components.pageItem
import app.aroma.ui.components.recipeGridPlaceholders
import app.aroma.ui.components.rememberFullSheetState
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.ControlHeight
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.PageEndSpace
import app.aroma.ui.theme.RowHeight
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.tabular
import java.time.LocalTime

/** What the library is narrowed to. [tools] only comes from a tool's own page; the tab offers no such filter. */
data class RecipeFilter(
    val query: String = "",
    val meal: Meal? = null,
    val quick: Boolean = false,
    val favoritesOnly: Boolean = false,
    val categories: Set<Organizer> = emptySet(),
    val tags: Set<Organizer> = emptySet(),
    val tools: Set<Organizer> = emptySet(),
    val sort: RecipeSort = RecipeSort.Newest,
) {
    val hasFilters: Boolean
        get() = favoritesOnly || quick || meal != null || categories.isNotEmpty() || tags.isNotEmpty() || tools.isNotEmpty()

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

    /** Browsing the whole library, with nothing searched or filtered: the cookbooks lead it. */
    val showsCookbooks: Boolean
        get() = cookbooks.isNotEmpty() && !filter.hasFilters && filter.query.isBlank() && (error == null || recipes.isNotEmpty())
}

data class RecipesActions(
    val onQueryChange: (String) -> Unit = {},
    val onToggleMeal: (Meal) -> Unit = {},
    val onToggleQuick: () -> Unit = {},
    val onToggleFavorites: () -> Unit = {},
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

private sealed interface RecipesSheet {
    data class Organizers(val kind: OrganizerKind) : RecipesSheet

    data object Sort : RecipesSheet
}

/** The meals the day runs through, one for each time of day (see [Meal.at]). */
private val MealsOfTheDay: Set<Meal> = (0..23).mapTo(mutableSetOf()) { Meal.at(LocalTime.of(it, 0)) }

/** How wide a cookbook's cover is in the row that leads the library: as wide as a card on Home. */
private val CoverWidth = 160.dp

/**
 * The library, for finding something to cook: a search, the meals and a few everyday filters, the
 * household's cookbooks while nothing is filtered, and every recipe below. Adding a recipe is in
 * the page's head, beside the order.
 */
@Composable
fun RecipesScreen(state: RecipesUiState, actions: RecipesActions, modifier: Modifier = Modifier, searchFocus: FocusRequester? = null) {
    var sheet by remember { mutableStateOf<RecipesSheet?>(null) }
    val grid = rememberLazyGridState()
    // Kept out here, so the chips stay where they were scrolled while the head of the page is out of view.
    val chipRow = rememberLazyListState()
    ScrollToTopOnReselect(grid)
    val barInset = LocalBottomBarInset.current
    Box(modifier.fillMaxSize()) {
        AromaPullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh, modifier = Modifier.fillMaxSize()) {
            RecipeGrid(
                recipes = state.recipes,
                onOpen = actions.onOpenRecipe,
                onLongPress = actions.onRecipeMenu,
                state = grid,
                isLoadingMore = state.isLoadingMore,
                canLoadMore = state.canLoadMore,
                onLoadMore = actions.onLoadMore,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, bottom = barInset + PageEndSpace),
                header = {
                    pageItem("header") { Header(state, actions, chipRow, searchFocus) { sheet = it } }
                    if (state.showsCookbooks) cookbooks(state.cookbooks, actions.onOpenCookbook)
                    body(state, actions)
                },
            )
            StatusBarCover(Modifier.align(Alignment.TopCenter))
        }
    }
    when (val current = sheet) {
        is RecipesSheet.Organizers -> {
            OrganizerSheet(
                kind = current.kind,
                options = state.options(current.kind),
                selected = state.filter.selected(current.kind),
                onToggle = { actions.onToggleOrganizer(current.kind, it) },
                onDismiss = { sheet = null },
            )
        }

        RecipesSheet.Sort -> {
            SortSheet(state.filter.sort, onSelect = actions.onSortChange, onDismiss = { sheet = null })
        }

        null -> {}
    }
}

private fun LazyGridScope.body(state: RecipesUiState, actions: RecipesActions) {
    when {
        state.isLoading -> {
            recipeGridPlaceholders()
        }

        state.error != null && state.recipes.isEmpty() -> {
            pageItem("error") { ErrorState(state.error, onRetry = actions.onRetry) }
        }

        state.recipes.isEmpty() && state.mealNames()?.isEmpty() == true -> {
            pageItem("mealUnfiled") {
                MessageState(
                    icon = R.drawable.ic_recipes,
                    title = stringResource(R.string.recipes_meal_empty_title),
                    body = stringResource(R.string.recipes_meal_empty_message),
                    actionLabel = stringResource(R.string.recipes_clear_filters),
                    onAction = actions.onClearFilters,
                )
            }
        }

        state.recipes.isEmpty() && (state.filter.hasFilters || state.filter.query.isNotBlank()) -> {
            pageItem("noResults") {
                MessageState(
                    icon = R.drawable.ic_search,
                    title = stringResource(R.string.recipes_no_results_title),
                    body = stringResource(R.string.recipes_no_results_message),
                    actionLabel = if (state.filter.hasFilters) stringResource(R.string.recipes_clear_filters) else null,
                    onAction = actions.onClearFilters,
                )
            }
        }

        // The add button in the head is the way in, so the message needs no button of its own.
        state.recipes.isEmpty() -> {
            pageItem("empty") {
                MessageState(
                    icon = R.drawable.ic_recipes,
                    title = stringResource(R.string.recipes_empty_title),
                    body = stringResource(R.string.recipes_empty_message),
                )
            }
        }
    }
}

/** The tab's title with the order of the library, the search, and the filters under it. */
@Composable
private fun Header(
    state: RecipesUiState,
    actions: RecipesActions,
    chipRow: LazyListState,
    searchFocus: FocusRequester?,
    openSheet: (RecipesSheet) -> Unit,
) {
    Column {
        PageHeader(title = stringResource(R.string.nav_recipes)) {
            val sortLabel = stringResource(R.string.sort_title) + ": " + stringResource(state.filter.sort.labelRes())
            HeaderAction(R.drawable.ic_sort, sortLabel, { openSheet(RecipesSheet.Sort) })
            HeaderAction(R.drawable.ic_add, stringResource(R.string.recipes_add), actions.onAddRecipe)
        }
        PillTextField(
            value = state.filter.query,
            onValueChange = actions.onQueryChange,
            placeholder = stringResource(R.string.search_recipes_hint),
            focusRequester = searchFocus,
            modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.l),
        )
        FilterChips(state, actions, chipRow, openSheet, Modifier.padding(top = Space.m))
        if (state.isOffline) OfflineNotice(Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m))
        // The cookbooks' title brings its own space.
        if (!state.showsCookbooks) Spacer(Modifier.height(Space.l))
    }
}

/** The meals first, as the question is mostly what to cook for one; then quick dishes, favourites and the rest. */
@Composable
private fun FilterChips(
    state: RecipesUiState,
    actions: RecipesActions,
    rowState: LazyListState,
    openSheet: (RecipesSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        state = rowState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        contentPadding = PaddingValues(horizontal = ScreenMargin),
    ) {
        items(state.meals, key = { it.name }) { meal ->
            AromaFilterChip(
                selected = state.filter.meal == meal,
                label = mealLabel(meal),
                onClick = { actions.onToggleMeal(meal) },
            )
        }
        item(key = "quick") {
            AromaFilterChip(selected = state.filter.quick, label = stringResource(R.string.recipes_quick), onClick = actions.onToggleQuick)
        }
        item(key = "favorites") {
            AromaFilterChip(
                selected = state.filter.favoritesOnly,
                label = stringResource(R.string.home_favorites),
                onClick = actions.onToggleFavorites,
            )
        }
        listOf(OrganizerKind.Category, OrganizerKind.Tag).filter { state.options(it).isNotEmpty() }.forEach { kind ->
            item(key = kind.name) {
                val count = state.filter.selected(kind).size
                val label = stringResource(kind.labelRes())
                AromaFilterChip(
                    selected = count > 0,
                    label = if (count > 0) "$label · $count" else label,
                    onClick = { openSheet(RecipesSheet.Organizers(kind)) },
                    trailingIcon = R.drawable.ic_expand_more,
                )
            }
        }
    }
}

/** The household's cookbooks as a row of covers, then the title of the whole library below them. */
private fun LazyGridScope.cookbooks(covers: List<CookbookCover>, onOpen: (Cookbook) -> Unit) {
    pageItem("cookbooks") {
        Column {
            SectionHeader(stringResource(R.string.recipes_cookbooks))
            Spacer(Modifier.height(Space.xs))
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenMargin),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                items(covers, key = { it.cookbook.id }) { cover ->
                    PhotoCard(
                        image = cover.photo,
                        title = cover.cookbook.name,
                        onClick = { onOpen(cover.cookbook) },
                        fallbackIcon = R.drawable.ic_recipes,
                        // Wider than tall, unlike a recipe's photo, so the row stays low above the library.
                        imageAspectRatio = 4f / 3f,
                        modifier = Modifier.width(CoverWidth),
                    ) {
                        Text(
                            text = pluralStringResource(R.plurals.recipe_count, cover.recipeCount, cover.recipeCount),
                            style = AromaType.Small.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            SectionHeader(stringResource(R.string.recipes_all))
            Spacer(Modifier.height(Space.xs))
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
        LazyColumn(contentPadding = PaddingValues(bottom = Space.m)) {
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

/** Choosing an order applies it at once and lets the sheet slide away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(current: RecipeSort, onSelect: (RecipeSort) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    var choice by rememberSaveable { mutableStateOf(current) }
    AromaBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.sort_title), sheetState = sheetState) {
        Column(
            Modifier
                .selectableGroup()
                .padding(bottom = Space.m),
        ) {
            RecipeSort.entries.forEach { sort ->
                ChoiceRow(
                    label = stringResource(sort.labelRes()),
                    chosen = sort == choice,
                    modifier =
                        Modifier.selectable(selected = sort == choice, role = Role.RadioButton) {
                            choice = sort
                            onSelect(sort)
                            scope.hideThen(sheetState, onDismiss)
                        },
                )
            }
        }
    }
}
