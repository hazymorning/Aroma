package app.kochi.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.kochi.core.format.DisplayQuantity
import app.kochi.core.format.IngredientLine

private val fractionStyle = SpanStyle(fontFeatureSettings = "frac")
private val tabularStyle = SpanStyle(fontFeatureSettings = "tnum")

/**
 * Builds "2 ½ EL" with real typographic fractions. The `frac` feature is applied to the
 * fraction only, otherwise whole numbers would be set as numerators as well.
 */
fun AnnotatedString.Builder.appendQuantity(quantity: DisplayQuantity) {
    when (quantity) {
        is DisplayQuantity.Decimal -> {
            withStyle(tabularStyle) { append(quantity.text) }
        }

        is DisplayQuantity.Fraction -> {
            if (quantity.whole > 0) {
                withStyle(tabularStyle) { append(quantity.whole.toString()) }
                append(' ')
            }
            withStyle(fractionStyle) { append("${quantity.numerator}/${quantity.denominator}") }
        }
    }
}

fun measureText(line: IngredientLine): AnnotatedString =
    buildAnnotatedString {
        line.amount?.let { appendQuantity(it) }
        line.amountUpper?.let {
            append('–')
            appendQuantity(it)
        }
        if (!line.unit.isNullOrBlank()) {
            if (line.amount != null) append(' ')
            append(line.unit)
        }
    }
