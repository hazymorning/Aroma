package app.aroma.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import app.aroma.R
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.ScreenMargin

/**
 * A row that a swipe towards the start removes. A faint red field with the bin opens behind it, and
 * the bin brightens, with a tick, once letting go would remove the row. The row needs a background
 * of its own, so that the field only shows where the row has moved away.
 */
@Composable
fun SwipeToRemove(onRemove: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberRowSwipeState()
    val haptics = LocalHapticFeedback.current
    val armed = state.targetValue == SwipeToDismissBoxValue.EndToStart
    LaunchedEffect(armed) {
        if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    val glyph by animateFloatAsState(if (armed) 1f else 0.45f, tween(Motion.STATE), label = "removeGlyph")
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { onRemove() },
        modifier = modifier,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
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
        },
    ) { content() }
}

/**
 * Swipe state for list rows that a swipe removes. Unlike the saveable default it is never restored,
 * so a row that comes back, through undo or a refresh, starts at rest instead of swiped away, and
 * cannot remove itself a second time.
 */
@Composable
private fun rememberRowSwipeState(): SwipeToDismissBoxState {
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    return remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
}
