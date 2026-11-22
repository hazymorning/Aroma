package app.kochi.ui.components

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.kochi.core.format.IngredientLine

/** An ingredient as shown in a list, optionally starting a new section. */
data class IngredientRow(val section: String?, val line: IngredientLine, val linkedRecipe: String? = null)

/**
 * Ingredients as a two-column table. The measure column is as wide as its widest entry
 * (capped), so amounts line up the way they do in a printed cookbook.
 */
@Composable
fun IngredientList(
    rows: List<IngredientRow>,
    modifier: Modifier = Modifier,
    checked: Set<Int> = emptySet(),
    onToggle: ((Int) -> Unit)? = null,
) {
    val measureStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    val textMeasurer = rememberTextMeasurer()
    val measures = remember(rows) { rows.map { measureText(it.line) } }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widest =
            remember(measures, measureStyle) {
                measures.maxOfOrNull { textMeasurer.measure(it, measureStyle).size.width } ?: 0
            }
        val columnWidth = with(density) { widest.toDp() }.coerceAtMost(maxWidth * 0.42f)
        Column {
            rows.forEachIndexed { index, row ->
                if (!row.section.isNullOrBlank()) {
                    Text(
                        text = row.section,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = if (index == 0) 4.dp else 20.dp, bottom = 4.dp),
                    )
                } else if (index > 0) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
                IngredientRowItem(
                    measure = measures[index],
                    row = row,
                    measureWidth = columnWidth,
                    checked = index in checked,
                    onToggle = onToggle?.let { { it(index) } },
                )
            }
        }
    }
}

@Composable
private fun IngredientRowItem(
    measure: AnnotatedString,
    row: IngredientRow,
    measureWidth: androidx.compose.ui.unit.Dp,
    checked: Boolean,
    onToggle: (() -> Unit)?,
) {
    val rowModifier =
        if (onToggle != null) {
            Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
        } else {
            Modifier
        }
    val decoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
    Row(
        modifier =
            rowModifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .alpha(if (checked) 0.45f else 1f)
                .padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (measureWidth.value > 0f) {
            Text(
                text = measure,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, textDecoration = decoration),
                modifier = Modifier.width(measureWidth),
            )
            Spacer(Modifier.width(16.dp))
        }
        Text(
            text = ingredientText(row, MaterialTheme.colorScheme.onSurfaceVariant),
            style = MaterialTheme.typography.bodyLarge.copy(textDecoration = decoration),
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
