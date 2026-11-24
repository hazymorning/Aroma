package app.aroma.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.ui.components.QuietPress
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.ContentMaxWidth
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Lines
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Motion.Settle
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.barShadow
import app.aroma.ui.theme.extras
import app.aroma.ui.theme.isDarkScheme

/**
 * Height of the bar above the system navigation: 20 dp of air above each glyph and below each label.
 * The space the bar takes is this plus the system navigation, which it reaches down over.
 */
internal val TabBarHeight = 84.dp

/**
 * The tab bar: a sheet of paper across the bottom edge, from side to side and down over the system
 * navigation, casting a soft shadow up onto the page that scrolls on below it. The tabs stand far
 * apart with air above and below; there is no pill behind the selected one, its icon turns into a
 * quiet sage duotone and its label into ink. On wide screens the tabs keep to the page's width.
 * [navigationInset] is the system navigation's height. [onReselect] fires when the current tab is
 * tapped again.
 */
@Composable
internal fun TabBar(
    selected: Tab?,
    onSelect: (Tab) -> Unit,
    navigationInset: Dp,
    modifier: Modifier = Modifier,
    onReselect: (Tab) -> Unit = {},
) {
    val edge = extras.floatingEdge
    // In the dark, a step below the sheets' paper, so the bar stays calm across the whole width.
    val paper = if (isDarkScheme) MaterialTheme.colorScheme.surfaceContainerHigh else extras.raised
    Box(
        modifier
            .fillMaxWidth()
            .barShadow()
            .background(paper)
            .drawBehind { drawLine(edge, Offset.Zero, Offset(size.width, 0f), Lines.hairline.toPx()) }
            .padding(bottom = navigationInset),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .height(TabBarHeight)
                .padding(horizontal = ScreenMargin)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { tab -> BarItem(tab, tab == selected, onSelect, onReselect, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun BarItem(
    tab: Tab,
    selected: Boolean,
    onSelect: (Tab) -> Unit,
    onReselect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val iconColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(Motion.STATE, easing = Settle),
        label = "tabIcon",
    )
    val labelColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(Motion.STATE, easing = Settle),
        label = "tabLabel",
    )
    Column(
        modifier
            .fillMaxHeight()
            .selectable(selected = selected, interactionSource = null, indication = QuietPress, role = Role.Tab) {
                if (selected) {
                    onReselect(tab)
                } else {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(tab)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Crossfade(selected, animationSpec = tween(Motion.STATE), label = "tabGlyph") { on ->
            Icon(
                painterResource(if (on) tab.selectedIcon else tab.icon),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(Glyph.l),
            )
        }
        Spacer(Modifier.height(Space.xxs))
        Text(
            text = stringResource(tab.label),
            style = AromaType.Caption,
            color = labelColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}
