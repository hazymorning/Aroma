package app.aroma.core.format

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** A quantity prepared for display, either as a (mixed) fraction or as a decimal number. */
sealed interface DisplayQuantity {
    data class Fraction(val whole: Long, val numerator: Int, val denominator: Int) : DisplayQuantity

    data class Decimal(val text: String) : DisplayQuantity

    /** Plain text with Unicode vulgar fractions, used where no rich text is available. */
    fun toPlainText(): String =
        when (this) {
            is Decimal -> {
                text
            }

            is Fraction -> {
                val glyph = VULGAR_FRACTIONS[numerator to denominator] ?: "$numerator⁄$denominator"
                if (whole > 0) "$whole $glyph" else glyph
            }
        }
}

object Quantities {
    private val commonFractions = listOf(1 to 8, 1 to 4, 1 to 3, 3 to 8, 1 to 2, 5 to 8, 2 to 3, 3 to 4, 7 to 8)
    private val halves = listOf(1 to 2)
    private const val TOLERANCE = 0.03

    /** The least amount two decimals show. */
    private const val SMALLEST = 0.01

    /**
     * Formats [value] the way a cook would write it down. Large amounts are rounded,
     * because "133 1/3 g" is never what anyone measures. A pinch scaled down never reads as 0:
     * too small for a fraction, it is a decimal of at least 0.01.
     */
    fun display(value: Double, preferFraction: Boolean, locale: Locale): DisplayQuantity? {
        if (value <= 0.0 || value.isNaN() || value.isInfinite()) return null
        if (value >= 100) return DisplayQuantity.Decimal(formatDecimal(value.roundToLong().toDouble(), 0, locale))
        if (preferFraction) {
            val candidates = if (value >= 10) halves else commonFractions
            fractionOf(value, candidates)?.let { return it }
            if (value >= 10) return DisplayQuantity.Decimal(formatDecimal(value.roundToLong().toDouble(), 0, locale))
        }
        val decimals = if (value >= 10) 1 else 2
        return DisplayQuantity.Decimal(formatDecimal(value.coerceAtLeast(SMALLEST), decimals, locale))
    }

    private fun fractionOf(value: Double, candidates: List<Pair<Int, Int>>): DisplayQuantity? {
        var whole = floor(value).toLong()
        val rest = value - whole
        if (rest < TOLERANCE) return if (whole > 0) DisplayQuantity.Decimal(whole.toString()) else null
        if (rest > 1 - TOLERANCE) return DisplayQuantity.Decimal((whole + 1).toString())
        val match = candidates.minByOrNull { (n, d) -> abs(rest - n.toDouble() / d) } ?: return null
        if (abs(rest - match.first.toDouble() / match.second) > TOLERANCE) return null
        return DisplayQuantity.Fraction(whole, match.first, match.second)
    }

    private fun formatDecimal(value: Double, maxDecimals: Int, locale: Locale): String {
        val format =
            NumberFormat.getNumberInstance(locale).apply {
                maximumFractionDigits = maxDecimals
                minimumFractionDigits = 0
                isGroupingUsed = false
            }
        return format.format(value)
    }
}

internal val VULGAR_FRACTIONS =
    mapOf(
        1 to 2 to "½",
        1 to 3 to "⅓",
        2 to 3 to "⅔",
        1 to 4 to "¼",
        3 to 4 to "¾",
        1 to 8 to "⅛",
        3 to 8 to "⅜",
        5 to 8 to "⅝",
        7 to 8 to "⅞",
    )

internal val GLYPH_VALUES: Map<Char, Double> =
    mapOf(
        '½' to 0.5,
        '⅓' to 1.0 / 3,
        '⅔' to 2.0 / 3,
        '¼' to 0.25,
        '¾' to 0.75,
        '⅛' to 0.125,
        '⅜' to 0.375,
        '⅝' to 0.625,
        '⅞' to 0.875,
        '⅕' to 0.2,
        '⅖' to 0.4,
        '⅗' to 0.6,
        '⅘' to 0.8,
        '⅙' to 1.0 / 6,
        '⅚' to 5.0 / 6,
    )
