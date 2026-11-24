package app.aroma.core.format

import app.aroma.core.model.RecipeSummary
import app.aroma.core.model.RecipeTime

/**
 * How long a recipe takes all in, in seconds: its total time, or else preparation and cooking
 * together; null where Mealie knows neither. A time Mealie keeps only as text, such as
 * "1 hour 20 minutes", is read as the times in the steps are, and a range counts by its end.
 */
fun RecipeSummary.readySeconds(): Int? =
    totalTime.seconds() ?: listOfNotNull(prepTime.seconds(), cookTime.seconds()).takeIf { it.isNotEmpty() }?.sum()

/** Whether a recipe is ready in [minutes] or less; one without any time never is. */
fun RecipeSummary.isReadyWithin(minutes: Int): Boolean = readySeconds()?.let { it <= minutes * SECONDS_PER_MINUTE } == true

private fun RecipeTime?.seconds(): Int? =
    this?.seconds ?: this?.text?.let { text -> DurationDetector.detect(text).sumOf { it.upperSeconds ?: it.seconds }.takeIf { it > 0 } }

private const val SECONDS_PER_MINUTE = 60
