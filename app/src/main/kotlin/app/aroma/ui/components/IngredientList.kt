package app.aroma.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.core.format.IngredientLine
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.tabular

/** An ingredient as shown in a list, optionally starting a new group. */
data class IngredientRow(val section: String?, val line: IngredientLine)

/**
 * Ingredients as a cookbook sets them: open on the page, no lines and no boxes. The amounts stand
 * in a column of their own, right-aligned in sage with tabular figures, as wide as the widest amount
 * and at least 56 dp; the names follow 16 dp after it. An ingredient without an amount stands in the
 * names' column. A group's name, in sage italics, stands 24 dp above its first line and 8 dp above
 * it. A tap ticks a line off ([onToggle]): it fades to tertiary ink. With
 * [checks], a tick stands before each line, as in cook mode.
 */
@Composable
fun IngredientList(
    rows: List<IngredientRow>,
    modifier: Modifier = Modifier,
    checked: Set<Int> = emptySet(),
    onToggle: ((Int) -> Unit)? = null,
    checks: Boolean = false,
) {
    val amountStyle = AromaTheme.type.body.tabular()
    val textMeasurer = rememberTextMeasurer()
    val amounts = remember(rows) { rows.map { measureText(it.line) } }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widest = remember(amounts, amountStyle) { amounts.maxOfOrNull { textMeasurer.measure(it, amountStyle).size.width } ?: 0 }
        val anyAmount = amounts.any { it.isNotEmpty() }
        val column =
            if (anyAmount) with(density) { widest.toDp() }.coerceAtLeast(AmountColumn).coerceAtMost(maxWidth * AMOUNT_SHARE) else 0.dp
        Column {
            rows.forEachIndexed { index, row ->
                if (!row.section.isNullOrBlank()) {
                    Text(
                        text = row.section,
                        style = AromaTheme.type.groupLabel,
                        modifier =
                            Modifier
                                .padding(top = if (index == 0) 0.dp else Space.s24, bottom = Space.s8)
                                .semantics { heading() },
                    )
                }
                IngredientLineRow(
                    amount = amounts[index],
                    row = row,
                    amountWidth = column,
                    checked = index in checked,
                    onToggle = onToggle?.let { { it(index) } },
                    check = checks,
                )
            }
        }
    }
}

/** The least width of the amounts' column, and the most of the line it may take. */
private val AmountColumn = 56.dp
private const val AMOUNT_SHARE = 0.42f

@Composable
private fun IngredientLineRow(
    amount: AnnotatedString,
    row: IngredientRow,
    amountWidth: Dp,
    checked: Boolean,
    onToggle: (() -> Unit)?,
    check: Boolean,
) {
    val colors = AromaTheme.colors
    val haptics = LocalHapticFeedback.current
    val tap =
        if (onToggle != null) {
            Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = {
                haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onToggle()
            })
        } else {
            Modifier
        }
    val amountColor by animateColorAsState(if (checked) colors.inkTertiary else colors.accent, Motion.state(), label = "amount")
    val textColor by animateColorAsState(if (checked) colors.inkTertiary else colors.ink, Motion.state(), label = "ingredient")
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (check) RowTarget else RowHeight)
                .then(tap)
                .padding(vertical = Space.s8),
        verticalAlignment = if (check) Alignment.CenterVertically else Alignment.Top,
    ) {
        if (check) {
            CheckCircle(checked)
            Spacer(Modifier.width(Space.s12))
        }
        if (amountWidth > 0.dp) {
            Text(
                text = amount,
                style = AromaTheme.type.body.tabular(),
                color = amountColor,
                textAlign = TextAlign.End,
                modifier = Modifier.width(amountWidth),
            )
            Spacer(Modifier.width(Space.s16))
        }
        Text(
            text = ingredientText(row, colors.inkSecondary.takeIf { !checked } ?: colors.inkTertiary),
            style = AromaTheme.type.body,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A line is at least 40 dp tall, as a cookbook sets its list; with a tick before it, as tall as the tick's target. */
private val RowHeight = 40.dp
private val RowTarget = 48.dp

private fun ingredientText(row: IngredientRow, noteColor: Color): AnnotatedString =
    buildAnnotatedString {
        append(row.line.text)
        row.line.note?.let { note ->
            if (row.line.text.isNotEmpty()) append(", ")
            withStyle(SpanStyle(color = noteColor)) { append(note) }
        }
    }
