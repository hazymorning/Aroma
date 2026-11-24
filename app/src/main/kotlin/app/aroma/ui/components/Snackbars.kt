package app.aroma.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.layeredShadow

/** The app's snackbars: paper words on ink, the action in soft sage, floating on their shadow; a swipe to either side puts them away. */
@Composable
fun AromaSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val colors = AromaTheme.colors
    val shape = AromaTheme.shapes.box
    val shadows = AromaTheme.shadows
    SnackbarHost(state, modifier.padding(horizontal = PageMargin)) { data ->
        key(data) {
            SwipeToDismissBox(
                state = rememberSwipeToDismissBoxState(),
                backgroundContent = {},
                onDismiss = { data.dismiss() },
            ) {
                Snackbar(
                    snackbarData = data,
                    shape = shape,
                    containerColor = colors.ink,
                    contentColor = colors.onInk,
                    actionColor = colors.accentSoft,
                    modifier = Modifier.layeredShadow(shadows.menu, shape),
                )
            }
        }
    }
}
