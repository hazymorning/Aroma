package app.aroma.feature.cook

import app.aroma.data.settings.IngredientsInSteps
import app.aroma.ui.components.IngredientRow

/** The ingredients a step shows, with their places in the recipe, by which they are ticked off. */
internal data class StepIngredients(val rows: List<IngredientRow>, val indexes: List<Int>, val all: Boolean)

/** All ingredients with their sections, those the step uses ([used]), or none, as the cook chose to see them. */
internal fun stepIngredients(rows: List<IngredientRow>, used: List<Int>, shown: IngredientsInSteps): StepIngredients =
    when (shown) {
        IngredientsInSteps.All -> {
            StepIngredients(rows, rows.indices.toList(), all = true)
        }

        IngredientsInSteps.Used -> {
            used.filter { it in rows.indices }.let {
                StepIngredients(
                    it.map { index ->
                        rows[index].copy(section = null)
                    },
                    it,
                    all = false,
                )
            }
        }

        IngredientsInSteps.None -> {
            StepIngredients(emptyList(), emptyList(), all = false)
        }
    }
