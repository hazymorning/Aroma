package app.aroma.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.floatingShadow

/** The app's snackbars: ink on a floating card, put away with a swipe to either side. */
@Composable
fun AromaSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier.padding(horizontal = ScreenMargin)) { data ->
        key(data) {
            SwipeToDismissBox(
                state = rememberSwipeToDismissBoxState(),
                backgroundContent = {},
                onDismiss = { data.dismiss() },
            ) {
                Snackbar(
                    snackbarData = data,
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary,
                    modifier = Modifier.floatingShadow(MaterialTheme.shapes.medium),
                )
            }
        }
    }
}
