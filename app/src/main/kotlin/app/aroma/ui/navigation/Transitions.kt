package app.aroma.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

/**
 * Calm screen changes: the old screen fades out quickly while the new one settles in with a
 * short sideways drift. Nothing scales, and both screens are never half visible at the same
 * time for long. Switching tabs only fades, because tabs sit side by side rather than in depth.
 */
internal object Transitions {
    private const val DURATION = 320
    private const val FADE_OUT = 110
    private const val FADE_IN = 210

    /** Material's emphasized decelerate curve: quick start, long soft landing. */
    private val Settle = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    private fun drift(fullWidth: Int) = fullWidth / 12

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (isTabSwitch()) {
            fadeIn(tween(FADE_IN, delayMillis = FADE_OUT, easing = LinearEasing))
        } else {
            slideInHorizontally(tween(DURATION, easing = Settle)) { drift(it) } +
                fadeIn(tween(FADE_IN, delayMillis = FADE_OUT, easing = LinearEasing))
        }
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (isTabSwitch()) {
            fadeOut(tween(FADE_OUT, easing = LinearEasing))
        } else {
            slideOutHorizontally(tween(DURATION, easing = Settle)) { -drift(it) } + fadeOut(tween(FADE_OUT, easing = LinearEasing))
        }
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (isTabSwitch()) {
            fadeIn(tween(FADE_IN, delayMillis = FADE_OUT, easing = LinearEasing))
        } else {
            slideInHorizontally(tween(DURATION, easing = Settle)) { -drift(it) } +
                fadeIn(tween(FADE_IN, delayMillis = FADE_OUT, easing = LinearEasing))
        }
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (isTabSwitch()) {
            fadeOut(tween(FADE_OUT, easing = LinearEasing))
        } else {
            slideOutHorizontally(tween(DURATION, easing = Settle)) { drift(it) } + fadeOut(tween(FADE_OUT, easing = LinearEasing))
        }
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

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
        initialState.destination.isTab() && targetState.destination.isTab()
}

/** True for the four destinations reachable from the bottom bar. */
internal fun NavDestination.isTab(): Boolean = Tab.entries.any { hasRoute(it.route::class) }
