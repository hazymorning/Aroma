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
    fun format(ingredient: Ingredient, scale: Double, locale: Locale): IngredientLine =
        if (ingredient.isParsed) {
            parsed(ingredient.quantity, ingredient.unit, ingredient.food, ingredient.note, scale, locale)
        } else {
            freeText(ingredient.note.ifBlank { ingredient.display }, ingredient.quantity, scale, locale)
        }

    /** Mirrors Mealie's own display rules for structured ingredients and shopping items. */
    fun parsed(
        quantity: Double,
        unit: IngredientUnit?,
        food: IngredientFood?,
        note: String,
        scale: Double,
        locale: Locale,
    ): IngredientLine {
        val scaled = quantity * scale
        val amount = Quantities.display(scaled, unit?.useFraction ?: true, locale)
        val unitLabel = unit?.label(plural = scaled > 1)
        val foodLabel = food?.label(plural = quantity <= 0 || scaled > 1)
        val cleanNote = note.trim().ifEmpty { null }
        return if (foodLabel.isNullOrBlank()) {
            IngredientLine(amount, null, unitLabel, cleanNote.orEmpty(), null)
        } else {
            IngredientLine(amount, null, unitLabel, foodLabel, cleanNote)
        }
    }

    /** Free text keeps its wording; a leading amount is detected so it can be scaled. */
    fun freeText(text: String, quantity: Double, scale: Double, locale: Locale): IngredientLine {
        val trimmed = text.trim()
        val leading = LeadingQuantityParser.parse(trimmed)
        if (leading == null || (quantity > 0 && abs(leading.value - quantity) > 0.001)) {
            val amount = if (quantity > 0) Quantities.display(quantity * scale, true, locale) else null
            return IngredientLine(amount, null, null, trimmed, null)
        }
        val (unit, rest) = splitUnit(leading.rest)
        return IngredientLine(
            amount = Quantities.display(leading.value * scale, true, locale),
            amountUpper = leading.upper?.let { Quantities.display(it * scale, true, locale) },
            unit = unit,
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

    private val KNOWN_UNITS =
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
            "stange",
            "stangen",
            "zweig",
            "zweige",
            "esslöffel",
            "teelöffel",
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
