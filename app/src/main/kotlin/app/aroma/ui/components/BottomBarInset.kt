package app.aroma.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much of the bottom of a tab screen the floating navigation bar covers, system navigation
 * included. Tab screens keep this much space free below their content; 0 where there is no bar.
 */
val LocalBottomBarInset = staticCompositionLocalOf { 0.dp }

/** Room below the last item of a scrolling page, so it never ends flush with an edge. */
val PageEndSpace: Dp = 24.dp
