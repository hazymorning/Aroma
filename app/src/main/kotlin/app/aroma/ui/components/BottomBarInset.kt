package app.aroma.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * How much of the bottom of a tab screen the tab bar covers, system navigation included. Tab
 * screens keep this much space free below their content; 0 where there is no bar.
 */
val LocalBottomBarInset = staticCompositionLocalOf { 0.dp }

/** Emits when the tab of the current page is tapped again, which takes the page back to its top. */
val LocalScrollToTop = staticCompositionLocalOf<Flow<Unit>> { emptyFlow() }

/** Scrolls [state] back to the top when the page's tab is tapped again. */
@Composable
fun ScrollToTopOnReselect(state: LazyListState) {
    val reselect = LocalScrollToTop.current
    LaunchedEffect(state, reselect) { reselect.collect { state.animateScrollToItem(0) } }
}

/** Scrolls [state] back to the top when the page's tab is tapped again. */
@Composable
fun ScrollToTopOnReselect(state: LazyGridState) {
    val reselect = LocalScrollToTop.current
    LaunchedEffect(state, reselect) { reselect.collect { state.animateScrollToItem(0) } }
}
