package app.aroma.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.core.model.RecipeSummary
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.ContentMaxWidth
import app.aroma.ui.theme.GridColumnGap
import app.aroma.ui.theme.GridRowGap
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.Space
import kotlin.math.max

/**
 * The recipe grid: two columns on a phone, more on wider screens, 16 dp apart and 28 dp between
 * rows. The cards of a row keep room for the longest name among them, so their meta lines stand on
 * one line. It asks for the next page shortly before the end is reached. [header] comes before the
 * grid in the same list. A long press on a card asks for its [onLongPress] actions; [saved] recipes
 * wear the ribbon.
 */
@Composable
fun RecipeGrid(
    recipes: List<RecipeSummary>,
    onOpen: (RecipeSummary) -> Unit,
    onLongPress: (RecipeSummary) -> Unit,
    state: LazyListState,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    saved: Set<String> = emptySet(),
    header: LazyListScope.() -> Unit,
) {
    val nearEnd by remember(state) {
        derivedStateOf {
            val last =
                state.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            last >= state.layoutInfo.totalItemsCount - NEAR_END
        }
    }
    val loadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(nearEnd, canLoadMore, recipes.size) {
        if (nearEnd && canLoadMore && !isLoadingMore) loadMore()
    }
    BoxWithConstraints(modifier) {
        val room = minOf(maxWidth, ContentMaxWidth) - PageMargin * 2
        val columns = gridColumns(room)
        val cardWidth = (room - GridColumnGap * (columns - 1)) / columns
        val rows = remember(recipes, columns) { recipes.chunked(columns) }
        LazyColumn(state = state, contentPadding = contentPadding, horizontalAlignment = Alignment.CenterHorizontally) {
            header()
            itemsIndexed(rows, key = { _, row -> row.first().id }) { index, row ->
                val lines = rememberTitleLines(row.map { it.name }, cardWidth)
                Row(
                    Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = PageMargin)
                        .padding(top = if (index == 0) 0.dp else GridRowGap)
                        .animateRow(this),
                    horizontalArrangement = Arrangement.spacedBy(GridColumnGap),
                ) {
                    row.forEach { recipe ->
                        RecipeCard(
                            recipe = recipe,
                            onClick = { onOpen(recipe) },
                            onLongClick = { onLongPress(recipe) },
                            saved = recipe.id in saved,
                            titleLines = lines,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (isLoadingMore) {
                item(key = "more") { SkeletonCards(columns, Modifier.padding(top = GridRowGap)) }
            }
        }
    }
}

/** How many columns of cards fit [room]: two on a phone, more where each keeps its least width. */
private fun gridColumns(room: Dp): Int = max(2, ((room + GridColumnGap) / (GridCardMinWidth + GridColumnGap)).toInt())

/** The least width of a card in the grid. */
private val GridCardMinWidth = 160.dp

/** How many rows before the end the next page is asked for. */
private const val NEAR_END = 3

/** A row of card-shaped skeletons, for a grid or a page of cards that is still loading. */
@Composable
fun SkeletonCards(columns: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = PageMargin),
        horizontalArrangement = Arrangement.spacedBy(GridColumnGap),
    ) {
        repeat(columns) {
            Column(Modifier.weight(1f)) {
                Skeleton(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(CARD_SKELETON),
                    shape = AromaTheme.shapes.cardImage,
                )
                SkeletonLine(
                    Modifier
                        .padding(top = Space.s12)
                        .fillMaxWidth(TITLE_SHARE),
                )
                SkeletonLine(
                    Modifier
                        .padding(top = Space.s8)
                        .fillMaxWidth(META_SHARE),
                )
                Spacer(Modifier.height(Space.s4))
            }
        }
    }
}

/** Grid-shaped skeletons that match [RecipeGrid], for a page that is still loading. */
fun LazyListScope.recipeGridSkeletons(rows: Int = 3) {
    items(rows) { index ->
        SkeletonCards(columns = 2, modifier = Modifier.padding(top = if (index == 0) 0.dp else GridRowGap))
    }
}

private const val CARD_SKELETON = 4f / 5f
private const val TITLE_SHARE = 0.8f
private const val META_SHARE = 0.4f
