package app.aroma.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.aroma.ui.theme.Lines
import app.aroma.ui.theme.extras
import app.aroma.ui.theme.isDarkScheme

private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/** A bottom sheet on paper, with a fine handle and no tonal tint. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AromaBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
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
                    .padding(vertical = 12.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(extras.hairlineStrong, CircleShape),
            )
        },
        content = content,
    )
}

/** A menu on paper with a hairline edge and a soft shadow. */
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
        shadowElevation = 8.dp,
        border = BorderStroke(Lines.hairline, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

/** A menu row with a 20 dp line icon; [destructive] items read in the error colour. */
@Composable
fun AromaMenuItem(
    text: String,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge, color = color) },
        leadingIcon =
            icon?.let {
                {
                    Icon(
                        painterResource(it),
                        contentDescription = null,
                        tint = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        onClick = onClick,
        modifier = modifier,
    )
}

/** A dialog on paper with quiet text actions: confirm in sage, or in red when it destroys something. */
@Composable
fun AromaAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    destructive: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = extras.raised,
        tonalElevation = 0.dp,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text =
            content ?: text?.let {
                { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
        confirmButton = {
            ConfirmLink(confirmLabel, onConfirm, if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        },
        dismissButton = { QuietLink(dismissLabel, onDismissRequest, modifier = Modifier.padding(end = 16.dp)) },
    )
}

@Composable
private fun ConfirmLink(text: String, onClick: () -> Unit, color: Color) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}
