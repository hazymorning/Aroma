package app.aroma.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import app.aroma.ui.theme.LocalAromaColors
import app.aroma.ui.theme.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
        coroutineScope.launch { amount.followPresses(interactionSource, rest = 0f, pressed = 1f) }
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

/**
 * Cards and boxes give way a little under the finger: they shrink to [Motion.PRESSED_SCALE] while
 * pressed, in the drawing alone, so a press never composes or lays out anything anew.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource) { scale.followPresses(interactionSource, rest = 1f, pressed = Motion.PRESSED_SCALE) }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Moves this value to [pressed] while [interactionSource] is pressed and back to [rest] once it is
 * let go. A tap quicker than the press can show, as most taps in a list are, still shows it whole
 * before it eases out; a press that turns into a scroll only eases out.
 */
private suspend fun Animatable<Float, AnimationVector1D>.followPresses(interactionSource: InteractionSource, rest: Float, pressed: Float) =
    coroutineScope {
        val presses = mutableListOf<PressInteraction.Press>()
        var motion: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> presses += interaction
                is PressInteraction.Release -> presses -= interaction.press
                is PressInteraction.Cancel -> presses -= interaction.press
                else -> return@collect
            }
            val down = presses.isNotEmpty()
            val tapped = interaction is PressInteraction.Release
            motion?.cancel()
            motion =
                launch {
                    if (down) {
                        animateTo(pressed, tween(Motion.EDGE, easing = Motion.Decelerate))
                    } else {
                        // What is left of the way in, at the pace of a whole press.
                        val left = (pressed - value) / (pressed - rest)
                        if (tapped && left > 0f) animateTo(pressed, tween((left * Motion.EDGE).roundToInt(), easing = Motion.Decelerate))
                        animateTo(rest, tween(Motion.STATE, easing = Motion.Decelerate))
                    }
                }
        }
    }
