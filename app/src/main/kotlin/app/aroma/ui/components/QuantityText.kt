package app.aroma.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.aroma.core.format.DisplayQuantity
import app.aroma.core.format.IngredientLine

private val tabularStyle = SpanStyle(fontFeatureSettings = "tnum")

/** What holds a number and its unit together on screen, "500 g": a narrow no-break space. */
const val NARROW_NO_BREAK_SPACE = "\u202F"

/**
 * "2 ½ EL": tabular figures, and fractions as the characters they are, which Aroma's fonts draw; a
 * whole number and its fraction are held together by a narrow space.
 */
fun AnnotatedString.Builder.appendQuantity(quantity: DisplayQuantity) {
    withStyle(tabularStyle) { append(quantity.toPlainText().replace(" ", NARROW_NO_BREAK_SPACE)) }
}

/** The amount and unit of [line] as the amounts' column and the steps set them: "250 g", "1–2 EL". */
fun measureText(line: IngredientLine): AnnotatedString =
    buildAnnotatedString {
        line.amount?.let { appendQuantity(it) }
        line.amountUpper?.let {
            append('–')
            appendQuantity(it)
        }
        if (!line.unit.isNullOrBlank()) {
            if (line.amount != null) append(NARROW_NO_BREAK_SPACE)
            append(line.unit)
        }
    }
