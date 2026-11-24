package app.aroma.core.format

import app.aroma.core.model.Ingredient
import app.aroma.core.model.IngredientFood
import app.aroma.core.model.IngredientUnit
import java.util.Locale
import kotlin.math.abs

/**
 * One ingredient split into the parts Aroma lays out separately:
 * the measure column (amount and unit) and the text column (food and note).
 */
data class IngredientLine(
    val amount: DisplayQuantity?,
    val amountUpper: DisplayQuantity?,
    val unit: String?,
    val text: String,
    val note: String?,
) {
    val hasMeasure: Boolean get() = amount != null || !unit.isNullOrBlank()

    fun measureText(): String =
        listOfNotNull(
            amount?.let { a -> amountUpper?.let { "${a.toPlainText()}–${it.toPlainText()}" } ?: a.toPlainText() },
            unit?.takeIf { it.isNotBlank() },
        ).joinToString(" ")

    fun toPlainText(): String =
        listOf(measureText(), text, note.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" ")
}

object IngredientFormatter {
    /** [ingredient] scaled by [scale], with its amount converted to [system] where that reads well. */
    fun format(ingredient: Ingredient, scale: Double, locale: Locale, system: MeasureSystem = MeasureSystem.AsWritten): IngredientLine =
        if (ingredient.isParsed) {
            parsed(ingredient.quantity, ingredient.unit, ingredient.food, ingredient.note, scale, locale, system)
        } else {
            freeText(ingredient.note.ifBlank { ingredient.display }, ingredient.quantity, scale, locale, system)
        }

    /** Mirrors Mealie's own display rules for structured ingredients and shopping items, leaving out emoji in the note. */
    fun parsed(
        quantity: Double,
        unit: IngredientUnit?,
        food: IngredientFood?,
        note: String,
        scale: Double,
        locale: Locale,
        system: MeasureSystem = MeasureSystem.AsWritten,
    ): IngredientLine {
        val scaled = quantity * scale
        val conversion = unit?.let { parsedConversion(scaled, it, food, note, system) }
        val amount = Quantities.display(conversion?.amount ?: scaled, conversion?.fraction ?: unit?.useFraction ?: true, locale)
        val unitLabel = conversion?.unit ?: unit?.label(plural = scaled > 1)
        val foodLabel = food?.label(plural = quantity <= 0 || scaled > 1)
        val cleanNote = note.withoutPictographs().trim().ifEmpty { null }
        return if (foodLabel.isNullOrBlank()) {
            IngredientLine(amount, null, unitLabel, cleanNote.orEmpty(), null)
        } else {
            IngredientLine(amount, null, unitLabel, foodLabel, cleanNote)
        }
    }

    /**
     * Free text keeps its wording but not its emoji, which would hide the amount of "🌾 500 g Mehl". A leading amount
     * is detected so it can be scaled and converted.
     */
    fun freeText(
        text: String,
        quantity: Double,
        scale: Double,
        locale: Locale,
        system: MeasureSystem = MeasureSystem.AsWritten,
    ): IngredientLine {
        val trimmed = text.withoutPictographs().trim()
        val leading = LeadingQuantityParser.parse(trimmed)
        if (leading == null || (quantity > 0 && abs(leading.value - quantity) > 0.001)) {
            val amount = if (quantity > 0) Quantities.display(quantity * scale, true, locale) else null
            return IngredientLine(amount, null, null, trimmed, null)
        }
        convertedFreeText(leading, scale, locale, system)?.let { return it }
        val (unit, rest) = splitUnit(leading.rest)
        return IngredientLine(
            amount = Quantities.display(leading.value * scale, true, locale),
            amountUpper = leading.upper?.let { Quantities.display(it * scale, true, locale) },
            unit = unit,
            text = rest,
            note = null,
        )
    }

    /** An amount of a Mealie unit in [system], unless the [note] gives an amount too. */
    private fun parsedConversion(
        amount: Double,
        unit: IngredientUnit,
        food: IngredientFood?,
        note: String,
        system: MeasureSystem,
    ): Conversion? {
        if (system == MeasureSystem.AsWritten || Measures.mentioned(note)) return null
        val weight = food?.let { CupWeight.of(it.name, note) ?: it.pluralName?.let { plural -> CupWeight.of(plural, note) } }
        val measure = Measures.of(unit, weight) ?: return null
        return UnitConversion.convert(amount, null, measure, weight, system)
    }

    /** "2 cups flour" as "240 g flour", unless the text gives another amount, as "2 cups (250 g) flour" does. */
    private fun convertedFreeText(leading: LeadingQuantity, scale: Double, locale: Locale, system: MeasureSystem): IngredientLine? {
        if (system == MeasureSystem.AsWritten) return null
        val (unit, rest) = Measures.leading(leading.rest) ?: return null
        if (Measures.mentioned(rest)) return null
        val weight = CupWeight.of(rest)
        val measure = Measures.named(unit, weight) ?: return null
        val conversion = UnitConversion.convert(leading.value * scale, leading.upper?.times(scale), measure, weight, system) ?: return null
        return IngredientLine(
            amount = Quantities.display(conversion.amount, conversion.fraction, locale),
            amountUpper = conversion.upper?.let { Quantities.display(it, conversion.fraction, locale) },
            unit = conversion.unit,
            text = rest,
            note = null,
        )
    }

    private fun splitUnit(text: String): Pair<String?, String> {
        val firstWord = text.substringBefore(' ')
        val key = firstWord.trimEnd('.').lowercase(Locale.ROOT)
        if (key !in KNOWN_UNITS || firstWord.length == text.length) return null to text
        return firstWord to text.substring(firstWord.length).trim()
    }

    internal val KNOWN_UNITS =
        setOf(
            "g",
            "gr",
            "gramm",
            "kg",
            "mg",
            "ml",
            "cl",
            "dl",
            "l",
            "liter",
            "el",
            "tl",
            "msp",
            "prise",
            "prisen",
            "tasse",
            "tassen",
            "becher",
            "bund",
            "stück",
            "stk",
            "scheibe",
            "scheiben",
            "zehe",
            "zehen",
            "dose",
            "dosen",
            "packung",
            "packungen",
            "pck",
            "päckchen",
            "glas",
            "gläser",
            "handvoll",
            "cm",
            "stange",
            "stangen",
            "zweig",
            "zweige",
            "würfel",
            "esslöffel",
            "teelöffel",
            "essl",
            "teel",
            "cup",
            "cups",
            "tbsp",
            "tbs",
            "tablespoon",
            "tablespoons",
            "tsp",
            "teaspoon",
            "teaspoons",
            "oz",
            "ounce",
            "ounces",
            "lb",
            "lbs",
            "pound",
            "pounds",
            "pinch",
            "clove",
            "cloves",
            "can",
            "cans",
            "slice",
            "slices",
            "bunch",
            "stick",
            "sticks",
            "sprig",
            "sprigs",
            "pkg",
            "package",
            "packet",
            "quart",
            "quarts",
            "pint",
            "pints",
            "handful",
        )
}
