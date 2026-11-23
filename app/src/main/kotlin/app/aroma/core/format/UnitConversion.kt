package app.aroma.core.format

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** An amount in another unit: [amount] up to [upper] [unit], shown as a fraction or as a decimal. */
internal data class Conversion(
    val amount: Double,
    val upper: Double?,
    val unit: String,
    val fraction: Boolean,
)

/**
 * Converts amounts the way cooks measure. Metric amounts are rounded to steps a scale or jug
 * shows; US amounts to the closest cup, spoon or ounce fraction within [CLOSE_ENOUGH]. An amount
 * that has none stays as written, as does one in the target system already: never worse than
 * the recipe.
 */
internal object UnitConversion {
    /** A little over 5 %, so that 30 g still reads as 1 oz. */
    private const val CLOSE_ENOUGH = 0.06

    private val quarters = listOf(0.25, 0.5, 0.75)
    private val cupFractions = listOf(0.25, 1.0 / 3, 0.5, 2.0 / 3, 0.75)
    private val spoonFractions = listOf(0.125, 0.25, 0.5, 0.75)

    /**
     * The units of a US kitchen, largest first, with the fractions and counts cooks use for each.
     * Fluid ounces come last, for what no cup or spoon measures, like 200 ml: every US measuring
     * jug is marked in them.
     */
    private val usVolumes =
        listOf(
            UsUnit("qt", "qt", 4 * CUP, quarters, min = 1.0),
            UsUnit("cup", "cups", CUP, cupFractions, min = 0.25),
            UsUnit("tbsp", "tbsp", TABLESPOON, listOf(0.5), min = 1.0, max = 8.0),
            UsUnit("tsp", "tsp", TABLESPOON / 3, spoonFractions, min = 0.125, max = 6.0),
            UsUnit("fl oz", "fl oz", FLUID_OUNCE, listOf(0.5), min = 1.0),
        )
    private val usWeights =
        listOf(
            UsUnit("lb", "lb", POUND, quarters, min = 1.0),
            UsUnit("oz", "oz", OUNCE, quarters, min = 0.25),
        )

    /**
     * [amount] up to [upper] of [measure] in [system]. In metric, cups of a [food] whose weight is
     * known become grams, as metric kitchens weigh solids; other volumes become millilitres.
     * Weights never become cups: they are exact, while a cup of flour varies by a fifth with how
     * it is filled.
     */
    fun convert(
        amount: Double,
        upper: Double?,
        measure: Measure,
        food: CupWeight?,
        system: MeasureSystem,
    ): Conversion? =
        when {
            amount <= 0 || measure.system == system -> null
            system == MeasureSystem.Metric -> toMetric(amount, upper, measure, food)
            system == MeasureSystem.US -> toUs(amount, upper, measure)
            else -> null
        }

    private fun toMetric(amount: Double, upper: Double?, measure: Measure, food: CupWeight?): Conversion? {
        val grams = food?.grams?.takeIf { measure.dimension == Dimension.Volume }
        val size = grams?.let { measure.size / CUP * it } ?: measure.size
        val low = roundMetric(amount * size).takeIf { it > 0 } ?: return null
        val high = upper?.let { roundMetric(it * size) }?.takeIf { it > low }
        val mass = grams != null || measure.dimension == Dimension.Mass
        val large = low >= 1000
        val unit =
            when {
                mass && large -> "kg"
                mass -> "g"
                large -> "l"
                else -> "ml"
            }
        val divisor = if (large) 1000.0 else 1.0
        return Conversion(low / divisor, high?.div(divisor), unit, fraction = false)
    }

    /** Single grams for yeast and salt, then steps of 5, 10, 25 and 50: 1 lb is 450 g, 1 cup 240 ml. */
    private fun roundMetric(value: Double): Double {
        val step =
            when {
                value < 10 -> 1.0
                value < 250 -> 5.0
                value < 500 -> 10.0
                value < 1000 -> 25.0
                else -> 50.0
            }
        return (value / step).roundToLong() * step
    }

    /** Both ends of a range in the first unit that measures both. */
    private fun toUs(amount: Double, upper: Double?, measure: Measure): Conversion? {
        val units = if (measure.dimension == Dimension.Mass) usWeights else usVolumes
        return units.firstNotNullOfOrNull { unit ->
            val low = unit.nearest(amount * measure.size) ?: return@firstNotNullOfOrNull null
            val high = upper?.let { unit.nearest(it * measure.size) ?: return@firstNotNullOfOrNull null }?.takeIf { it > low }
            Conversion(low, high, if ((high ?: low) > 1) unit.plural else unit.label, fraction = true)
        }
    }

    private class UsUnit(
        val label: String,
        val plural: String,
        val size: Double,
        val fractions: List<Double>,
        val min: Double,
        val max: Double = Double.MAX_VALUE,
    ) {
        /** The amount of this unit closest to [base] grams or millilitres, if it is close enough. */
        fun nearest(base: Double): Double? {
            val amount = base / size
            val whole = floor(amount)
            // From 10 on only halves, as Quantities shows them.
            val parts = listOf(0.0, 1.0) + if (amount >= 10) listOf(0.5) else fractions
            val best = parts.map { whole + it }.filter { it in min..max }.minByOrNull { abs(it - amount) } ?: return null
            return best.takeIf { abs(best - amount) <= amount * CLOSE_ENOUGH }
        }
    }
}
