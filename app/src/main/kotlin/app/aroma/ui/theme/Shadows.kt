package app.aroma.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Ink the shadows are tinted with: a deep sage instead of grey, so they sit naturally on the paper. */
private val ShadowInk = Color(0xFF1B2A19)

/** Whether the current scheme is dark, judged by the page colour so dynamic colour counts too. */
val isDarkScheme: Boolean
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * The shadow of things that float above the page: the bottom bar, the cooking button. Two layers,
 * a wide soft one that lifts and a tight one that grounds the edge; never on content itself.
 */
@Composable
@ReadOnlyComposable
fun Modifier.floatingShadow(shape: Shape): Modifier =
    if (isDarkScheme) {
        dropShadow(shape, Shadow(radius = 32.dp, spread = (-4).dp, color = Color.Black.copy(alpha = 0.55f), offset = DpOffset(0.dp, 12.dp)))
            .dropShadow(shape, Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.35f), offset = DpOffset(0.dp, 1.dp)))
    } else {
        dropShadow(shape, Shadow(radius = 36.dp, spread = (-6).dp, color = ShadowInk.copy(alpha = 0.12f), offset = DpOffset(0.dp, 14.dp)))
            .dropShadow(shape, Shadow(radius = 5.dp, color = ShadowInk.copy(alpha = 0.06f), offset = DpOffset(0.dp, 2.dp)))
    }
