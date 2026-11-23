package app.aroma.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.ScreenMargin
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** Where a row that a swipe removes stands: at rest, or swiped out towards the start. */
private enum class RowSwipe { Rest, Out }

/**
 * A row that a swipe towards the start removes. A faint red field with the bin opens behind it, and
 * the bin brightens, with a tick, once letting go would remove the row. Let go, the row carries on
 * at the finger's speed, and the list closes up as soon as it has all but left. The row needs a
 * background of its own, so that the field only shows where the row has moved away.
 */
@Composable
fun SwipeToRemove(onRemove: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Never restored, so a row that comes back, through undo or a refresh, starts at rest instead of
    // swiped away, and cannot remove itself a second time.
    val state = remember { AnchoredDraggableState(RowSwipe.Rest) }
    val haptics = LocalHapticFeedback.current
    val armed = state.targetValue == RowSwipe.Out
    LaunchedEffect(armed) {
        if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    val remove by rememberUpdatedState(onRemove)
    LaunchedEffect(state) {
        snapshotFlow { state.isGone() }.first { it }
        remove()
    }
    val glyph by animateFloatAsState(if (armed) 1f else 0.45f, tween(Motion.STATE), label = "removeGlyph")
    val threshold = with(LocalDensity.current) { RemoveThreshold.toPx() }
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
                .padding(end = ScreenMargin),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(
                painterResource(R.drawable.ic_delete),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .size(Glyph.m)
                        .graphicsLayer { alpha = glyph },
            )
        }
        Box(
            Modifier
                .anchoredDraggable(
                    state = state,
                    reverseDirection = LocalLayoutDirection.current == LayoutDirection.Rtl,
                    orientation = Orientation.Horizontal,
                    flingBehavior = AnchoredDraggableDefaults.flingBehavior(state, { threshold }, Motion.LetGo),
                ).layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    state.updateAnchors(
                        DraggableAnchors {
                            RowSwipe.Rest at 0f
                            RowSwipe.Out at -placeable.width.toFloat()
                        },
                    )
                    layout(placeable.width, placeable.height) { placeable.placeRelative(state.requireOffset().roundToInt(), 0) }
                }
                // What is left of it in sight once it is gone is not drawn, so the list closes over the red field alone.
                .graphicsLayer { alpha = if (state.isGone()) 0f else 1f },
        ) { content() }
    }
}

/** How far a row must go before letting go removes it; a quick flick removes it sooner. */
private val RemoveThreshold = 56.dp

/**
 * Whether the row has all but left: no more than its own padding is still in sight. Its spring creeps
 * on to rest for a good while after that, and the list would wait on an empty row meanwhile. NaN, and
 * so never gone, until the row has been measured.
 */
private fun AnchoredDraggableState<RowSwipe>.isGone(): Boolean = offset <= anchors.positionOf(RowSwipe.Out) * GONE

private const val GONE = 0.95f
