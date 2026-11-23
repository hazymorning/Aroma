package app.aroma.ui.theme

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

/** Durations in milliseconds; everything settles with [app.aroma.ui.navigation.Transitions.Settle]. */
object Motion {
    const val PRESS = 120
    const val STATE = 180
    const val EXPAND = 240
}

/** Thin lines: between rows, around controls, and the ring of a check circle. */
object Lines {
    val hairline: Dp = 0.5.dp
    val control: Dp = 1.dp
    val checkRing: Dp = 1.25.dp
}
