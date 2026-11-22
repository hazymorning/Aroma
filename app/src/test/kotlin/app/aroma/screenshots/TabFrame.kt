package app.aroma.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.navigation.FloatingBar
import app.aroma.ui.navigation.FloatingBarGap
import app.aroma.ui.navigation.FloatingBarHeight
import app.aroma.ui.navigation.Tab

/** A tab page as the app shows it: with the floating bar over its bottom edge. */
@Composable
fun TabFrame(tab: Tab, modifier: Modifier = Modifier, page: @Composable () -> Unit) {
    Box(modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBottomBarInset provides FloatingBarHeight + FloatingBarGap, content = page)
        FloatingBar(
            selected = tab,
            onSelect = {},
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = FloatingBarGap),
        )
    }
}
