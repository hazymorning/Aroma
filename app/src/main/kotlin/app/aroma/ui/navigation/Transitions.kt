package app.aroma.ui.navigation

import androidx.activity.BackEventCompat
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import app.aroma.ui.theme.Motion

/**
 * Quiet, quick screen changes: the old screen fades out in 90 ms, the new one fades in over it and
 * drifts the last few dp into place, 220 ms in all. Nothing scales, nothing grows, and nothing
 * travels from one screen to the next. Switching tabs only fades, because tabs sit side by side
 * rather than in depth. With the system's animations off, pages only cross-fade, under the back
 * gesture too.
 */
internal object Transitions {
    /** A whole screen change. */
    private const val DURATION = Motion.PAGE

    /** How long the old screen takes to fade, and when the new one starts to show. */
    private const val LEAVE = 90
    private const val ARRIVE_DELAY = 60

    /** A drift of a few dp, enough to tell forward from back. */
    private fun drift(fullWidth: Int) = fullWidth / 24

    private val drifting = tween<IntOffset>(DURATION, easing = Motion.Decelerate)

    /** The old screen's fade, and what leaves with it. */
    val leave: ExitTransition = fadeOut(leaving())

    /** The new screen's fade, and what arrives with it. */
    val arrive: EnterTransition = fadeIn(arriving())

    private fun <T> leaving(): FiniteAnimationSpec<T> = tween(LEAVE, easing = LinearEasing)

    private fun <T> arriving(): FiniteAnimationSpec<T> = tween(DURATION - ARRIVE_DELAY, delayMillis = ARRIVE_DELAY, easing = LinearEasing)

    /** What the back gesture drives: the whole change at the finger's pace, linear so the page stays under the finger. */
    private fun <T> underFinger(): FiniteAnimationSpec<T> = tween(DURATION, easing = LinearEasing)

    /** Pages that only cross-fade, without moving. */
    val fadeIn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { fadeIn(tween(Motion.EDGE)) }

    val fadeOut: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { fadeOut(tween(Motion.EDGE)) }

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (isTabSwitch()) arrive else slideInHorizontally(drifting) { drift(it) } + arrive
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (isTabSwitch()) leave else slideOutHorizontally(drifting) { -drift(it) } + leave
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (isTabSwitch()) arrive else slideInHorizontally(drifting) { -drift(it) } + arrive
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (isTabSwitch()) leave else slideOutHorizontally(drifting) { drift(it) } + leave
    }

    /**
     * The back gesture plays the same motion, driven by the finger: the page drifts the way the
     * finger moves, to the right from the left edge and to the left from the right one, and the
     * page under it comes from the other side. Linear curves keep the page exactly under the
     * finger; the library's default would shrink the page instead.
     */
    val predictivePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition = { edge ->
        slideInHorizontally(underFinger()) { -drift(it) * withFinger(edge) } + fadeIn(underFinger())
    }

    val predictivePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition = { edge ->
        slideOutHorizontally(underFinger()) { drift(it) * withFinger(edge) } + fadeOut(underFinger())
    }

    /** The back gesture with the system's animations off: the pages only cross-fade as the finger goes. */
    val predictiveFadeIn: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition = { fadeIn(underFinger()) }

    val predictiveFadeOut: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition = { fadeOut(underFinger()) }

    /** 1 where the finger moves to the right, from the left edge or from no edge at all; -1 from the right edge. */
    private fun withFinger(edge: Int): Int = if (edge == BackEventCompat.EDGE_RIGHT) -1 else 1

    /**
     * The tab bar's share of a tab page's own fade, for [page]'s transition: in step with the page
     * as it arrives and as it leaves, also where the back gesture drives it.
     */
    fun barFade(page: Transition.Segment<EnterExitState>): FiniteAnimationSpec<Float> =
        if (page.targetState == EnterExitState.Visible) arriving() else leaving()

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
        initialState.destination.isTab() && targetState.destination.isTab()
}

/** True for the four destinations reachable from the bottom bar. */
internal fun NavDestination.isTab(): Boolean = Tab.entries.any { hasRoute(it.route::class) }
