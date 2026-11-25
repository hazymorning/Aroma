package app.aroma.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** One layer of a shadow: how far it falls, how soft it is and how dark, in the theme's shadow colour. */
@Immutable
data class ShadowLayer(val offsetY: Dp, val blur: Dp, val opacity: Float)

/**
 * The shadows of what floats over the page, and nothing else casts one: soft, in several layers,
 * tinted warm. In the dark they are twice as dense, and the bars stand out by their lighter paper.
 */
@Immutable
class AromaShadows(dark: Boolean) {
    private val density = if (dark) 2f else 1f

    private fun layers(vararg layers: ShadowLayer) = layers.map { it.copy(opacity = it.opacity * density) }

    /** The navigation bar and the action bars: the shadow rises softly onto the page above them. */
    val bottomBar = layers(ShadowLayer((-1).dp, 3.dp, 0.05f), ShadowLayer((-6).dp, 16.dp, 0.06f), ShadowLayer((-16).dp, 40.dp, 0.06f))

    /** The collapsed head, once content has scrolled beneath it. */
    val topBar = layers(ShadowLayer(1.dp, 3.dp, 0.04f), ShadowLayer(6.dp, 16.dp, 0.05f))

    /** Sheets. */
    val sheet = layers(ShadowLayer((-2).dp, 8.dp, 0.06f), ShadowLayer((-12).dp, 40.dp, 0.10f))

    /** Menus and the snackbar. */
    val menu = layers(ShadowLayer(2.dp, 6.dp, 0.06f), ShadowLayer(12.dp, 32.dp, 0.10f))
}

/** Draws [layers] under what follows, in the theme's shadow colour; [alpha] fades the whole shadow, for one that comes and goes. */
@Composable
@ReadOnlyComposable
fun Modifier.layeredShadow(layers: List<ShadowLayer>, shape: Shape = RectangleShape, alpha: Float = 1f): Modifier {
    if (alpha <= 0f) return this
    val color = AromaTheme.colors.shadow
    return layers.fold(this) { modifier, layer ->
        modifier.dropShadow(
            shape,
            Shadow(radius = layer.blur, color = color, alpha = layer.opacity * alpha, offset = DpOffset(0.dp, layer.offsetY)),
        )
    }
}

/**
 * [layeredShadow] for a shadow that fades as it comes and goes: [alpha] is read while drawing, so
 * the fade costs no composition.
 */
@Composable
@ReadOnlyComposable
fun Modifier.layeredShadow(layers: List<ShadowLayer>, shape: Shape = RectangleShape, alpha: () -> Float): Modifier {
    val shadowColor = AromaTheme.colors.shadow
    return layers.fold(this) { modifier, layer ->
        modifier.dropShadow(shape) {
            radius = layer.blur.toPx()
            color = shadowColor
            this.alpha = layer.opacity * alpha()
            offset = Offset(0f, layer.offsetY.toPx())
        }
    }
}
