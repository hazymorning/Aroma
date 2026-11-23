package app.aroma.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
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

/**
 * Two-column grid (more on wide screens) that asks for the next page shortly
 * before the end is reached.
 */
@Composable
fun RecipeGrid(
    recipes: List<RecipeSummary>,
    onOpen: (RecipeSummary) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    isLoadingMore: Boolean = false,
    canLoadMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(start = ScreenMargin, end = ScreenMargin, bottom = 32.dp),
    header: LazyGridScope.() -> Unit = {},
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
        columns = GridCells.Adaptive(minSize = 160.dp),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        header()
        items(recipes, key = { it.id }) { recipe ->
            // Each card carries the row gap below it, so header items above the grid set their own spacing.
            RecipeCard(
                recipe = recipe,
                onClick = { onOpen(recipe) },
                imageAspectRatio = 1f,
                modifier = Modifier.padding(bottom = GridRowGap),
            )
        }
        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "more") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** Grid-shaped placeholders that match [RecipeGrid]. */
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

/** Space between rows of recipe cards. */
private val GridRowGap = 32.dp
