package app.aroma.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.layeredShadow
import kotlinx.coroutines.delay

/**
 * The app's snackbars: paper words on ink, the action in soft sage, floating on their shadow; a
 * swipe to either side puts them away. One rises the last few dp into place as it fades in and sinks
 * a little as it fades out; one that takes another's place fades in over it. Nothing grows.
 */
@Composable
fun AromaSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val current = state.currentSnackbarData
    val accessibility = LocalAccessibilityManager.current
    // Each message stays as long as it asks, or longer where TalkBack needs the time.
    LaunchedEffect(current) {
        if (current == null) return@LaunchedEffect
        delay(current.visuals.duration.millis(current.visuals.actionLabel != null, accessibility))
        current.dismiss()
    }
    val drift = with(LocalDensity.current) { if (reducedMotion()) 0 else Motion.Drift.roundToPx() }
    AnimatedContent(
        targetState = current,
        transitionSpec = {
            val arrive = fadeIn(Motion.edge()) + slideInVertically(Motion.state()) { drift }
            val leave = fadeOut(Motion.edge()) + slideOutVertically(Motion.edge()) { drift / 2 }
            // The room changes at once: each message keeps to the bottom while it comes or goes.
            arrive togetherWith leave using SizeTransform(clip = false) { _, _ -> snap() }
        },
        contentAlignment = Alignment.BottomCenter,
        // Each message keeps room around it for its shadow, which is not cut off while it fades.
        modifier =
            modifier
                .offset(y = ShadowFall)
                .padding(horizontal = PageMargin - ShadowRoom),
        label = "snackbar",
    ) { data ->
        if (data == null) return@AnimatedContent
        Box(Modifier.padding(start = ShadowRoom, end = ShadowRoom, top = ShadowRoom, bottom = ShadowFall)) { Message(data) }
    }
}

/** How far a message's shadow reaches beyond its sides and its top, and how far it falls below it. */
private val ShadowRoom = Space.s24
private val ShadowFall = Space.s48

@Composable
private fun Message(data: SnackbarData) {
    val colors = AromaTheme.colors
    val shape = AromaTheme.shapes.box
    SwipeToDismissBox(
        state = rememberSwipeToDismissBoxState(),
        backgroundContent = {},
        onDismiss = { data.dismiss() },
        modifier =
            Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                dismiss {
                    data.dismiss()
                    true
                }
            },
    ) {
        Snackbar(
            snackbarData = data,
            shape = shape,
            containerColor = colors.ink,
            contentColor = colors.onInk,
            actionColor = colors.accentSoft,
            modifier = Modifier.layeredShadow(AromaTheme.shadows.menu, shape),
        )
    }
}

/** How long a message stays: as Material has it, and as long as TalkBack's user asked for where that is longer. */
private fun SnackbarDuration.millis(hasAction: Boolean, accessibility: AccessibilityManager?): Long {
    val original =
        when (this) {
            SnackbarDuration.Short -> SHORT
            SnackbarDuration.Long -> LONG
            SnackbarDuration.Indefinite -> Long.MAX_VALUE
        }
    return accessibility?.calculateRecommendedTimeoutMillis(
        original,
        containsIcons = true,
        containsText = true,
        containsControls = hasAction,
    ) ?: original
}

private const val SHORT = 4000L
private const val LONG = 10000L
