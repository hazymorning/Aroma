package app.aroma.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.ui.theme.Lines
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Motion.Settle
import app.aroma.ui.theme.extras
import app.aroma.ui.theme.floatingShadow

/** Height of the bar itself; the space it takes is this plus [FloatingBarGap] and the system bar. */
internal val FloatingBarHeight = 64.dp
internal val FloatingBarGap = 12.dp

/**
 * The tab bar: a stadium of paper resting above the page on a long, soft shadow, the one shadow a
 * tab page casts. There is no pill behind the selected tab; its icon turns into a quiet sage
 * duotone and its label into ink. [onReselect] fires when the current tab is tapped again.
 */
@Composable
internal fun FloatingBar(selected: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier, onReselect: (Tab) -> Unit = {}) {
    val shape = CircleShape
    Row(
        modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .height(FloatingBarHeight)
            .floatingShadow(shape)
            .background(extras.raised, shape)
            .border(Lines.hairline, extras.floatingEdge, shape)
            .padding(horizontal = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { tab -> BarItem(tab, tab == selected, onSelect, onReselect, Modifier.weight(1f)) }
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.6f else 1f, tween(Motion.PRESS), label = "tabPress")
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
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab) {
                if (selected) {
                    onReselect(tab)
                } else {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(tab)
                }
            }.alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Crossfade(selected, animationSpec = tween(Motion.STATE), label = "tabGlyph") { on ->
            Icon(
                painterResource(if (on) tab.selectedIcon else tab.icon),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(tab.label),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The paper the page fades into below the bar, so content dissolves before reaching its edge. */
@Composable
internal fun BarFade(barInset: Dp, modifier: Modifier = Modifier) {
    val page = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .height(barInset + 24.dp)
            .background(Brush.verticalGradient(0f to page.copy(alpha = 0f), 0.45f to page.copy(alpha = 0.85f), 1f to page)),
    )
}
