package app.aroma.feature.recipes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import app.aroma.ui.components.CarouselCardWidth
import app.aroma.ui.components.CookbookCard
import app.aroma.ui.components.EmptyState
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.GroupHead
import app.aroma.ui.components.HeaderState
import app.aroma.ui.components.IconButton
import app.aroma.ui.components.LargeHeader
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PillField
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.RecipeGrid
import app.aroma.ui.components.SEPARATOR
import app.aroma.ui.components.ScrollToTopOnReselect
import app.aroma.ui.components.SectionTitle
import app.aroma.ui.components.Segment
import app.aroma.ui.components.SegmentGap
import app.aroma.ui.components.SheetFooter
import app.aroma.ui.components.Subtitle
import app.aroma.ui.components.TextAction
import app.aroma.ui.components.TopBar
import app.aroma.ui.components.animateOpenRow
import app.aroma.ui.components.hideThen
import app.aroma.ui.components.mealLabel
import app.aroma.ui.components.recipeGridSkeletons
import app.aroma.ui.components.rememberFullSheetState
import app.aroma.ui.components.rememberHeaderState
import app.aroma.ui.components.rememberTitleLines
import app.aroma.ui.components.requestWhenAttached
import app.aroma.ui.components.scrolledBeneath
import app.aroma.ui.theme.ContentMaxWidth
import app.aroma.ui.theme.EndSpace
import app.aroma.ui.theme.GridColumnGap
import app.aroma.ui.theme.HeaderGap
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.SectionGap
import app.aroma.ui.theme.SectionTitleGap
import app.aroma.ui.theme.Space
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
    val sort: RecipeSort = RecipeSort.Newest,
) {
    val hasFilters: Boolean
        get() = favoritesOnly || quick || longAgo || meal != null || categories.isNotEmpty() || tags.isNotEmpty()

    /** Whether anything narrows or orders the library other than the search: what resetting the filters takes back. */
    val hasFiltersOrSort: Boolean
        get() = hasFilters || sort != RecipeSort.Newest

    fun selected(kind: OrganizerKind): Set<Organizer> =
        when (kind) {
            OrganizerKind.Category -> categories
            OrganizerKind.Tag -> tags
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

    /** Categories and tags to choose from. */
    fun options(kind: OrganizerKind): List<Organizer> =
        when (kind) {
            OrganizerKind.Category -> categories
            OrganizerKind.Tag -> tags
        }

    /**
     * Whether the household's cookbooks lead the library, as a shelf above all its recipes: while
     * nothing is searched or narrowed, so that what matches comes first once something is.
     */
    val showsCookbooks: Boolean
        get() = cookbooks.isNotEmpty() && filter.query.isBlank() && !filter.hasFilters && (error == null || recipes.isNotEmpty())

    /** The categories or tags to narrow by, without those named for a kind of dish, which has a choice of its own. */
    fun choices(kind: OrganizerKind): List<Organizer> = options(kind).filterNot { organizer -> Meal.entries.any { it.matches(organizer) } }
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
    val onResetFilters: () -> Unit = {},
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
 * The library, for finding something to cook: its count under the title, with what narrows it, and
 * a search. While nothing is searched or narrowed, the household's cookbooks lead it as a shelf of
 * covers, then all its recipes follow as cards. The filter, one level down in a sheet, holds the kind
 * of dish, quick dishes, saved ones, categories, tags and the order. Adding a recipe is in the head.
 */
@Composable
fun RecipesScreen(
    state: RecipesUiState,
    actions: RecipesActions,
    modifier: Modifier = Modifier,
    focusSearch: Boolean = false,
    onFocusSearchConsume: () -> Unit = {},
) {
    var filtering by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    ScrollToTopOnReselect(list)
    val searchFocus = remember { FocusRequester() }
    val consumeFocusSearch by rememberUpdatedState(onFocusSearchConsume)
    // Search on Home lands in the search field, with the keyboard up: the field is in the list's
    // first item, so the list goes back to its top, and the field is asked until it is laid out.
    LaunchedEffect(focusSearch) {
        if (!focusSearch) return@LaunchedEffect
        list.scrollToItem(0)
        searchFocus.requestWhenAttached()
        consumeFocusSearch()
    }
    val header = rememberHeaderState()
    val barInset = LocalBottomBarInset.current
    val headActions: @Composable () -> Unit = {
        IconButton(
            R.drawable.ic_sliders,
            stringResource(R.string.recipes_filter),
            { filtering = true },
            dot = state.filter.hasFiltersOrSort,
        )
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
                    if (state.showsCookbooks) {
                        item(key = "cookbooks") { CookbookShelf(state.cookbooks, actions.onOpenCookbook, Modifier.animateOpenRow(this)) }
                        item(key = "allRecipes") {
                            SectionTitle(
                                stringResource(R.string.recipes_all),
                                Modifier
                                    .animateOpenRow(this)
                                    .widthIn(max = ContentMaxWidth)
                                    .padding(top = SectionGap, bottom = SectionTitleGap),
                            )
                        }
                    }
                    body(state, actions)
                },
            )
        }
        TopBar(
            title = stringResource(R.string.nav_recipes),
            collapsed = header.collapsed,
            raised = list.canScrollBackward,
            scrolled = list::scrolledBeneath,
            actions = { headActions() },
        )
    }
    if (filtering) FilterSheet(state = state, actions = actions, onDismiss = { filtering = false })
}

private fun LazyListScope.body(state: RecipesUiState, actions: RecipesActions) {
    when {
        state.isLoading -> {
            recipeGridSkeletons()
        }

        state.error != null && state.recipes.isEmpty() -> {
            item(key = "error") { ErrorState(state.error, onRetry = actions.onRetry, modifier = Modifier.animateOpenRow(this)) }
        }

        state.recipes.isEmpty() && state.mealNames()?.isEmpty() == true -> {
            item(key = "mealUnfiled") {
                EmptyState(
                    text = stringResource(R.string.recipes_meal_empty_title),
                    actionLabel = stringResource(R.string.recipes_clear_filters),
                    onAction = actions.onClearFilters,
                    modifier = Modifier.animateOpenRow(this),
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
                    modifier = Modifier.animateOpenRow(this),
                )
            }
        }

        state.recipes.isEmpty() -> {
            item(key = "empty") {
                EmptyState(
                    text = stringResource(R.string.recipes_empty_title),
                    actionLabel = stringResource(R.string.recipes_add),
                    onAction = actions.onAddRecipe,
                    modifier = Modifier.animateOpenRow(this),
                )
            }
        }
    }
}

/**
 * The title with the library's count and what narrows it, "12 Rezepte · Abendessen, Schnell", and the
 * search 16 dp under it.
 */
@Composable
private fun Header(
    state: RecipesUiState,
    actions: RecipesActions,
    header: HeaderState,
    searchFocus: FocusRequester?,
    headActions: @Composable () -> Unit,
) {
    val count = state.total?.let { total -> pluralStringResource(R.plurals.recipe_count, total, total) }
    val narrowed = narrowedBy(state.filter).joinToString(", ").takeIf { it.isNotEmpty() }
    val subtitle = listOfNotNull(count, narrowed).joinToString(SEPARATOR).takeIf { it.isNotEmpty() }
    Column(Modifier.padding(bottom = HeaderGap)) {
        LargeHeader(
            title = stringResource(R.string.nav_recipes),
            state = header,
            subtitle = subtitle?.let { { Subtitle(it) } },
            actions = { headActions() },
        )
        PillField(
            value = state.filter.query,
            onValueChange = actions.onQueryChange,
            placeholder = stringResource(R.string.search_recipes_hint),
            focusRequester = searchFocus,
            modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s16),
        )
        if (state.isOffline) OfflineNotice(Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s12))
    }
}

/** What narrows the library, in the words the filter uses for it: the kind of dish first, then the rest as chosen. */
@Composable
private fun narrowedBy(filter: RecipeFilter): List<String> {
    val quick = stringResource(R.string.recipes_quick)
    val saved = stringResource(R.string.recipes_saved)
    val longAgo = stringResource(R.string.recipes_long_ago)
    val meal = filter.meal?.let { mealLabel(it) }
    return listOfNotNull(meal, quick.takeIf { filter.quick }, saved.takeIf { filter.favoritesOnly }, longAgo.takeIf { filter.longAgo }) +
        filter.categories.map { it.name } +
        filter.tags.map { it.name }
}

/**
 * The household's cookbooks as a shelf of covers that scrolls sideways, as Home's ideas do: each opens
 * its own page.
 */
@Composable
private fun CookbookShelf(cookbooks: List<CookbookCover>, onOpen: (Cookbook) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.widthIn(max = ContentMaxWidth)) {
        SectionTitle(stringResource(R.string.recipes_cookbooks), Modifier.padding(bottom = SectionTitleGap))
        // A cookbook's name is short as a rule: the covers keep no more lines than the longest takes.
        val lines = rememberTitleLines(cookbooks.map { it.cookbook.name }, CarouselCardWidth, least = 1)
        LazyRow(
            contentPadding = PaddingValues(horizontal = PageMargin),
            horizontalArrangement = Arrangement.spacedBy(GridColumnGap),
        ) {
            items(cookbooks, key = { it.cookbook.id }) { cover ->
                CookbookCard(
                    cover = cover,
                    onClick = { onOpen(cover.cookbook) },
                    titleLines = lines,
                    modifier = Modifier.width(CarouselCardWidth),
                )
            }
        }
    }
}

/**
 * What narrows the library and how it is ordered, one level down, in the groups and words of the
 * sheet that files a recipe: the kind of dish, quick dishes and saved ones, categories and tags, and
 * the order, every choice in sight. Each holds at once; the list behind the sheet follows, and the
 * button at the foot says how many recipes it holds now and shows them. While anything is chosen,
 * resetting stands at the end of the title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(state: RecipesUiState, actions: RecipesActions, onDismiss: () -> Unit) {
    val sheetState = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    val filter = state.filter
    val meals = state.meals
    AromaBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.recipes_filter),
        sheetState = sheetState,
        titleAction = {
            AnimatedVisibility(visible = filter.hasFiltersOrSort, enter = fadeIn(Motion.edge()), exit = fadeOut(Motion.edge())) {
                TextAction(stringResource(R.string.recipes_reset), onClick = actions.onResetFilters)
            }
        },
    ) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(top = Space.s12, bottom = Space.s8),
        ) {
            FilterGroup(stringResource(R.string.organizers_meal)) {
                Chips(Modifier.selectableGroup()) {
                    Segment(stringResource(R.string.recipes_all_kinds), filter.meal == null, { filter.meal?.let(actions.onToggleMeal) })
                    meals.forEach { meal ->
                        Segment(mealLabel(meal), filter.meal == meal, {
                            if (filter.meal !=
                                meal
                            ) {
                                actions.onToggleMeal(meal)
                            }
                        })
                    }
                }
            }
            FilterGroup(stringResource(R.string.recipes_narrow), Modifier.padding(top = Space.s24)) {
                Chips {
                    Segment(stringResource(R.string.recipes_quick), filter.quick, actions.onToggleQuick)
                    Segment(stringResource(R.string.recipes_saved), filter.favoritesOnly, actions.onToggleFavorites)
                    Segment(stringResource(R.string.recipes_long_ago), filter.longAgo, actions.onToggleLongAgo)
                }
            }
            listOf(OrganizerKind.Category, OrganizerKind.Tag).forEach { kind ->
                val options = state.choices(kind)
                if (options.isNotEmpty()) {
                    val chosen = filter.selected(kind)
                    FilterGroup(stringResource(kind.labelRes()), Modifier.padding(top = Space.s24)) {
                        Chips {
                            options.forEach { option ->
                                Segment(option.name, chosen.any { it.id == option.id }, { actions.onToggleOrganizer(kind, option) })
                            }
                        }
                    }
                }
            }
            FilterGroup(stringResource(R.string.sort_title), Modifier.padding(top = Space.s24)) {
                Chips(Modifier.selectableGroup()) {
                    RecipeSort.entries.forEach { sort ->
                        Segment(stringResource(sort.labelRes()), filter.sort == sort, {
                            if (filter.sort !=
                                sort
                            ) {
                                actions.onSortChange(sort)
                            }
                        })
                    }
                }
            }
        }
        SheetFooter {
            PrimaryButton(
                text =
                    state.total?.let { total -> pluralStringResource(R.plurals.recipes_show, total, total) }
                        ?: stringResource(R.string.recipes_show_all),
                onClick = { scope.hideThen(sheetState, onDismiss) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A group of the filter: its head on the margin over its choices, which start on the margin too. */
@Composable
private fun FilterGroup(label: String, modifier: Modifier = Modifier, choices: @Composable () -> Unit) {
    Column(modifier) {
        GroupHead(label, Modifier.padding(horizontal = PageMargin))
        choices()
    }
}

/**
 * A group's choices as words on the sage wash once chosen, wrapping onto as many lines as they need,
 * so that every one is in sight. Where one alone may be chosen, [modifier] makes them one group.
 */
@Composable
private fun Chips(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier.padding(horizontal = PageMargin), horizontalArrangement = Arrangement.spacedBy(SegmentGap)) { content() }
}

private fun OrganizerKind.labelRes() =
    when (this) {
        OrganizerKind.Category -> R.string.recipes_categories
        OrganizerKind.Tag -> R.string.recipes_tags
    }

private fun RecipeSort.labelRes() =
    when (this) {
        RecipeSort.Newest -> R.string.sort_newest
        RecipeSort.Name -> R.string.sort_name
        RecipeSort.Rating -> R.string.sort_rating
        RecipeSort.LastMade -> R.string.sort_last_made
        RecipeSort.Random -> R.string.sort_random
    }
