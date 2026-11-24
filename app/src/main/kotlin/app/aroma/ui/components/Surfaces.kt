package app.aroma.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.ui.components.LocalScreenTransition
import app.aroma.ui.components.LocalSharedTransition
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.LocalTonalFill
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.layeredShadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A bottom sheet on the bars' paper, 28 dp corners at its top, a fine handle and its soft shadow,
 * opening at its full height under its [title] and a [meta] line where it needs one. A recipe's
 * name as the title ([titleStyle]) is set as a recipe's name. Controls on it take the bars' tonal
 * fill. The content is already clear of the navigation bar and the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AromaBottomSheet(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberFullSheetState(),
    titleStyle: TextStyle = AromaTheme.type.labelLarge,
    meta: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AromaTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.layeredShadow(AromaTheme.shadows.sheet, AromaTheme.shapes.sheet),
        sheetState = sheetState,
        shape = AromaTheme.shapes.sheet,
        containerColor = colors.bar,
        contentColor = colors.ink,
        tonalElevation = 0.dp,
        scrimColor = colors.scrim.copy(alpha = if (colors.isDark) 0.6f else 0.32f),
        dragHandle = {
            val handle = stringResource(R.string.sheet_handle)
            Box(
                Modifier
                    .padding(vertical = Space.s12)
                    .size(width = HandleWidth, height = Space.s4)
                    .background(colors.surfacePressed, AromaTheme.shapes.round)
                    .semantics { contentDescription = handle },
            )
        },
    ) {
        // A sheet is a window of its own: nothing in it travels to the next screen.
        CompositionLocalProvider(
            LocalTonalFill provides colors.surfaceOnBar,
            LocalSharedTransition provides null,
            LocalScreenTransition provides null,
        ) {
            Column(Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s4, bottom = Space.s16)) {
                Text(
                    text = title,
                    style = titleStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (meta != null) Box(Modifier.padding(top = Space.s4)) { meta() }
            }
            content()
        }
    }
}

private val HandleWidth = 32.dp

/** A sheet that opens at its full height, so its footer is never below the screen's edge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberFullSheetState(): SheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))

/** A sheet's one action, at its foot. */
@Composable
fun SheetFooter(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.padding(horizontal = PageMargin, vertical = Space.s16)) { content() }
}

/**
 * Lets the sheet slide away before [then] changes what is underneath, so the change happens in
 * plain sight. A second tap while the sheet is already leaving does nothing, so nothing runs twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun CoroutineScope.hideThen(sheet: SheetState, then: () -> Unit) {
    if (sheet.targetValue == SheetValue.Hidden) return
    launch { sheet.hide() }.invokeOnCompletion { then() }
}

/** A 56 dp action of a sheet: a 20 dp glyph in second ink on the edge and its label after it. */
@Composable
fun SheetAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val colors = AromaTheme.colors
    val color = if (destructive) colors.error else colors.ink
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ChoiceHeight)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = PageMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (destructive) color else colors.inkSecondary,
            modifier = Modifier.size(Glyph.control),
        )
        Spacer(Modifier.width(Space.s16))
        Text(label, style = AromaTheme.type.body, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** An option of a list to choose from, on a sheet: the chosen one carries a sage check at its end. */
@Composable
fun ChoiceRow(label: String, chosen: Boolean, modifier: Modifier = Modifier, description: String? = null) {
    val check by animateFloatAsState(if (chosen) 1f else 0f, Motion.edge(), label = "check")
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ChoiceHeight)
                .padding(horizontal = PageMargin, vertical = Space.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = label, style = AromaTheme.type.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (description != null) Text(description, style = AromaTheme.type.bodySmall)
        }
        Spacer(Modifier.width(Space.s16))
        Icon(
            painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = AromaTheme.colors.accent,
            modifier =
                Modifier
                    .size(Glyph.control)
                    .graphicsLayer { alpha = check },
        )
    }
}

private val ChoiceHeight = 56.dp

/**
 * A menu on the bars' paper with the soft shadow of what floats. The popup is padded by the
 * shadow's reach, and moved back by as much, so the shadow is not cut off at the popup's edge.
 */
@Composable
fun AromaDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AromaTheme.colors
    val shape = AromaTheme.shapes.box
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(-ShadowRoom, -ShadowRoom),
        shape = RectangleShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier,
    ) {
        Column(
            Modifier
                .padding(ShadowRoom)
                .layeredShadow(AromaTheme.shadows.menu, shape)
                .clip(shape)
                .background(colors.bar)
                .widthIn(min = MenuWidth)
                .padding(vertical = Space.s8),
            content = content,
        )
    }
}

/** How far a menu's shadow reaches beyond it, and how wide a menu is at least. */
private val ShadowRoom = 24.dp
private val MenuWidth = 200.dp

/**
 * A row of a menu with an optional 20 dp glyph; [destructive] rows read in red. In a menu of
 * choices, the [selected] one carries a sage check at its end.
 */
@Composable
fun AromaMenuItem(
    text: String,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    selected: Boolean = false,
) {
    val colors = AromaTheme.colors
    val color = if (destructive) colors.error else colors.ink
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MenuRowHeight)
                .clickable(role = Role.Button, onClick = onClick)
                .then(if (selected) Modifier.semantics { this.selected = true } else Modifier)
                .padding(horizontal = Space.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (destructive) color else colors.inkSecondary,
                modifier = Modifier.size(Glyph.control),
            )
            Spacer(Modifier.width(Space.s12))
        }
        Text(
            text,
            style = AromaTheme.type.body,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Spacer(Modifier.width(Space.s12))
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(Glyph.control),
            )
        }
    }
}

private val MenuRowHeight = 48.dp

/**
 * A dialog on the bars' paper with text actions: confirm in sage, or in red where it destroys
 * something, and a way back out where there is a [dismissLabel].
 */
@Composable
fun AromaAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    text: String? = null,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    val colors = AromaTheme.colors
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = AromaTheme.shapes.box,
        containerColor = colors.bar,
        titleContentColor = colors.ink,
        textContentColor = colors.inkSecondary,
        tonalElevation = 0.dp,
        title = { Text(title, style = AromaTheme.type.labelLarge) },
        text =
            content?.let { body -> { CompositionLocalProvider(LocalTonalFill provides colors.surfaceOnBar) { body() } } }
                ?: text?.let { { Text(it, style = AromaTheme.type.body, color = colors.inkSecondary) } },
        confirmButton = { DialogAction(confirmLabel, onConfirm, if (destructive) colors.error else colors.accent, confirmEnabled) },
        dismissButton = dismissLabel?.let { { DialogAction(it, onDismissRequest, colors.accent, enabled = true) } },
    )
}

@Composable
private fun DialogAction(text: String, onClick: () -> Unit, color: Color, enabled: Boolean) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(AromaTheme.shapes.round)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s12),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AromaTheme.type.label, color = if (enabled) color else color.copy(alpha = 0.38f))
    }
}

/** Pull to refresh with an indicator on the bars' paper and a sage arc, the same on every page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AromaPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                containerColor = AromaTheme.colors.bar,
                color = AromaTheme.colors.accent,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        content = content,
    )
}
