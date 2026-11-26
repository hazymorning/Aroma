package app.aroma.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Rows of a lazy list that a finger takes hold of and moves to another place. The row held follows
 * the finger, the rows it passes glide aside as it goes, and let go, it settles into its new place.
 * [onMove] moves the row keyed `from` to where the row keyed `to` stands, and [canMove] keeps a row
 * among those it may go between, such as the items of one aisle. Held near the top or the foot of
 * the list, past [startEdge] or [endEdge] of the view, the list scrolls on by itself.
 */
@Stable
class ListReorder internal constructor(
    private val list: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (from: Any, to: Any) -> Unit,
    private val canMove: (from: Any, to: Any) -> Boolean,
) {
    /** The key of the row a finger holds, while one does. */
    var held: Any? by mutableStateOf(null)
        private set

    /** The row settling into its place after it was let go, while it does. */
    private var settling: Any? by mutableStateOf(null)

    /** Where the held row's top stands in the list's view, as the finger has moved it. */
    private var top by mutableFloatStateOf(0f)
    private val settle = Animatable(0f)

    internal var startEdge = 0f
    internal var endEdge = 0f

    /** Takes hold of row [key]; false where it is not in view. */
    fun lift(key: Any): Boolean {
        val row = row(key) ?: return false
        settling = null
        held = key
        top = row.offset.toFloat()
        return true
    }

    /** Moves the held row [by] so far along the list, past the rows it may go between. */
    fun drag(by: Float) {
        val key = held ?: return
        top += by
        moveIfPassed(key)
    }

    /**
     * Moves the held row past the rows whose middle it has passed: as its middle passes theirs, so
     * it never swaps back and forth over a taller row.
     */
    private fun moveIfPassed(key: Any) {
        val row = row(key) ?: return
        val laid = row.offset + row.size / 2f
        val dragged = top + row.size / 2f
        val passed =
            list.layoutInfo.visibleItemsInfo.filter { other ->
                val middle = other.offset + other.size / 2f
                other.key != key &&
                    (if (dragged > laid) middle > laid && middle <= dragged else middle < laid && middle >= dragged) &&
                    canMove(key, other.key)
            }
        val target = (if (dragged > laid) passed.maxByOrNull { it.offset } else passed.minByOrNull { it.offset }) ?: return
        onMove(key, target.key)
    }

    /** Lets the held row go: it settles into the place it was moved to. */
    fun drop() {
        val key = held ?: return
        val from = shiftOf(key)
        held = null
        settling = key
        scope.launch {
            settle.snapTo(from)
            settle.animateTo(0f, Motion.state())
            if (settling == key) settling = null
        }
    }

    /** How far row [key] is drawn from where the list lays it out; read while drawing. */
    fun shift(key: Any): Float =
        when (key) {
            held -> shiftOf(key)
            settling -> settle.value
            else -> 0f
        }

    /** Whether row [key] floats over the others: held, or settling into its place. */
    fun isLifted(key: Any): Boolean = key == held || key == settling

    private fun shiftOf(key: Any): Float = row(key)?.let { top - it.offset } ?: 0f

    private fun row(key: Any): LazyListItemInfo? = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    /** How fast the list scrolls on by itself while the held row is near an end of the view, in px a frame. */
    internal fun edgeSpeed(): Float {
        val key = held ?: return 0f
        val row = row(key) ?: return 0f
        val info = list.layoutInfo
        val start = info.viewportStartOffset + startEdge
        val end = info.viewportEndOffset - endEdge
        val over =
            when {
                top < start -> top - start
                top + row.size > end -> top + row.size - end
                else -> return 0f
            }
        return (over * EDGE_PACE).coerceIn(-MAX_SPEED, MAX_SPEED)
    }

    internal fun afterScroll() = held?.let(::moveIfPassed)

    private companion object {
        /** A share of how far past the edge the row is, each frame. */
        const val EDGE_PACE = 0.2f
        const val MAX_SPEED = 40f
    }
}

/**
 * A [ListReorder] for [list]; [startEdge] and [endEdge] are what covers the list at its top and its
 * foot, such as a bar, where a held row makes the list scroll on.
 */
@Composable
fun rememberListReorder(
    list: LazyListState,
    onMove: (from: Any, to: Any) -> Unit,
    canMove: (from: Any, to: Any) -> Boolean,
    startEdge: Dp = 0.dp,
    endEdge: Dp = 0.dp,
): ListReorder {
    val scope = rememberCoroutineScope()
    val move by rememberUpdatedState(onMove)
    val may by rememberUpdatedState(canMove)
    val reorder = remember(list) { ListReorder(list, scope, { from, to -> move(from, to) }, { from, to -> may(from, to) }) }
    with(LocalDensity.current) {
        reorder.startEdge = startEdge.toPx()
        reorder.endEdge = endEdge.toPx()
    }
    // Frame by frame only while a row is held at an edge; otherwise nothing waits on the next frame.
    LaunchedEffect(reorder) {
        snapshotFlow { reorder.edgeSpeed() != 0f }.collectLatest { atEdge ->
            while (atEdge) {
                val speed = reorder.edgeSpeed()
                if (speed == 0f) break
                list.scrollBy(speed)
                reorder.afterScroll()
                withFrameNanos {}
            }
        }
    }
    return reorder
}
