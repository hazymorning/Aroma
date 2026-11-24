package app.aroma.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
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
 * rather than in depth. With the system's animations off, pages only cross-fade.
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
    val leave: ExitTransition = fadeOut(tween(LEAVE, easing = LinearEasing))

    /** The new screen's fade, and what arrives with it. */
    val arrive: EnterTransition = fadeIn(tween(DURATION - ARRIVE_DELAY, delayMillis = ARRIVE_DELAY, easing = LinearEasing))

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
     * The back gesture plays the same motion, driven by the finger. Linear curves keep the screen
     * exactly under the finger; the library's default would shrink the page instead.
     */
    val predictivePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition = {
        slideInHorizontally(tween(DURATION, easing = LinearEasing)) { -drift(it) } + fadeIn(tween(DURATION, easing = LinearEasing))
    }

    val predictivePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition = {
        slideOutHorizontally(tween(DURATION, easing = LinearEasing)) { drift(it) } + fadeOut(tween(DURATION, easing = LinearEasing))
    }

    /** The tab bar comes and goes with its page, fading in place. */
    val barEnter: EnterTransition = arrive

    val barExit: ExitTransition = leave

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
        initialState.destination.isTab() && targetState.destination.isTab()
}

/** True for the four destinations reachable from the bottom bar. */
internal fun NavDestination.isTab(): Boolean = Tab.entries.any { hasRoute(it.route::class) }
