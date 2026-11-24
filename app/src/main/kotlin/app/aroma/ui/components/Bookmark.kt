package app.aroma.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.Motion

/**
 * The bookmark ribbon's shape: a strip with a V cut into its foot, as deep as a fifth of its height.
 */
@Immutable
object RibbonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(
            Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height)
                lineTo(size.width / 2, size.height * (1 - NOTCH))
                lineTo(0f, size.height)
                close()
            },
        )
}

/** How deep the V at the ribbon's foot is cut, as a share of its height. */
private const val NOTCH = 0.2f

/** The ribbon on a card's photo, and on the recipe page's photo. */
enum class RibbonSize(val size: DpSize) {
    Card(DpSize(12.dp, 36.dp)),
    Hero(DpSize(16.dp, 52.dp)),
}

/**
 * The bookmark ribbon of a saved recipe, in sage, hanging from the top edge of its photo. It
 * replaces the heart everywhere. Where it comes and goes in view ([animated]), it slides down from
 * the edge as the recipe is saved, and back up as it is let go.
 */
@Composable
fun Ribbon(saved: Boolean, modifier: Modifier = Modifier, size: RibbonSize = RibbonSize.Card, animated: Boolean = false) {
    val strip = @Composable { Box(Modifier.size(size.size).background(AromaTheme.colors.accent, RibbonShape)) }
    if (!animated) {
        if (saved) Box(modifier) { strip() }
        return
    }
    AnimatedVisibility(
        visible = saved,
        modifier = modifier,
        enter = slideInVertically(tween(Motion.STATE, easing = Motion.Decelerate)) { -it },
        exit = slideOutVertically(tween(Motion.STATE, easing = Motion.Decelerate)) { -it },
    ) { strip() }
}
