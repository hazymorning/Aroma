package app.aroma.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme
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

/** From the row of a pushed page's back button to its title, so the circle never sits on the title. */
val NavigationGap: Dp = Space.s16

/** From a title to the line under it. */
val SubtitleGap: Dp = Space.s4

/** From a small line over a title, its eyebrow ("Heute Abend", "Wie hat es geschmeckt?"), to the title. */
val EyebrowGap: Dp = Space.s8

/** From a page's head to its content. */
val HeaderGap: Dp = Space.s24

/** Scrolling content ends this far above any bar. */
val EndSpace: Dp = Space.s24

/** Between the columns of the recipe grid. */
val GridColumnGap: Dp = Space.s16

/**
 * Between the rows of the recipe grid. The one distance off the scale: a row of cards carries its
 * title and meta under the photo, and needs a little more than 24 dp.
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
}

/**
 * Motion: 150 ms for edges and things that fade in, 250 ms for a change of state and the selection
 * pill, 220 ms for a quiet change of page. Everything decelerates; springs never overshoot, and
 * nothing zooms.
 */
object Motion {
    const val EDGE = 150
    const val STATE = 250
    const val PAGE = 220

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

    /**
     * The soft edge under the status bar: content that scrolls beneath it fades out over this much
     * instead of being cut by a line. It comes with the scroll, so nothing at rest stands in it.
     */
    val ScrollEdge: Dp = Space.s16

    /** How far a menu, a snackbar or a dialog drifts into place as it fades in. */
    val Drift: Dp = Space.s8

    /** How far a sheet follows the back gesture down before it is let go. */
    val SheetBack: Dp = Space.s48

    /** A skeleton waits this long before it fades in, so what loads at once never flashes one. */
    const val SKELETON_DELAY = 150

    /** Spring stiffnesses that come to rest in about the time of an edge, a change of state and a slow move. */
    const val STIFFNESS_EDGE = 1500f
    const val STIFFNESS_STATE = 550f
    const val STIFFNESS_SLOW = 300f

    /** How far a figure that changes drifts as it rolls, a share of its line: up as it counts up, down as it counts down. */
    const val ROLL = 0.4f

    /** What takes another's place, a label in its button: it fades in once the other has all but faded, so the two never blur. */
    fun <T> after() = tween<T>(EDGE, delayMillis = EDGE / 2, easing = Decelerate)

    /** How fast the photo at the head of a page moves as the page scrolls over it, against the page's own pace. */
    const val PARALLAX = 0.5f

    /** How much of a swipe the page under the finger follows, held back, before letting go turns it. */
    const val SWIPE_PULL = 0.4f
}

/**
 * Material's parts move as Aroma's own do: sheets, the pull to refresh and the few other parts Aroma
 * takes from Material come to rest on springs that never overshoot, as quickly as an edge or a
 * change of state. A sheet that is put away glides off instead of vanishing in a few frames.
 */
object AromaMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = settle(Motion.STIFFNESS_STATE)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = settle(Motion.STIFFNESS_EDGE)

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = settle(Motion.STIFFNESS_SLOW)

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = settle(Motion.STIFFNESS_STATE)

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = settle(Motion.STIFFNESS_EDGE)

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = settle(Motion.STIFFNESS_SLOW)

    private fun <T> settle(stiffness: Float): FiniteAnimationSpec<T> = spring(Spring.DampingRatioNoBouncy, stiffness)
}
