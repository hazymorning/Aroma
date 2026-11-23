package app.aroma.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import app.aroma.ui.theme.Motion
import kotlinx.coroutines.launch

/** Pressing text, links and glyphs dims them instead of blooming a ripple. */
val QuietPress: IndicationNodeFactory = DimOnPress(0.6f)

/** Pressing a photo dims it less than text, as it carries more weight. */
val PhotoPress: IndicationNodeFactory = DimOnPress(0.88f)

/** Dims what is pressed to [pressedAlpha] at once, and lets it come back calmly. */
private class DimOnPress(private val pressedAlpha: Float) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = DimNode(interactionSource, pressedAlpha)

    override fun equals(other: Any?): Boolean = other is DimOnPress && other.pressedAlpha == pressedAlpha

    override fun hashCode(): Int = pressedAlpha.hashCode()
}

private class DimNode(private val interactionSource: InteractionSource, private val pressedAlpha: Float) :
    Modifier.Node(),
    DrawModifierNode {
    private var alpha = Animatable(1f)
    private val paint = Paint()

    override fun onAttach() {
        coroutineScope.launch {
            val presses = mutableListOf<PressInteraction.Press>()
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                    else -> return@collect
                }
                val pressed = presses.isNotEmpty()
                launch { alpha.animateTo(if (pressed) pressedAlpha else 1f, tween(if (pressed) Motion.PRESS else Motion.STATE)) }
            }
        }
    }

    // A node that is attached again, as in a lazy list, starts undimmed.
    override fun onDetach() {
        alpha = Animatable(1f)
    }

    override fun ContentDrawScope.draw() {
        val value = alpha.value
        if (value == 1f) {
            drawContent()
        } else {
            paint.alpha = value
            drawIntoCanvas { canvas ->
                canvas.saveLayer(size.toRect(), paint)
                drawContent()
                canvas.restore()
            }
        }
    }
}
