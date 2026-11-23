package app.aroma.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.aroma.core.format.IngredientLine
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.CardPadding
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget
import app.aroma.ui.theme.tabular

/** An ingredient as shown in a list, optionally starting a new section. */
data class IngredientRow(val section: String?, val line: IngredientLine)

/**
 * Ingredients as a two-column table. The measure column is as wide as its widest entry (capped),
 * so amounts line up the way they do in a printed cookbook. With [onCards], each section is a
 * [SoftCard] under its [GroupLabel], and the list keeps the page margins itself; without, the rows
 * run plainly between hairlines, as in a sheet.
 */
@Composable
fun IngredientList(
    rows: List<IngredientRow>,
    modifier: Modifier = Modifier,
    checked: Set<Int> = emptySet(),
    onToggle: ((Int) -> Unit)? = null,
    onCards: Boolean = false,
) {
    val measureStyle = AromaType.BodyStrong.tabular()
    val textMeasurer = rememberTextMeasurer()
    val measures = remember(rows) { rows.map { measureText(it.line) } }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widest =
            remember(measures, measureStyle) {
                measures.maxOfOrNull { textMeasurer.measure(it, measureStyle).size.width } ?: 0
            }
        val textWidth = if (onCards) maxWidth - (ScreenMargin + CardPadding) * 2 else maxWidth
        val columnWidth = with(density) { widest.toDp() }.coerceAtMost(textWidth * 0.42f)
        val item: @Composable (Int) -> Unit = { index ->
            IngredientRowItem(
                measure = measures[index],
                row = rows[index],
                measureWidth = columnWidth,
                checked = index in checked,
                onToggle = onToggle?.let { { it(index) } },
                modifier = if (onCards) Modifier.padding(horizontal = CardPadding) else Modifier,
            )
        }
        if (onCards) {
            val sections = remember(rows) { rows.indices.groupConsecutiveBy { !rows[it].section.isNullOrBlank() } }
            Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
                sections.forEach { indexes ->
                    CardGroup(rows[indexes.first()].section) {
                        indexes.forEachIndexed { position, index ->
                            if (position > 0) Hairline(Modifier.padding(horizontal = CardPadding))
                            item(index)
                        }
                    }
                }
            }
        } else {
            Column {
                rows.forEachIndexed { index, row ->
                    if (!row.section.isNullOrBlank()) {
                        Text(
                            text = row.section,
                            style = AromaType.Label,
                            color = MaterialTheme.colorScheme.primary,
                            modifier =
                                Modifier
                                    .padding(top = if (index == 0) 0.dp else Space.l, bottom = Space.xs)
                                    .semantics { heading() },
                        )
                    } else if (index > 0) {
                        Hairline()
                    }
                    item(index)
                }
            }
        }
    }
}

/** Splits indexes into runs that each start where [startsRun] is true (and at the first index). */
private fun IntRange.groupConsecutiveBy(startsRun: (Int) -> Boolean): List<List<Int>> =
    fold(mutableListOf<MutableList<Int>>()) { runs, index ->
        if (runs.isEmpty() || startsRun(index)) runs.add(mutableListOf(index)) else runs.last().add(index)
        runs
    }

@Composable
private fun IngredientRowItem(
    measure: AnnotatedString,
    row: IngredientRow,
    measureWidth: androidx.compose.ui.unit.Dp,
    checked: Boolean,
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val rowModifier =
        if (onToggle != null) {
            Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
        } else {
            Modifier
        }
    val decoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
    val fade by animateFloatAsState(if (checked) 0.4f else 1f, tween(Motion.STATE), label = "ingredientChecked")
    // The amount in sage; once ticked off it is muted with the rest of the row.
    val measureColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        tween(Motion.STATE),
        label = "ingredientMeasure",
    )
    Row(
        modifier =
            rowModifier
                .then(modifier)
                .fillMaxWidth()
                .heightIn(min = TouchTarget)
                .graphicsLayer { alpha = fade }
                .padding(vertical = Space.s),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (measureWidth.value > 0f) {
            Text(
                text = measure,
                style = AromaType.BodyStrong.copy(textDecoration = decoration).tabular(),
                color = measureColor,
                modifier = Modifier.width(measureWidth),
            )
            Spacer(Modifier.width(Space.m))
        }
        Text(
            text = ingredientText(row, MaterialTheme.colorScheme.onSurfaceVariant),
            style = AromaType.Body.copy(textDecoration = decoration),
            modifier = Modifier.weight(1f),
        )
    }
}

private fun ingredientText(row: IngredientRow, noteColor: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        append(row.line.text)
        row.line.note?.let { note ->
            if (row.line.text.isNotEmpty()) append(", ")
            withStyle(SpanStyle(color = noteColor)) { append(note) }
        }
    }
