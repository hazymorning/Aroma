package app.aroma.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The spacing scale. Layouts use these steps only; component heights are the one exception. */
object Space {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val s: Dp = 12.dp
    val m: Dp = 16.dp
    val l: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
    val xxxl: Dp = 64.dp
}

/** The one left (and right) edge every title, paragraph, field and photo starts on. */
val ScreenMargin: Dp = 24.dp

/** How far a [app.aroma.ui.components.SoftCard] stands outside the text column: half the margin. */
val CardInset: Dp = 12.dp

/** Text after a 24 dp check or icon. */
val IconKeyline: Dp = 64.dp

/** Durations in milliseconds, and the curve that movement and size changes settle with. */
object Motion {
    const val PRESS = 120
    const val STATE = 180
    const val EXPAND = 240

    /** One breath of something that pulses while it waits: a placeholder, a finished timer. */
    const val PULSE = 1000

    /** Material's emphasized decelerate curve: quick start, long soft landing. */
    val Settle = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}

/** Thin lines: between rows, around controls, and the ring of a check circle. */
object Lines {
    val hairline: Dp = 0.5.dp
    val control: Dp = 1.dp
    val checkRing: Dp = 1.25.dp
}
