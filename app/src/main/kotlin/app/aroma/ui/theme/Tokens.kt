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
}

/**
 * The page's one edge, left and right: Android's margin for phones. Titles, photos, fields,
 * buttons, chips and the edges of cards all stand on it.
 */
val ScreenMargin: Dp = 16.dp

/** How far the content of a card stands inside the card's edge. */
val CardPadding: Dp = 16.dp

/** The edge of text inside a card, on the page: where a group's label stands above its card. */
val ContentEdge: Dp = ScreenMargin + CardPadding

/** The space above a section's title, which sets sections apart. */
val SectionSpace: Dp = 32.dp

/** Content wider than this is centred, so lines stay readable on tablets. */
val ContentMaxWidth: Dp = 600.dp

/** Room below the last item of a scrolling page, so it never ends flush with an edge. */
val PageEndSpace: Dp = 24.dp

/** The least size of anything that takes a tap. */
val TouchTarget: Dp = 48.dp

/** The height of buttons and fields that span a column, and of a round button beside them. */
val ControlHeight: Dp = 52.dp

/** The height of chips and compact buttons; their touch area reaches 48 dp beyond it. */
val ChipHeight: Dp = 36.dp

/** The least height of a row: of one line, and of two lines or a thumbnail. */
object RowHeight {
    val oneLine: Dp = 56.dp
    val twoLines: Dp = 72.dp
}

/** A photo or a face at the start of a row. */
val ThumbnailSize: Dp = 56.dp

/** Line glyphs: beside small text, in rows, fields and buttons, in bars, and standing alone. */
object Glyph {
    val s: Dp = 16.dp
    val m: Dp = 20.dp
    val l: Dp = 24.dp
    val xl: Dp = 32.dp
}

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

/** Thin lines: between rows inside a card, the ring of an open check, the focus of a field. */
object Lines {
    val hairline: Dp = 0.5.dp
    val checkRing: Dp = 1.25.dp
    val focus: Dp = 1.5.dp
}
