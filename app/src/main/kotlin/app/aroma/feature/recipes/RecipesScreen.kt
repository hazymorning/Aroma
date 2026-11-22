package app.aroma.feature.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.Organizer
import app.aroma.core.model.OrganizerKind
import app.aroma.core.model.RecipeSort
import app.aroma.core.model.RecipeSummary
import app.aroma.data.AromaError
import app.aroma.ui.components.AromaFilterChip
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.MessageState
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PillTextField
import app.aroma.ui.components.RecipeGrid
import app.aroma.ui.components.fullBleed
import app.aroma.ui.components.recipeGridPlaceholders

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
)

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
    Box(modifier.fillMaxSize()) {
        RecipeGrid(
            recipes = state.recipes,
            onOpen = actions.onOpenRecipe,
            isLoadingMore = state.isLoadingMore,
            canLoadMore = state.canLoadMore,
            onLoadMore = actions.onLoadMore,
            modifier = Modifier.fillMaxSize(),
            // Room for the add button, which sits above the bar.
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = LocalBottomBarInset.current + 96.dp),
            header = {
                item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                    Header(state, actions, searchFocus) { sheet = it }
                }
                when {
                    state.isLoading -> {
                        recipeGridPlaceholders()
                    }

                    state.error != null && state.recipes.isEmpty() -> {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
                            ErrorState(state.error, onRetry = actions.onRetry)
                        }
                    }

                    state.recipes.isEmpty() && (state.filter.hasFilters || state.filter.query.isNotBlank()) -> {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "noResults") {
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
                        item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                            MessageState(
                                icon = R.drawable.ic_menu_book,
                                title = stringResource(R.string.recipes_empty_title),
                                body = stringResource(R.string.recipes_empty_message),
                                actionLabel = stringResource(R.string.recipes_add),
                                onAction = actions.onAddRecipe,
                            )
                        }
                    }
                }
            },
        )
        FloatingActionButton(
            onClick = actions.onAddRecipe,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = LocalBottomBarInset.current + 16.dp),
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.recipes_add))
        }
    }
    when (val current = sheet) {
        is RecipesSheet.Organizers -> {
            OrganizerSheet(
                kind = current.kind,
                options =
                    when (current.kind) {
                        OrganizerKind.Category -> state.categories
                        OrganizerKind.Tag -> state.tags
                        OrganizerKind.Tool -> state.tools
                    },
                selected = state.filter.selected(current.kind),
                onToggle = { actions.onToggleOrganizer(current.kind, it) },
                onDismiss = { sheet = null },
            )
        }

        RecipesSheet.Sort -> {
            SortSheet(state.filter.sort, onSelect = {
                actions.onSortChange(it)
                sheet = null
            }, onDismiss = { sheet = null })
        }

        null -> {}
    }
}

@Composable
private fun Header(state: RecipesUiState, actions: RecipesActions, searchFocus: FocusRequester?, openSheet: (RecipesSheet) -> Unit) {
    Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
        Text(
            text = stringResource(R.string.nav_recipes),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = 20.dp, bottom = 20.dp),
        )
        PillTextField(
            value = state.filter.query,
            onValueChange = actions.onQueryChange,
            placeholder = stringResource(R.string.search_recipes_hint),
            focusRequester = searchFocus,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            modifier = Modifier.fullBleed(20.dp),
        ) {
            item {
                AromaFilterChip(
                    selected = state.filter.favoritesOnly,
                    label = stringResource(R.string.home_favorites),
                    onClick = actions.onToggleFavorites,
                )
            }
            OrganizerKind.entries.forEach { kind ->
                val options =
                    when (kind) {
                        OrganizerKind.Category -> state.categories
                        OrganizerKind.Tag -> state.tags
                        OrganizerKind.Tool -> state.tools
                    }
                if (options.isNotEmpty()) {
                    item {
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
            item {
                AromaFilterChip(
                    selected = false,
                    label = stringResource(state.filter.sort.labelRes()),
                    onClick = { openSheet(RecipesSheet.Sort) },
                    trailingIcon = R.drawable.ic_sort,
                )
            }
        }
        if (state.isOffline) OfflineNotice(Modifier.padding(bottom = 8.dp))
        state.total?.takeIf { !state.isLoading }?.let { total ->
            Text(
                text = pluralStringResource(R.plurals.recipe_count, total, total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(kind.labelRes()),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.navigationBarsPadding()) {
            items(options, key = { it.id }) { option ->
                val checked = selected.any { it.id == option.id }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Checkbox) { onToggle(option) }
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                    Text(option.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(current: RecipeSort, onSelect: (RecipeSort) -> Unit, onDismiss: () -> Unit) {
    var choice by rememberSaveable { mutableStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.sort_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Column(Modifier.navigationBarsPadding()) {
            RecipeSort.entries.forEach { sort ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) {
                                choice = sort
                                onSelect(sort)
                            }.padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = choice == sort, onClick = null, modifier = Modifier.padding(12.dp))
                    Text(stringResource(sort.labelRes()), style = MaterialTheme.typography.bodyLarge)
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(12.dp)) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}
