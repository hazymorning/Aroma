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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.Organizer
import app.aroma.core.model.OrganizerKind
import app.aroma.core.model.RecipeSort
import app.aroma.core.model.RecipeSummary
import app.aroma.data.AromaError
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.AromaFilterChip
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.HeaderAction
import app.aroma.ui.components.Kicker
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.MessageState
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PageEndSpace
import app.aroma.ui.components.PageHeader
import app.aroma.ui.components.PillTextField
import app.aroma.ui.components.RecipeGrid
import app.aroma.ui.components.ScrollToTopOnReselect
import app.aroma.ui.components.StatusBarScrim
import app.aroma.ui.components.fullBleed
import app.aroma.ui.components.recipeGridPlaceholders
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.ScreenMargin
import kotlinx.coroutines.launch

data class RecipeFilter(
    val query: String = "",
    val favoritesOnly: Boolean = false,
    val categories: Set<Organizer> = emptySet(),
    val tags: Set<Organizer> = emptySet(),
    val tools: Set<Organizer> = emptySet(),
    val sort: RecipeSort = RecipeSort.Newest,
) {
    val hasFilters: Boolean get() = favoritesOnly || categories.isNotEmpty() || tags.isNotEmpty() || tools.isNotEmpty()

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
    val canLoadMore: Boolean = false,
    val isOffline: Boolean = false,
    val error: AromaError? = null,
    val categories: List<Organizer> = emptyList(),
    val tags: List<Organizer> = emptyList(),
    val tools: List<Organizer> = emptyList(),
) {
    fun options(kind: OrganizerKind): List<Organizer> =
        when (kind) {
            OrganizerKind.Category -> categories
            OrganizerKind.Tag -> tags
            OrganizerKind.Tool -> tools
        }
}

data class RecipesActions(
    val onQueryChange: (String) -> Unit = {},
    val onToggleFavorites: () -> Unit = {},
    val onToggleOrganizer: (OrganizerKind, Organizer) -> Unit = { _, _ -> },
    val onSortChange: (RecipeSort) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onOpenRecipe: (RecipeSummary) -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onAddRecipe: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

private sealed interface RecipesSheet {
    data class Organizers(val kind: OrganizerKind) : RecipesSheet

    data object Sort : RecipesSheet
}

@Composable
fun RecipesScreen(state: RecipesUiState, actions: RecipesActions, modifier: Modifier = Modifier, searchFocus: FocusRequester? = null) {
    var sheet by remember { mutableStateOf<RecipesSheet?>(null) }
    val grid = rememberLazyGridState()
    ScrollToTopOnReselect(grid)
    Box(modifier.fillMaxSize()) {
        RecipeGrid(
            recipes = state.recipes,
            onOpen = actions.onOpenRecipe,
            state = grid,
            isLoadingMore = state.isLoadingMore,
            canLoadMore = state.canLoadMore,
            onLoadMore = actions.onLoadMore,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(start = ScreenMargin, end = ScreenMargin, bottom = LocalBottomBarInset.current + PageEndSpace),
            header = {
                pageItem("header") { Header(state, actions, searchFocus) { sheet = it } }
                body(state, actions)
            },
        )
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
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

        state.recipes.isEmpty() -> {
            pageItem("empty") {
                MessageState(
                    icon = R.drawable.ic_recipes,
                    title = stringResource(R.string.recipes_empty_title),
                    body = stringResource(R.string.recipes_empty_message),
                    actionLabel = stringResource(R.string.recipes_add),
                    onAction = actions.onAddRecipe,
                    primaryAction = true,
                )
            }
        }
    }
}

@Composable
private fun Header(state: RecipesUiState, actions: RecipesActions, searchFocus: FocusRequester?, openSheet: (RecipesSheet) -> Unit) {
    Column(Modifier.hangIntoRowGap()) {
        PageHeader(title = stringResource(R.string.nav_recipes)) {
            HeaderAction(R.drawable.ic_add, stringResource(R.string.recipes_add), actions.onAddRecipe)
        }
        Spacer(Modifier.height(24.dp))
        PillTextField(
            value = state.filter.query,
            onValueChange = actions.onQueryChange,
            placeholder = stringResource(R.string.search_recipes_hint),
            focusRequester = searchFocus,
            modifier = Modifier.padding(horizontal = ScreenMargin),
        )
        Spacer(Modifier.height(16.dp))
        FilterChips(state, actions, openSheet)
        if (state.isOffline) OfflineNotice(Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = 16.dp))
        RecipeCount(state)
    }
}

@Composable
private fun FilterChips(state: RecipesUiState, actions: RecipesActions, openSheet: (RecipesSheet) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = ScreenMargin),
    ) {
        item(key = "favorites") {
            AromaFilterChip(
                selected = state.filter.favoritesOnly,
                label = stringResource(R.string.home_favorites),
                onClick = actions.onToggleFavorites,
            )
        }
        OrganizerKind.entries.filter { state.options(it).isNotEmpty() }.forEach { kind ->
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
        item(key = "sort") {
            AromaFilterChip(
                selected = false,
                label = stringResource(state.filter.sort.labelRes()),
                onClick = { openSheet(RecipesSheet.Sort) },
                trailingIcon = R.drawable.ic_sort,
            )
        }
    }
}

/**
 * The number of recipes as a kicker, 24 dp under the page head and 16 dp above the first photos.
 * While the first page loads it keeps its line, so the photos do not shift when the count arrives;
 * without recipes it gives way to the message.
 */
@Composable
internal fun RecipeCount(state: RecipesUiState) {
    val count = state.total?.takeIf { state.recipes.isNotEmpty() }
    if (count == null && !state.isLoading) return
    Kicker(
        text = count?.let { pluralStringResource(R.plurals.recipe_count, it, it) }.orEmpty(),
        modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = 24.dp, bottom = 16.dp),
    )
}

/**
 * A grid item across the whole width, reaching the screen edges, for content that keeps its own
 * 24 dp margins: the page head and messages.
 */
internal fun LazyGridScope.pageItem(key: String, content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }, key = key, contentType = key) {
        Box(Modifier.fullBleed(ScreenMargin)) { content() }
    }

/**
 * [RecipeGrid] leaves the same 32 dp after every row, the page head included. The head hangs its
 * last 32 dp into that gap, so it alone decides how close the first photos sit beneath it. The
 * overhang has slid under the status bar or the top bar by the time the grid lets go of the head.
 */
internal fun Modifier.hangIntoRowGap(): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, (placeable.height - GridRowGap.roundToPx()).coerceAtLeast(0)) { placeable.place(0, 0) }
    }

private val GridRowGap = 32.dp

private fun OrganizerKind.labelRes() =
    when (this) {
        OrganizerKind.Category -> R.string.recipes_categories
        OrganizerKind.Tag -> R.string.recipes_tags
        OrganizerKind.Tool -> R.string.recipes_tools
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
    AromaBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle(stringResource(kind.labelRes()))
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(options, key = { _, option -> option.id }) { index, option ->
                val checked = selected.any { it.id == option.id }
                if (index > 0) Hairline(Modifier.padding(horizontal = ScreenMargin))
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
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var choice by rememberSaveable { mutableStateOf(current) }
    AromaBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetTitle(stringResource(R.string.sort_title))
        Column(
            Modifier
                .selectableGroup()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            RecipeSort.entries.forEachIndexed { index, sort ->
                if (index > 0) Hairline(Modifier.padding(horizontal = ScreenMargin))
                ChoiceRow(
                    label = stringResource(sort.labelRes()),
                    chosen = sort == choice,
                    modifier =
                        Modifier.selectable(selected = sort == choice, role = Role.RadioButton) {
                            choice = sort
                            onSelect(sort)
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                        },
                )
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier =
            Modifier
                .padding(start = ScreenMargin, end = ScreenMargin, top = 8.dp, bottom = 16.dp)
                .semantics { heading() },
    )
}

/** A 56 dp option on the sheet's margins; a sage check fades in at its end once it is chosen. */
@Composable
private fun ChoiceRow(label: String, chosen: Boolean, modifier: Modifier = Modifier) {
    val check by animateFloatAsState(if (chosen) 1f else 0f, tween(Motion.STATE), label = "check")
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = ScreenMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        Icon(
            painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .size(20.dp)
                    .graphicsLayer { alpha = check },
        )
    }
}
