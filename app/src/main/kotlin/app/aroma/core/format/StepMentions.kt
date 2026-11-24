package app.aroma.core.format

import app.aroma.core.model.Recipe
import java.util.Locale

/** A word of a step, from [start] to before [end], that names the recipe's ingredient [ingredient]: a tap on it ticks the ingredient off. */
data class StepMention(val start: Int, val end: Int, val ingredient: Int)

/**
 * For each of [steps], the words that name exactly one of the recipe's ingredients. A word that
 * could name more than one names the one Mealie links to the step, if it links exactly one of them;
 * otherwise it names none, as a wrong tick is worse than none.
 */
fun Recipe.stepMentions(steps: List<String>, locale: Locale): List<List<StepMention>> {
    val names = ingredients.map { StepLinks.nameWords(IngredientFormatter.format(it, 1.0, locale).toPlainText()) }
    val linked = linkedIngredients()
    return steps.mapIndexed { index, step ->
        StepLinks
            .mentions(names, step)
            .groupBy { it.range }
            .mapNotNull { (range, found) ->
                val candidates = found.map { it.ingredient }.distinct()
                val narrowed =
                    linked
                        ?.getOrNull(index)
                        ?.let { linkedHere ->
                            candidates.filter { it in linkedHere }
                        }?.takeIf { it.isNotEmpty() }
                (narrowed ?: candidates).singleOrNull()?.let { StepMention(range.first, range.last + 1, it) }
            }
    }
}
