package app.aroma.feature.recipes

import androidx.annotation.Keep
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.RecipeSummary
import app.aroma.ui.components.DetailTopBar
import app.aroma.ui.components.DetailTopBarHeight
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.Kicker
import app.aroma.ui.components.LargeTitle
import app.aroma.ui.components.MessageState
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PageEndSpace
import app.aroma.ui.components.RecipeGrid
import app.aroma.ui.components.pageItem
import app.aroma.ui.components.recipeGridPlaceholders
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.Standfirst

/** What a collection page holds; [app.aroma.ui.navigation.CollectionRoute] names one of these. */
@Keep
enum class CollectionKind { Cookbook, Favorites, Category, Tag, Tool }

data class CollectionActions(
    val onBack: () -> Unit = {},
    val onOpenRecipe: (RecipeSummary) -> Unit = {},
    val onRecipeMenu: (RecipeSummary) -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * Recipes that belong together, on a page of their own: a cookbook, the favourites, or one category,
 * tag or tool. The name is the large title, under what kind of collection it is.
 */
@Composable
fun CollectionScreen(
    kind: CollectionKind,
    name: String,
    description: String,
    state: RecipesUiState,
    actions: CollectionActions,
    modifier: Modifier = Modifier,
) {
    val grid = rememberLazyGridState()
    var titleEnd by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val collapsed by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > titleEnd } }
    Box(modifier.fillMaxSize()) {
        RecipeGrid(
            recipes = state.recipes,
            onOpen = actions.onOpenRecipe,
            onLongPress = actions.onRecipeMenu,
            state = grid,
            isLoadingMore = state.isLoadingMore,
            canLoadMore = state.canLoadMore,
            onLoadMore = actions.onLoadMore,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = ScreenMargin,
                    end = ScreenMargin,
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + DetailTopBarHeight,
                    bottom = PageEndSpace + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            header = {
                pageItem("header") { Header(kind, name, description, state, onTitleLaidOut = { titleEnd = it }) }
                when {
                    state.isLoading -> {
                        recipeGridPlaceholders()
                    }

                    state.error != null && state.recipes.isEmpty() -> {
                        pageItem("error") { ErrorState(state.error, onRetry = actions.onRetry) }
                    }

                    state.recipes.isEmpty() -> {
                        pageItem("empty") {
                            MessageState(
                                icon = R.drawable.ic_recipes,
                                title =
                                    stringResource(
                                        if (kind ==
                                            CollectionKind.Cookbook
                                        ) {
                                            R.string.cookbook_empty_title
                                        } else {
                                            R.string.recipes_no_results_title
                                        },
                                    ),
                                body =
                                    stringResource(
                                        if (kind ==
                                            CollectionKind.Cookbook
                                        ) {
                                            R.string.cookbook_empty_message
                                        } else {
                                            R.string.recipes_no_results_message
                                        },
                                    ),
                            )
                        }
                    }
                }
            },
        )
        DetailTopBar(onBack = actions.onBack, title = name, collapsed = collapsed)
    }
}

/** The kind of collection as a kicker, its name as the large title, the description as a serif lead, then the count. */
@Composable
private fun Header(
    kind: CollectionKind,
    name: String,
    description: String,
    state: RecipesUiState,
    onTitleLaidOut: (Int) -> Unit,
) {
    Column {
        Column(Modifier.onSizeChanged { onTitleLaidOut(it.height) }) {
            kind.labelRes()?.let { Kicker(stringResource(it), Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.xs)) }
            LargeTitle(name, bottomSpacing = 0.dp)
        }
        if (description.isNotBlank()) {
            Text(
                text = description,
                style = Standfirst,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.xs),
            )
        }
        if (state.isOffline) OfflineNotice(Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m))
        // Keeps its line while the first page loads, so the photos do not shift when the count arrives.
        val count = state.total?.takeIf { state.recipes.isNotEmpty() }
        if (count != null || state.isLoading) {
            Kicker(
                text = count?.let { pluralStringResource(R.plurals.recipe_count, it, it) }.orEmpty(),
                modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.l, bottom = Space.m),
            )
        }
    }
}

private fun CollectionKind.labelRes(): Int? =
    when (this) {
        CollectionKind.Cookbook -> R.string.collection_cookbook
        CollectionKind.Favorites -> null
        CollectionKind.Category -> R.string.collection_category
        CollectionKind.Tag -> R.string.collection_tag
        CollectionKind.Tool -> R.string.collection_tool
    }
