package app.aroma.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.aroma.ui.navigation.Transitions.Settle
import app.aroma.ui.theme.floatingShadow
import app.aroma.ui.theme.isDarkScheme

/** Height of the bar itself; the space it takes is this plus [FloatingBarGap] and the system bar. */
internal val FloatingBarHeight = 64.dp
internal val FloatingBarGap = 12.dp

/**
 * The tab bar: a white stadium resting above the page on a soft shadow, with a capsule that
 * glides to the selected tab. Content scrolls on underneath it.
 */
@Composable
internal fun FloatingBar(selected: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val shape = CircleShape
    val dark = isDarkScheme
    BoxWithConstraints(
        modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .height(FloatingBarHeight)
            .floatingShadow(shape)
            .background(if (dark) Color(0xFF232621) else MaterialTheme.colorScheme.surfaceContainerLowest, shape)
            .border(0.5.dp, if (dark) Color.White.copy(alpha = 0.07f) else Color(0xFF1C2A1A).copy(alpha = 0.05f), shape)
            .padding(6.dp),
    ) {
        val tabs = Tab.entries
        val itemWidth = maxWidth / tabs.size
        val index = tabs.indexOf(selected)
        val capsuleOffset by animateDpAsState(itemWidth * index.coerceAtLeast(0), tween(320, easing = Settle), label = "capsule")
        if (index >= 0) {
            Box(
                Modifier
                    .offset { IntOffset(capsuleOffset.roundToPx(), 0) }
                    .width(itemWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (dark) 0.7f else 0.85f), shape),
            )
        }
        Row(Modifier.fillMaxSize().selectableGroup(), horizontalArrangement = Arrangement.SpaceEvenly) {
            tabs.forEach { tab -> BarItem(tab, tab == selected, onSelect, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun BarItem(tab: Tab, selected: Boolean, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(220),
        label = "tabColor",
    )
    Column(
        modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = { if (!selected) onSelect(tab) }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(if (selected) tab.selectedIcon else tab.icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(tab.label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
            maxLines = 1,
        )
    }
}
