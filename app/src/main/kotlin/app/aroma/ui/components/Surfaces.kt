package app.aroma.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.RowHeight
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.extras
import app.aroma.ui.theme.isDarkScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

private val HandleWidth = 32.dp

/**
 * A bottom sheet on paper, with a fine handle and no tonal tint, that opens at its full height under
 * its [title], with a [meta] line below it where the sheet needs one. The content is already clear
 * of the navigation bar and the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AromaBottomSheet(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberFullSheetState(),
    meta: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = extras.raised,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = if (isDarkScheme) 0.60f else 0.32f),
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = Space.s)
                    .size(width = HandleWidth, height = Space.xxs)
                    .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        },
    ) {
        Column(Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.xs, bottom = Space.m)) {
            Text(
                text = title,
                style = AromaType.SectionTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (meta != null) Box(Modifier.padding(top = Space.xxs)) { meta() }
        }
        content()
    }
}

/** A sheet that opens at its full height, so its footer is never below the screen's edge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberFullSheetState(): SheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))

/** A sheet's one action, at its foot. */
@Composable
fun SheetFooter(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.padding(horizontal = ScreenMargin, vertical = Space.m)) { content() }
}

/** The lift of a menu above the page. */
private val MenuShadow = 8.dp

/** A menu on paper with a soft shadow. */
@Composable
fun AromaDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        containerColor = extras.raised,
        tonalElevation = 0.dp,
        shadowElevation = MenuShadow,
        content = content,
    )
}

/**
 * A menu row with a 20 dp line icon; [destructive] items read in the error colour. In a menu of
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
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(text, style = AromaType.Body, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon =
            icon?.let {
                {
                    Icon(
                        painterResource(it),
                        contentDescription = null,
                        tint = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Glyph.m),
                    )
                }
            },
        trailingIcon =
            if (selected) {
                {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Glyph.m),
                    )
                }
            } else {
                null
            },
        onClick = onClick,
        modifier = if (selected) modifier.semantics { this.selected = true } else modifier,
    )
}

/**
 * A dialog on paper with quiet text actions: confirm in sage, or in red when it destroys something,
 * and a way back out where there is a [dismissLabel].
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
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = extras.raised,
        tonalElevation = 0.dp,
        title = { Text(title, style = AromaType.SectionTitle) },
        text =
            content ?: text?.let {
                { Text(it, style = AromaType.Body, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
        confirmButton = {
            ConfirmLink(
                confirmLabel,
                onConfirm,
                if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                confirmEnabled,
            )
        },
        dismissButton = dismissLabel?.let { { QuietLink(it, onDismissRequest, modifier = Modifier.padding(end = Space.m)) } },
    )
}

@Composable
private fun ConfirmLink(text: String, onClick: () -> Unit, color: Color, enabled: Boolean) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(text, style = AromaType.Label, color = if (enabled) color else color.copy(alpha = 0.38f))
    }
}

/** Pull to refresh with a paper indicator and a sage arc, the same on every tab. */
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
                containerColor = extras.raised,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        content = content,
    )
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

/** A 56 dp action row of a sheet: a 24 dp glyph on the margin and its label 16 dp after it. */
@Composable
fun SheetAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(RowHeight.oneLine)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = ScreenMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Glyph.l),
        )
        Spacer(Modifier.width(Space.m))
        Text(label, style = AromaType.Body, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
