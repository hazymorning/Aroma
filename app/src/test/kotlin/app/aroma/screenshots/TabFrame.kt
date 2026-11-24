package app.aroma.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.navigation.Tab
import app.aroma.ui.navigation.TabBar
import app.aroma.ui.navigation.TabBarHeight

/** The gesture navigation of a typical phone, which the tab bar reaches down over. */
private val GestureNavigation = 24.dp

/** A tab page as the app shows it: with the tab bar across its bottom edge. */
@Composable
fun TabFrame(tab: Tab, modifier: Modifier = Modifier, page: @Composable () -> Unit) {
    Box(modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBottomBarInset provides TabBarHeight + GestureNavigation, content = page)
        TabBar(
            selected = tab,
            onSelect = {},
            navigationInset = GestureNavigation,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
