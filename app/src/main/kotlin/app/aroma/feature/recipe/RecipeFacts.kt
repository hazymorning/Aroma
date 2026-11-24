package app.aroma.feature.recipe

import app.aroma.core.format.Quantities
import app.aroma.core.format.YieldText
import app.aroma.core.model.Recipe
import app.aroma.ui.components.NARROW_NO_BREAK_SPACE
import java.util.Locale

/**
 * What the stepper counts, and what the line under the ingredients' title adds, from the recipe's
 * servings and the text of its yield. Where the text counts the servings themselves ("1 Blech", as
 * Mealie 2 kept them), the stepper counts in its words; otherwise it counts servings, and a yield of
 * its own ("ca. 24 Stück") stands in the line, scaled with the amounts.
 */
internal class RecipeYield(servings: Double, text: String?) {
    private val yield = YieldText.parse(text)

    private val countsServings =
        servings > 0 && yield != null && yield.quantity == servings && yield.upTo == null && yield.prefix.isEmpty()

    /** The words the stepper counts in, or null where it counts servings. */
    val stepperUnit: String? = yield?.unit?.takeIf { countsServings && it.isNotEmpty() && !it.isServings() }

    /** The yield of its own, scaled: "ca. 48 Stück"; null where there is none, or where the stepper counts it. */
    fun line(scale: Double, locale: Locale): String? {
        val counted = yield?.takeUnless { countsServings } ?: return null
        val quantity = counted.quantity ?: return null
        val count = listOfNotNull(quantity, counted.upTo).joinToString("–") { number(it * scale, locale) }
        val amount = if (counted.unit.isEmpty()) count else count + NARROW_NO_BREAK_SPACE + counted.unit
        return if (counted.prefix.isEmpty()) amount else "${counted.prefix} $amount"
    }

    private fun number(value: Double, locale: Locale): String =
        Quantities.display(value, preferFraction = false, locale = locale)?.toPlainText() ?: value.toString()

    private fun String.isServings(): Boolean = lowercase(Locale.ROOT).trimEnd('.') in ServingWords

    private companion object {
        val ServingWords = setOf("portion", "portionen", "serving", "servings", "person", "personen", "persons", "people", "pers")
    }
}

/** Whether the recipe is baked, so its time in the oven reads "Backen": its name, categories or tags say so. */
internal fun Recipe.bakes(): Boolean {
    val words = (listOf(name) + categories.map { it.name } + tags.map { it.name }).joinToString(" ").lowercase(Locale.ROOT)
    return !NotBaked.containsMatchIn(words) && Baked.containsMatchIn(words)
}

private val Baked =
    Regex(
        """kuchen|plätzchen|gebäck|torte|tarte|muffin|brownie|cookie|keks|gebacken|\bback|\bbrot\b|\bbrote\b|\bbrötchen|""" +
            """\bbak(e|ed|ing)\b|\bcakes?\b|\bbread|\bpies?\b|\btarts?\b|\bpastr""",
    )
private val NotBaked = Regex("""no[- ]bake|ohne backen""")
