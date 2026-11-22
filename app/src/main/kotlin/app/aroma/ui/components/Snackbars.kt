package app.aroma.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier

/** The app's snackbars, which a swipe to either side puts away like a notification. */
@Composable
fun AromaSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        key(data) {
            SwipeToDismissBox(
                state = rememberSwipeToDismissBoxState(),
                backgroundContent = {},
                onDismiss = { data.dismiss() },
            ) {
                Snackbar(data)
            }
        }
    }
}
