package app.aroma.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.ui.components.Text
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.ContentMaxWidth
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.layeredShadow

/** The pill behind the chosen destination's glyph. */
private val PillWidth = 56.dp
private val PillHeight = 32.dp

/** The line a destination's label takes. */
private val LabelHeight = 16.dp

/** The air above the pill, so the glyphs never sit pressed against the bar's top edge. */
private val TopSpace = Space.s16

/** The air under the label, above the system navigation, which adds its own below. */
private val BottomSpace = Space.s12

/** The height of the navigation bar above the system navigation, which it reaches down over. */
internal val TabBarHeight = TopSpace + PillHeight + Space.s4 + LabelHeight + BottomSpace

/**
 * The navigation bar: four destinations on the bars' paper across the bottom edge, down over the
 * system navigation, with the soft shadow of what floats and no line. The chosen one stands on a
 * soft sage pill, its glyph and label in ink; the pill glides to a destination as it is chosen. The
 * others are in second ink. [navigationInset] is the system navigation's height; [onReselect] fires
 * when the chosen destination is tapped again.
 */
@Composable
internal fun TabBar(
    selected: Tab?,
    onSelect: (Tab) -> Unit,
    navigationInset: Dp,
    modifier: Modifier = Modifier,
    onReselect: (Tab) -> Unit = {},
) {
    val colors = AromaTheme.colors
    val index = Tab.entries.indexOf(selected)
    val pillAt by animateFloatAsState(index.coerceAtLeast(0).toFloat(), Motion.state(), label = "navPill")
    Box(
        modifier
            .fillMaxWidth()
            .layeredShadow(AromaTheme.shadows.bottomBar)
            .background(colors.bar)
            .padding(bottom = navigationInset),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .height(TabBarHeight)
                .drawBehind {
                    if (index < 0) return@drawBehind
                    val slot = size.width / Tab.entries.size
                    val pill = Size(PillWidth.toPx(), PillHeight.toPx())
                    drawRoundRect(
                        colors.accentSoft,
                        Offset(slot * (pillAt + 0.5f) - pill.width / 2, TopSpace.toPx()),
                        pill,
                        CornerRadius(pill.height / 2),
                    )
                }.selectableGroup(),
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
    val colors = AromaTheme.colors
    val haptics = LocalHapticFeedback.current
    val ink by animateColorAsState(if (selected) colors.ink else colors.inkSecondary, Motion.state(), label = "navInk")
    Column(
        modifier
            .fillMaxHeight()
            .selectable(selected = selected, interactionSource = null, indication = null, role = Role.Tab) {
                if (selected) {
                    onReselect(tab)
                } else {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(tab)
                }
            }.padding(top = TopSpace),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(PillWidth, PillHeight), contentAlignment = Alignment.Center) {
            Crossfade(selected, animationSpec = Motion.state(), label = "navGlyph") { on ->
                Icon(
                    painterResource(if (on) tab.selectedIcon else tab.icon),
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(Glyph.nav),
                )
            }
        }
        Spacer(Modifier.height(Space.s4))
        // The four names share the bar's width: they grow with the font up to 130 %, so none is cut.
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(MAX_LABEL_SCALE))) {
            Text(
                text = stringResource(tab.label),
                style = if (selected) AromaTheme.type.navSelected else AromaTheme.type.nav,
                color = ink,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** How far the tab names grow with the system's font size. */
private const val MAX_LABEL_SCALE = 1.3f
