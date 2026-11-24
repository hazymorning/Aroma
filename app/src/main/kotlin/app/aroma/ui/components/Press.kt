package app.aroma.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import app.aroma.ui.theme.LocalAromaColors
import app.aroma.ui.theme.Motion
import kotlinx.coroutines.launch

/**
 * How a press shows: a faint tint of ink over what is pressed, in at once and out calmly, instead
 * of a coloured ripple. The theme sets it for everything that takes a tap.
 */
val PressTint: IndicationNodeFactory = Tint(onInk = false)

/** The press of what is filled with ink, the primary button: a faint tint of paper. */
val PressTintOnInk: IndicationNodeFactory = Tint(onInk = true)

private class Tint(private val onInk: Boolean) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = TintNode(interactionSource, onInk)

    override fun equals(other: Any?): Boolean = other is Tint && other.onInk == onInk

    override fun hashCode(): Int = onInk.hashCode()
}

private class TintNode(private val interactionSource: InteractionSource, private val onInk: Boolean) :
    Modifier.Node(),
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {
    private var amount = Animatable(0f)

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
                launch { amount.animateTo(if (pressed) 1f else 0f, tween(if (pressed) Motion.EDGE else Motion.STATE)) }
            }
        }
    }

    // A node that is attached again, as in a lazy list, starts unpressed.
    override fun onDetach() {
        amount = Animatable(0f)
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val value = amount.value
        if (value > 0f) {
            val colors = currentValueOf(LocalAromaColors)
            val tint = if (onInk) colors.onInk.copy(alpha = ON_INK_TINT) else colors.ink.copy(alpha = INK_TINT)
            drawRect(tint.copy(alpha = tint.alpha * value))
        }
    }
}

/** Ink at 6 %, as the brief has it; on ink, paper a little stronger, as dark fills hide a faint tint. */
private const val INK_TINT = 0.06f
private const val ON_INK_TINT = 0.12f

/** Cards and boxes give way a little under the finger: they shrink to [Motion.PRESSED_SCALE] while pressed. */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) Motion.PRESSED_SCALE else 1f,
        tween(if (pressed) Motion.EDGE else Motion.STATE, easing = Motion.Decelerate),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
