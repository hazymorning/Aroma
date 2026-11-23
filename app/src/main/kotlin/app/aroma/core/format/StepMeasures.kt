package app.aroma.core.format

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Converts temperatures and lengths in the text of a cooking step: "350 °F" becomes "175 °C" and
 * a "23 cm" springform a "9 inch" one. Only certain matches change: a bare "350°" or "350 degrees"
 * may be either scale, "2 c" is two cups, and gas marks have no scale. A step that gives its
 * temperatures or lengths in both systems already, as "180 °C / 350 °F" does, keeps them.
 */
object StepMeasures {
    private const val NUMBER = """\d+\s+\d+/\d+|\d+/\d+|(?:\d+\s*)?[½¼¾⅓⅔⅛⅜⅝⅞]|\d+(?:[.,]\d+)?"""
    private const val DEGREES = """\d{1,3}(?:[.,]\d)?"""
    private const val CM_PER_INCH = 2.54

    /** Ovens start here; below, meat and sugar need single degrees. */
    private const val OVEN_CELSIUS = 120
    private const val OVEN_FAHRENHEIT = 250

    /** A low and an optional high temperature, what comes before the scale ("°", "degrees ", "Grad "), and the scale. */
    private val temperature =
        Regex(
            """(?<![\d.,])($DEGREES)(?:\s*(?:-|–|to|bis)\s*($DEGREES))?\s*([°º˚]\s*|(?:degrees?|grad)\s+)?""" +
                """(celsius|fahrenheit|grad|[CF℃℉])(?!\p{L})""",
            RegexOption.IGNORE_CASE,
        )

    /** One to three numbers ("9 x 13", "2-3") and a metric unit, which is empty for inches. */
    private val length =
        Regex(
            """(?<![\d.,/])((?:$NUMBER)(?:\s*(?:-\s*by|x|×|by|-|–|to|bis)\s*(?:$NUMBER)){0,2})""" +
                """(?:\s*-?\s*(?:inch(?:es)?|zoll)|-in\.|″|\s*-?\s*(cm|mm))(?!\p{L})""",
            RegexOption.IGNORE_CASE,
        )
    private val number = Regex(NUMBER)
    private val link = Regex("""https?://\S+""")

    /** A temperature or length in a step, in [system], and how the other system writes it. */
    private class Mention(val range: IntRange, val system: MeasureSystem, val converted: String)

    fun convert(text: String, system: MeasureSystem, locale: Locale): String {
        if (system == MeasureSystem.AsWritten) return text
        val links = link.findAll(text).map { it.range }.toList()
        val changes =
            listOf(temperatures(text), lengths(text, locale))
                .filter { mentions -> mentions.distinctBy { it.system }.size == 1 }
                .flatten()
                .filter { mention -> mention.system != system && links.none { mention.range.first in it } }
        return buildString {
            var position = 0
            changes.sortedBy { it.range.first }.forEach {
                append(text, position, it.range.first).append(it.converted)
                position = it.range.last + 1
            }
            append(text, position, text.length)
        }
    }

    private fun temperatures(text: String): List<Mention> =
        temperature
            .findAll(text)
            .mapNotNull { match ->
                val (low, high, before, scale) = match.destructured
                val degrees = listOf(low, high).filter { it.isNotEmpty() }.mapNotNull(LeadingQuantityParser::amount)
                // A bare "C" may be a cup, unless the number is an oven's.
                val bareLetter = before.isEmpty() && scale.first().isLetter() && scale.length == 1
                if ((bareLetter && degrees.first() < 100) || isNegative(text, match.range.first) || isAngle(text, match.range, scale)) {
                    return@mapNotNull null
                }
                val celsius = scale.first().lowercaseChar() in "c℃g"
                val converted = degrees.map { if (celsius) toFahrenheit(it) else toCelsius(it) }.distinct()
                val system = if (celsius) MeasureSystem.Metric else MeasureSystem.US
                Mention(match.range, system, converted.joinToString("–", postfix = if (celsius) " °F" else " °C"))
            }.toList()

    /** "Das Blech um 180 Grad drehen" turns the tray: a bare "Grad" is an angle when something turns by it. */
    private fun isAngle(text: String, range: IntRange, scale: String): Boolean =
        scale.equals("grad", ignoreCase = true) &&
            (turnedBy.containsMatchIn(text.substring(0, range.first)) || turning.containsMatchIn(text.substring(range.last + 1)))

    private val turnedBy = Regex("""\bum\s+$""", RegexOption.IGNORE_CASE)
    private val turning = Regex("""^\s*(?:\p{L}+\s+){0,2}(?:drehen|wenden|gedreht|gewendet)""", RegexOption.IGNORE_CASE)

    /** "-18 °C" in a freezer, while "180 °C-200 °C" is a range. */
    private fun isNegative(text: String, start: Int): Boolean {
        val sign = text.getOrNull(start - 1)
        return (sign == '-' || sign == '−') && text.getOrNull(start - 2)?.isLetterOrDigit() != true
    }

    private fun toCelsius(fahrenheit: Double) = rounded((fahrenheit - 32) * 5 / 9, if (fahrenheit >= OVEN_FAHRENHEIT) 5 else 1)

    private fun toFahrenheit(celsius: Double) = rounded(celsius * 9 / 5 + 32, if (celsius >= OVEN_CELSIUS) 25 else 1)

    private fun rounded(value: Double, step: Int) = (value / step).roundToInt() * step

    private fun lengths(text: String, locale: Locale): List<Mention> =
        length
            .findAll(text)
            .mapNotNull { match ->
                val (numbers, metricUnit) = match.destructured
                val sizes = number.findAll(numbers).toList().map { LeadingQuantityParser.amount(it.value) ?: return@mapNotNull null }
                val (converted, unit) =
                    when {
                        metricUnit.isEmpty() -> metricLengths(sizes.map { it * CM_PER_INCH }, locale)
                        metricUnit.equals("mm", ignoreCase = true) -> inchLengths(sizes.map { it / 10 }, locale)
                        else -> inchLengths(sizes, locale)
                    }
                val system = if (metricUnit.isEmpty()) MeasureSystem.US else MeasureSystem.Metric
                val each = converted.iterator()
                Mention(match.range, system, numbers.replace(number) { each.next() } + " $unit")
            }.toList()

    /** Millimetres for thin slices, half centimetres for pieces, whole ones for pans: 9 inch is 23 cm. */
    private fun metricLengths(centimetres: List<Double>, locale: Locale): Pair<List<String>, String> {
        val millimetres = centimetres.max() < 1
        val values =
            centimetres.map {
                when {
                    millimetres -> (it * 10).roundToInt().coerceAtLeast(1).toDouble()
                    it < 5 -> ((it * 2).roundToInt() / 2.0).coerceAtLeast(0.5)
                    else -> it.roundToInt().toDouble()
                }
            }
        return values.map { shown(it, fraction = false, locale) } to if (millimetres) "mm" else "cm"
    }

    private fun inchLengths(centimetres: List<Double>, locale: Locale): Pair<List<String>, String> =
        centimetres.map { shown(inches(it), fraction = true, locale) } to "inch"

    /** The nearest eighth, quarter or half inch for pieces; pans come in whole inches: 23 cm is 9 inch. */
    private fun inches(centimetres: Double): Double {
        val inches = centimetres / CM_PER_INCH
        if (inches >= 5) return inches.roundToInt().toDouble()
        val whole = floor(inches)
        return listOf(0.0, 0.125, 0.25, 0.5, 0.75, 1.0).map { whole + it }.filter { it > 0 }.minBy { abs(it - inches) }
    }

    private fun shown(value: Double, fraction: Boolean, locale: Locale) =
        Quantities.display(value, fraction, locale)?.toPlainText().orEmpty()
}
