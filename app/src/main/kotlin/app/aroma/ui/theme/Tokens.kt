package app.aroma.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale: every distance between things is one of these and nothing else. Sizes of
 * things, a button's height or a thumbnail's side, are not distances and live with their parts.
 */
object Space {
    val s4: Dp = 4.dp
    val s8: Dp = 8.dp
    val s12: Dp = 12.dp
    val s16: Dp = 16.dp
    val s20: Dp = 20.dp
    val s24: Dp = 24.dp
    val s32: Dp = 32.dp
    val s40: Dp = 40.dp
    val s48: Dp = 48.dp
    val s64: Dp = 64.dp
}

/** The page's edge for text, lists and boxes; a box's edge stands on the same line as the text. */
val PageMargin: Dp = Space.s24

/** Content inside a box keeps this far from the box's edge. */
val BoxPadding: Dp = Space.s16

/** Between boxes standing one under the other. */
val BoxGap: Dp = Space.s12

/** Between sections. */
val SectionGap: Dp = Space.s40

/** From a section's title to its content. */
val SectionTitleGap: Dp = Space.s12

/** From the status bar to a page's title. */
val HeaderTop: Dp = Space.s16

/** From a title to the line under it. */
val SubtitleGap: Dp = Space.s4

/** From a page's head to its content. */
val HeaderGap: Dp = Space.s24

/** How far a highlighted row's fill reaches beyond the column; its content stays on the column. */
val HighlightReach: Dp = Space.s12

/** Scrolling content ends this far above any bar. */
val EndSpace: Dp = Space.s24

/** Between the columns of the recipe grid. */
val GridColumnGap: Dp = Space.s16

/**
 * Between the rows of the recipe grid. The one distance off the scale, as the brief asks for it: a
 * row of cards carries its title and meta under the photo, and needs a little more than 24 dp.
 */
val GridRowGap: Dp = 28.dp

/** Content wider than this is centred, so lines stay readable on large screens and on a phone held sideways. */
val ContentMaxWidth: Dp = 600.dp

/** The least size of anything that takes a tap, however small it looks. */
val TouchTarget: Dp = 48.dp

/** A photo or a face at the start of a row. */
val ThumbnailSize: Dp = 56.dp

/** The least height of a row: of one line, and of two lines or a thumbnail. */
object RowHeight {
    val oneLine: Dp = 56.dp
    val twoLines: Dp = 72.dp
}

/** Glyph sizes: beside meta text, in buttons, rows and icon buttons, and in the navigation. */
object Glyph {
    val meta: Dp = 16.dp
    val control: Dp = 20.dp
    val nav: Dp = 24.dp
}

/** Corner radii. Buttons, pills, the search field and the stepper are fully round. */
object Radius {
    val box: Dp = 20.dp
    val cardImage: Dp = 14.dp
    val thumbnail: Dp = 12.dp
    val sheet: Dp = 28.dp

    /** A shape inside another follows its corner: the outer radius less the room between them, at least 4 dp. */
    fun inner(outer: Dp, inset: Dp): Dp = (outer - inset).coerceAtLeast(Space.s4)
}

/**
 * Motion: 150 ms for edges and things that fade in, 250 ms for a change of state and the selection
 * pill, 350 ms for a change of page. Everything decelerates; springs never overshoot.
 */
object Motion {
    const val EDGE = 150
    const val STATE = 250
    const val PAGE = 350

    /** A photo fading in over its placeholder. */
    const val IMAGE = 200

    /** One breath of a skeleton, from 60 % to full and back. */
    const val PULSE = 1200

    /** How small a card or box gets while pressed. */
    const val PRESSED_SCALE = 0.98f

    /** The standard deceleration curve: fast away, slow to rest. */
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    /** A spring for what a finger drags and lets go: it carries on at the finger's speed and never overshoots. */
    val LetGo: AnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    fun <T> edge() = tween<T>(EDGE, easing = Decelerate)

    fun <T> state() = tween<T>(STATE, easing = Decelerate)

    fun <T> page() = tween<T>(PAGE, easing = Decelerate)
}
