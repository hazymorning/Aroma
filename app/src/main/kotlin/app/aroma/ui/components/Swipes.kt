package app.aroma.ui.components

import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Swipe state for list rows that a swipe removes. Unlike the saveable default it is never restored,
 * so a row that comes back, through undo or a refresh, starts at rest instead of swiped away, and
 * cannot remove itself a second time.
 */
@Composable
fun rememberRowSwipeState(): SwipeToDismissBoxState {
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    return remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
}
