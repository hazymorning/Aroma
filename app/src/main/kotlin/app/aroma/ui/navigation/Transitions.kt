package app.aroma.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import app.aroma.ui.theme.Motion

/**
 * Calm screen changes, after Material's shared axis: the old screen fades out quickly as it starts
 * to drift away, and the new one fades in once it has gone, drifting the rest of the way in. Both
 * drift on one gliding curve over the whole change, so neither jumps, and both screens are never
 * half visible at the same time. Nothing scales. Switching tabs only fades, because tabs sit side by
 * side rather than in depth.
 */
internal object Transitions {
    /** A whole screen change: the old screen's fade, then the new one's. */
    private const val DURATION = Motion.PRESS + Motion.STATE

    private fun drift(fullWidth: Int) = fullWidth / 12

    private val drifting = tween<IntOffset>(DURATION, easing = Motion.Glide)

    /** The old screen's fade, and what leaves with it. */
    val leave: ExitTransition = fadeOut(tween(Motion.PRESS, easing = LinearEasing))

    /** The new screen's fade once the old one has gone, and what arrives with it. */
    val arrive: EnterTransition = fadeIn(tween(Motion.STATE, delayMillis = Motion.PRESS, easing = LinearEasing))

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

    /** The tab bar comes with its page, rising the last quarter of its height into place, and leaves with it. */
    val barEnter: EnterTransition =
        arrive + slideInVertically(tween(Motion.STATE, delayMillis = Motion.PRESS, easing = Motion.Glide)) { it / 4 }

    val barExit: ExitTransition = leave + slideOutVertically(tween(Motion.PRESS, easing = Motion.Glide)) { it / 4 }

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
        initialState.destination.isTab() && targetState.destination.isTab()
}

/** True for the four destinations reachable from the bottom bar. */
internal fun NavDestination.isTab(): Boolean = Tab.entries.any { hasRoute(it.route::class) }
