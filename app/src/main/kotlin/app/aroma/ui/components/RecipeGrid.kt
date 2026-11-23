package app.aroma.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aroma.core.model.RecipeSummary
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space

/**
 * Two-column grid (more on wide screens) that asks for the next page shortly before the end is
 * reached. A long press on a card asks for its [onLongPress] actions.
 */
@Composable
fun RecipeGrid(
    recipes: List<RecipeSummary>,
    onOpen: (RecipeSummary) -> Unit,
    onLongPress: (RecipeSummary) -> Unit,
    state: LazyGridState,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    header: LazyGridScope.() -> Unit,
) {
    val nearEnd by remember(state) {
        derivedStateOf {
            val last =
                state.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            last >= state.layoutInfo.totalItemsCount - 6
        }
    }
    val loadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(nearEnd, canLoadMore, recipes.size) {
        if (nearEnd && canLoadMore && !isLoadingMore) loadMore()
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridColumnWidth),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        modifier = modifier,
    ) {
        header()
        items(recipes, key = { it.id }) { recipe ->
            // Each card carries the row gap below it, so header items above the grid set their own spacing.
            RecipeCard(
                recipe = recipe,
                onClick = { onOpen(recipe) },
                onLongClick = { onLongPress(recipe) },
                modifier = Modifier.padding(bottom = GridRowGap),
            )
        }
        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "more") {
                Box(Modifier.fillMaxWidth().padding(Space.l), contentAlignment = Alignment.Center) { InlineProgress() }
            }
        }
    }
}

/** Grid-shaped placeholders that match [RecipeGrid] and the cookbook shelf. */
fun LazyGridScope.recipeGridPlaceholders(count: Int = 6) {
    items(count) {
        Placeholder(
            Modifier
                .padding(bottom = GridRowGap)
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = MaterialTheme.shapes.medium,
        )
    }
}

/**
 * A grid item across the whole width, reaching the screen edges, for content that keeps its own
 * margins: a page's head and its messages.
 */
fun LazyGridScope.pageItem(key: String, content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }, key = key, contentType = key) {
        Box(Modifier.fullBleed(ScreenMargin)) { content() }
    }

/** The least width of a column of cards; wider screens fit more columns. */
internal val GridColumnWidth = 160.dp

/** Space between rows of cards. */
internal val GridRowGap = Space.l
