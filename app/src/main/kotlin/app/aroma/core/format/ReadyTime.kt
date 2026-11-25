package app.aroma.core.format

import app.aroma.core.model.RecipeSummary
import app.aroma.core.model.RecipeTime

/**
 * How long a recipe takes all in, in seconds: its total time, or else preparation and cooking
 * together; null where Mealie knows neither. A time Mealie keeps only as text, such as
 * "1 hour 20 minutes" or "PT45M", is read as the recipe's page reads it, and a range counts by its end.
 */
fun RecipeSummary.readySeconds(): Int? =
    totalTime.seconds() ?: listOfNotNull(prepTime.seconds(), cookTime.seconds()).takeIf { it.isNotEmpty() }?.sum()

/** Whether a recipe is ready in [minutes] or less; one without any time never is. */
fun RecipeSummary.isReadyWithin(minutes: Int): Boolean = readySeconds()?.let { it <= minutes * SECONDS_PER_MINUTE } == true

/**
 * The minutes a time Mealie keeps as text, such as "1:20 h" or "PT45M", stands for, as the recipe's
 * page reads it: a range by its end; null where the text names no time.
 */
fun readyMinutes(text: String): Int? = TimeText.read(text)?.let { time -> (time.upTo ?: time.minutes).takeIf { it > 0 } }

private fun RecipeTime?.seconds(): Int? = this?.seconds ?: this?.text?.let(::readyMinutes)?.times(SECONDS_PER_MINUTE)

private const val SECONDS_PER_MINUTE = 60
