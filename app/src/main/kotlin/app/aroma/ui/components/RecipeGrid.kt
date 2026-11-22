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
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
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
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        header()
        items(recipes, key = { it.id }) { recipe ->
            RecipeCard(recipe = recipe, onClick = { onOpen(recipe) }, imageAspectRatio = 1f)
        }
        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "more") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary)
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
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = MaterialTheme.shapes.large,
        )
    }
}
