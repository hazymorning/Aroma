package app.aroma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.LocalTonalFill
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.layeredShadow

/** The air around the bar's buttons, the same on every side. */
private val BarPadding = Space.s16

/** The height of [BottomActionBar] above the system navigation, around 48 dp buttons. */
val ActionBarHeight: Dp = BarPadding + 48.dp + BarPadding

/** How much of the window's foot a [BottomActionBar] takes, the system navigation included. */
@Composable
fun actionBarInset(): Dp = ActionBarHeight + with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

/**
 * The bar at the foot of a page whose main action stays at hand while the page scrolls: 48 dp icon
 * buttons on the left ([IconButtonPlace.Bar]), the page's one primary button across the rest, with
 * 16 dp of air on every side; taller buttons, as in cook mode, make the bar taller. It rests on the
 * bottom edge, down over the system navigation, on the bars' paper, and casts its soft shadow up
 * onto the page; it has no line.
 */
@Composable
fun BottomActionBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = AromaTheme.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .layeredShadow(AromaTheme.shadows.bottomBar)
                .background(colors.bar)
                .navigationBarsPadding()
                .heightIn(min = ActionBarHeight)
                .padding(BarPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s8),
    ) {
        CompositionLocalProvider(LocalTonalFill provides colors.surfaceOnBar) { content() }
    }
}
