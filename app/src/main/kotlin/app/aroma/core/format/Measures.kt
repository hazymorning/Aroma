package app.aroma.core.format

import app.aroma.core.model.IngredientUnit
import java.util.Locale

internal enum class Dimension { Mass, Volume }

/**
 * What a unit measures: [size] grams or millilitres, in [system]. Old units of neither system, such
 * as the German Pfund of 500 g, have none and convert to both.
 */
internal data class Measure(val dimension: Dimension, val size: Double, val system: MeasureSystem?)

/*
 * US volumes in the sizes of US nutrition labels: a cup of 240 ml holds 16 tablespoons of 15 ml.
 * They are within 1.5 % of the customary sizes, far closer than anyone fills a cup, and keep
 * 1 cup at 240 ml and 250 ml at 1 cup.
 */
internal const val CUP = 240.0
internal const val TABLESPOON = 15.0
internal const val FLUID_OUNCE = 30.0
internal const val OUNCE = 28.349523125
internal const val POUND = 453.59237

/** Tells what a unit of a recipe measures, from Mealie's standard fields or from its name. */
internal object Measures {
    private val sticks = arrayOf("stick", "sticks")

    private val gram = Measure(Dimension.Mass, 1.0, MeasureSystem.Metric)
    private val kilogram = Measure(Dimension.Mass, 1000.0, MeasureSystem.Metric)
    private val millilitre = Measure(Dimension.Volume, 1.0, MeasureSystem.Metric)
    private val litre = Measure(Dimension.Volume, 1000.0, MeasureSystem.Metric)
    private val ounce = Measure(Dimension.Mass, OUNCE, MeasureSystem.US)
    private val pound = Measure(Dimension.Mass, POUND, MeasureSystem.US)
    private val fluidOunce = Measure(Dimension.Volume, FLUID_OUNCE, MeasureSystem.US)
    private val cup = Measure(Dimension.Volume, CUP, MeasureSystem.US)

    /** The units Mealie 3.13 and newer describe every other unit with. */
    private val standards =
        mapOf(
            "gram" to gram,
            "kilogram" to kilogram,
            "milliliter" to millilitre,
            "liter" to litre,
            "ounce" to ounce,
            "pound" to pound,
            "fluid_ounce" to fluidOunce,
            "cup" to cup,
        )

    /**
     * English and German names, abbreviations and plurals, in lower case and without dots. Spoons
     * are missing on purpose and stay as written: kitchens of both systems measure with the same
     * spoons (an Australian tablespoon even holds 20 ml), and a kitchen scale is too coarse for the
     * 4 g of a teaspoon of baking powder.
     */
    private val names: Map<String, Measure> =
        buildMap {
            fun add(measure: Measure, vararg keys: String) = keys.forEach { put(it, measure) }
            add(gram, "g", "gr", "gram", "grams", "gramm")
            add(kilogram, "kg", "kilo", "kilos", "kilogram", "kilograms", "kilogramm")
            add(gram.copy(size = 0.001), "mg", "milligram", "milligrams", "milligramm")
            add(millilitre, "ml", "milliliter", "milliliters", "millilitre", "millilitres")
            add(millilitre.copy(size = 10.0), "cl", "centiliter", "centiliters", "zentiliter")
            add(millilitre.copy(size = 100.0), "dl", "deciliter", "deciliters", "deziliter")
            add(litre, "l", "liter", "liters", "litre", "litres")
            add(Measure(Dimension.Mass, 500.0, null), "pfund", "pfd")
            add(ounce, "oz", "ounce", "ounces", "unze", "unzen")
            add(pound, "lb", "lbs", "pound", "pounds")
            add(fluidOunce, "fl oz", "floz", "fluid ounce", "fluid ounces")
            add(cup, "c", "cup", "cups", "tasse", "tassen")
            add(cup.copy(size = 2 * CUP), "pt", "pint", "pints")
            add(cup.copy(size = 4 * CUP), "qt", "quart", "quarts")
            add(cup.copy(size = 16 * CUP), "gal", "gallon", "gallons")
            add(Measure(Dimension.Mass, CupWeight.Butter.grams / 2, MeasureSystem.US), *sticks)
        }

    private val spoons =
        setOf("tsp", "teaspoon", "teaspoons", "tbsp", "tbs", "tablespoon", "tablespoons", "tl", "el", "teelöffel", "esslöffel")
    private val amountWithUnit = Regex("""\d\s*-?\s*(\p{L}+(?:\.?\s*oz)?)""")
    private val spaces = Regex("""\s+""")

    /** [unit] as Mealie describes it, falling back to its names for servers before 3.13. */
    fun of(unit: IngredientUnit, food: CupWeight?): Measure? {
        val standard = standards[unit.standardUnit]
        val quantity = unit.standardQuantity
        if (standard != null && quantity != null && quantity > 0) {
            return standard.copy(size = standard.size * quantity).takeUnless { it.isSpoon }
        }
        return listOfNotNull(unit.abbreviation, unit.name, unit.pluralName, unit.pluralAbbreviation)
            .firstNotNullOfOrNull { named(it, food) }
    }

    /** The unit called [name]. A stick is butter's: cinnamon and celery come in sticks too. */
    fun named(name: String, food: CupWeight?): Measure? {
        val key = key(name)
        return if (key in sticks && food != CupWeight.Butter) null else names[key]
    }

    /** Splits free text such as "fl oz milk" into its leading unit and the rest. */
    fun leading(text: String): Pair<String, String>? {
        val first = text.substringBefore(' ')
        val two = text.split(' ', limit = 3).take(2).joinToString(" ")
        val unit = listOf(two, first).firstOrNull { key(it) in names } ?: return null
        return unit to text.substring(unit.length).trim().ifEmpty { return null }
    }

    /**
     * Whether [text] gives another amount, as "(240 ml)" does in "1 cup (240 ml) milk" or "2 tbsp"
     * in "1 cup plus 2 tbsp flour". Converting only the first would show it twice or half of it.
     */
    fun mentioned(text: String): Boolean =
        amountWithUnit.findAll(text).map { it.groupValues[1] }.any { key(it) in spoons || named(it, null) != null }

    private fun key(name: String) =
        name
            .lowercase(Locale.ROOT)
            .replace(".", "")
            .trim()
            .replace(spaces, " ")

    private val Measure.isSpoon get() = system == MeasureSystem.US && dimension == Dimension.Volume && size <= TABLESPOON
}
