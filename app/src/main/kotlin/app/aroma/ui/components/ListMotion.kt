package app.aroma.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.aroma.ui.theme.Motion

/**
 * How the rows of a lazy list come, go and make room; the rows need stable keys. A new row fades in
 * where it lands while the rows after it glide aside. A row that leaves holds still while the rows
 * after it glide over it, and fades only once it is nearly covered, so a card made of several rows
 * closes without a gap.
 */
fun Modifier.animateRow(scope: LazyItemScope): Modifier =
    with(scope) {
        animateItem(
            fadeInSpec = tween(Motion.STATE, easing = Motion.Decelerate),
            placementSpec = tween(Motion.STATE, easing = Motion.Decelerate),
            fadeOutSpec = tween(Motion.EDGE, delayMillis = Motion.EDGE),
        )
    }

/**
 * [animateRow] for rows that stand open on the page, with no ground of their own to cover what they
 * glide over: one that leaves fades at once, and one that comes fades in once what stood in its
 * place has all but faded, so two never show through each other.
 */
fun Modifier.animateOpenRow(scope: LazyItemScope): Modifier =
    with(scope) {
        animateItem(
            fadeInSpec = Motion.after(),
            placementSpec = tween(Motion.STATE, easing = Motion.Decelerate),
            fadeOutSpec = Motion.edge(),
        )
    }

/**
 * Rows in a column, outside a lazy list, that come and go as the rows of a list do: a new row opens
 * its room as it fades in, its top edge first; a row that leaves fades where it stands, then gives
 * its room up. Rows keep their place by [key]; those there from the start are simply there.
 */
@Composable
fun <T> RowsInMotion(items: List<T>, key: (T) -> Any, modifier: Modifier = Modifier, row: @Composable (T) -> Unit) {
    val rows = remember { ShownRows<T>() }.update(items, key)
    Column(modifier) {
        rows.forEach { shown ->
            key(shown.key) {
                AnimatedVisibility(visibleState = shown.visible, enter = RoomOpens, exit = RoomCloses) { row(shown.item) }
            }
        }
    }
}

/**
 * How something outside a lazy list comes into a column: its room opens, its top edge first, as it
 * fades in. Where the column stands on a bar, it rises from the bar.
 */
val RoomOpens: EnterTransition = expandVertically(Motion.state(), expandFrom = Alignment.Top) + fadeIn(Motion.state())

/** How it leaves: it fades where it stands, then its room closes. */
val RoomCloses: ExitTransition =
    fadeOut(Motion.edge()) + shrinkVertically(tween(Motion.STATE, delayMillis = Motion.EDGE / 2, easing = Motion.Decelerate), Alignment.Top)

/** A row of [RowsInMotion]: its key, what it shows, and whether it is there or on its way in or out. */
private class ShownRow<T>(val key: Any, item: T, val visible: MutableTransitionState<Boolean>) {
    var item by mutableStateOf(item)
}

/** The rows of [RowsInMotion] in their order, those on their way out kept where they stood. */
private class ShownRows<T> {
    private var rows: List<ShownRow<T>> = emptyList()
    private var last: List<T>? = null

    fun update(items: List<T>, key: (T) -> Any): List<ShownRow<T>> {
        if (items === last) return rows
        val first = last == null
        last = items
        val known = rows.associateBy { it.key }
        val next =
            items
                .map { item ->
                    val id = key(item)
                    val row = known[id] ?: ShownRow(id, item, MutableTransitionState(first))
                    row.item = item
                    row.visible.targetState = true
                    row
                }.toMutableList()
        // A row that left stays after the row it followed until it has faded and closed.
        var after: ShownRow<T>? = null
        for (row in rows) {
            if (next.any { it === row }) {
                after = row
                continue
            }
            if (row.visible.isIdle && !row.visible.currentState) continue
            row.visible.targetState = false
            next.add(after?.let { previous -> next.indexOfFirst { it === previous } + 1 } ?: 0, row)
            after = row
        }
        rows = next
        return next
    }
}
