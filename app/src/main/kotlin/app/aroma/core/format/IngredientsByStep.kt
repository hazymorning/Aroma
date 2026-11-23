package app.aroma.core.format

import app.aroma.core.model.Recipe
import java.util.Locale

/**
 * For each of the recipe's steps, the indexes of the ingredients it uses: as Mealie links them,
 * where the recipe has links, otherwise as the step names them.
 */
fun Recipe.ingredientsByStep(locale: Locale): List<List<Int>> {
    if (steps.any { it.ingredientReferences.isNotEmpty() }) {
        val byReference = ingredients.mapIndexedNotNull { index, ingredient -> ingredient.referenceId?.let { it to index } }.toMap()
        return steps.map { step ->
            step.ingredientReferences
                .mapNotNull { byReference[it] }
                .distinct()
                .sorted()
        }
    }
    val lines = ingredients.map { IngredientFormatter.format(it, 1.0, locale).toPlainText() }
    return StepLinks.of(lines, steps.map { it.text })
}
