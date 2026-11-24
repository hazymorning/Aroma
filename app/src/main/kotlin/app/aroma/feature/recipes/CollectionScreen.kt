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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.aroma.R
import app.aroma.core.model.RecipeSummary
import app.aroma.ui.components.AromaPullToRefreshBox
import app.aroma.ui.components.DetailTopBar
import app.aroma.ui.components.DetailTopBarHeight
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.LargeTitle
import app.aroma.ui.components.MessageState
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.RecipeGrid
import app.aroma.ui.components.Text
import app.aroma.ui.components.largeTitleAlpha
import app.aroma.ui.components.pageItem
import app.aroma.ui.components.recipeGridPlaceholders
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.PageEndSpace
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space

/** What a collection page holds; [app.aroma.ui.navigation.CollectionRoute] names one of these. */
@Keep
enum class CollectionKind { Cookbook, Favorites, Category, Tag, Tool, Meal, Quick }

data class CollectionActions(
    val onBack: () -> Unit = {},
    val onOpenRecipe: (RecipeSummary) -> Unit = {},
    val onRecipeMenu: (RecipeSummary) -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * Recipes that belong together, on a page of their own: a cookbook, the favourites, one meal, the
 * quick ones, or one category, tag or tool. The name is the large title, under what kind of
 * collection it is.
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
    val collapsed by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > titleEnd / 2 } }
    // The indicator comes down over the bar, so it shows while the page refreshes.
    AromaPullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh, modifier = modifier.fillMaxSize()) {
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
                pageItem("header") {
                    Header(
                        kind,
                        name,
                        description,
                        state,
                        onTitleLaidOut = { titleEnd = it },
                        titleAlpha = {
                            if (grid.firstVisibleItemIndex >
                                0
                            ) {
                                0f
                            } else {
                                largeTitleAlpha(grid.firstVisibleItemScrollOffset, titleEnd)
                            }
                        },
                    )
                }
                when {
                    state.isLoading -> {
                        recipeGridPlaceholders()
                    }

                    state.error != null && state.recipes.isEmpty() -> {
                        pageItem("error") { ErrorState(state.error, onRetry = actions.onRetry) }
                    }

                    state.recipes.isEmpty() -> {
                        pageItem("empty") {
                            val (title, body) = kind.emptyRes()
                            MessageState(icon = R.drawable.ic_recipes, title = stringResource(title), body = stringResource(body))
                        }
                    }
                }
            },
        )
        DetailTopBar(onBack = actions.onBack, title = name, collapsed = collapsed)
    }
}

/** The kind of collection over its name as the large title, the description, then the count. */
@Composable
private fun Header(
    kind: CollectionKind,
    name: String,
    description: String,
    state: RecipesUiState,
    onTitleLaidOut: (Int) -> Unit,
    titleAlpha: () -> Float,
) {
    Column {
        // The title fades as it passes under the bar, as on every page with a large title.
        Column(
            Modifier
                .onSizeChanged { onTitleLaidOut(it.height) }
                .graphicsLayer { alpha = titleAlpha() },
        ) {
            kind.labelRes()?.let {
                Text(
                    text = stringResource(it),
                    style = AromaType.Small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.xs),
                )
            }
            LargeTitle(name)
        }
        if (description.isNotBlank()) {
            Text(
                text = description,
                style = AromaType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.xs),
            )
        }
        if (state.isOffline) OfflineNotice(Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m))
        // Keeps its line while the first page loads, so the photos do not shift when the count arrives.
        val count = state.total?.takeIf { state.recipes.isNotEmpty() }
        if (count != null || state.isLoading) {
            Text(
                text = count?.let { pluralStringResource(R.plurals.recipe_count, it, it) }.orEmpty(),
                style = AromaType.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m, bottom = Space.m),
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
        CollectionKind.Meal, CollectionKind.Quick -> null
    }

/** What an empty collection says: the title and the line under it. */
private fun CollectionKind.emptyRes(): Pair<Int, Int> =
    when (this) {
        CollectionKind.Cookbook -> R.string.cookbook_empty_title to R.string.cookbook_empty_message
        CollectionKind.Meal -> R.string.recipes_meal_empty_title to R.string.recipes_meal_empty_message
        else -> R.string.recipes_no_results_title to R.string.recipes_no_results_message
    }
