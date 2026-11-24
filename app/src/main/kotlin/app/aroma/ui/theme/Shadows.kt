package app.aroma.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Whether the current scheme is dark, judged by the page colour so dynamic colour counts too. */
val isDarkScheme: Boolean
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/*
 * The page is flat. Only what floats above it casts a shadow, and all of it the same way: long,
 * faint and tinted with the green-black ink instead of grey. Draw shadows before background and clip.
 */

/** The cooking button and the snackbar: a wide soft lift plus a tight grounding edge. */
@Composable
@ReadOnlyComposable
fun Modifier.floatingShadow(shape: Shape): Modifier {
    val ink = extras.shadowInk
    return if (isDarkScheme) {
        dropShadow(shape, Shadow(radius = 28.dp, spread = (-4).dp, color = ink.copy(alpha = 0.55f), offset = DpOffset(0.dp, 10.dp)))
            .dropShadow(shape, Shadow(radius = 3.dp, color = ink.copy(alpha = 0.35f), offset = DpOffset(0.dp, 1.dp)))
    } else {
        dropShadow(shape, Shadow(radius = 32.dp, spread = (-6).dp, color = ink.copy(alpha = 0.16f), offset = DpOffset(0.dp, 14.dp)))
            .dropShadow(shape, Shadow(radius = 3.dp, color = ink.copy(alpha = 0.06f), offset = DpOffset(0.dp, 1.dp)))
    }
}

/**
 * The tab bar, which rests on the bottom edge across the whole width: its shadow falls up onto the
 * page, wide and soft, with a tight edge where the two meet.
 */
@Composable
@ReadOnlyComposable
fun Modifier.barShadow(): Modifier {
    val ink = extras.shadowInk
    return if (isDarkScheme) {
        dropShadow(RectangleShape, Shadow(radius = 24.dp, color = ink.copy(alpha = 0.5f), offset = DpOffset(0.dp, (-6).dp)))
    } else {
        dropShadow(RectangleShape, Shadow(radius = 24.dp, color = ink.copy(alpha = 0.1f), offset = DpOffset(0.dp, (-6).dp)))
            .dropShadow(RectangleShape, Shadow(radius = 3.dp, color = ink.copy(alpha = 0.05f), offset = DpOffset(0.dp, (-1).dp)))
    }
}

/** Round buttons resting on a photo, so they read on light and dark pictures alike. */
fun Modifier.photoButtonShadow(shape: Shape): Modifier =
    dropShadow(shape, Shadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.14f), offset = DpOffset(0.dp, 2.dp)))
