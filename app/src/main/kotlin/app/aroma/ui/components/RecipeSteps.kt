package app.aroma.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.aroma.core.format.IngredientFormatter
import app.aroma.core.format.Markdown
import app.aroma.core.format.MeasureSystem
import app.aroma.core.format.StepAmount
import app.aroma.core.format.StepMeasures
import app.aroma.core.format.StepMention
import app.aroma.core.format.StyledText
import app.aroma.core.format.UnitSpacing
import app.aroma.core.format.stepAmounts
import app.aroma.core.format.stepMentions
import app.aroma.core.model.Recipe
import app.aroma.core.model.Step

/**
 * A recipe's steps as the recipe's page and cook mode set them: each step's text as it reads, the
 * amounts written into it and the words in it that tick an ingredient off.
 */
class StepTexts(
    private val texts: List<StyledText>,
    private val amounts: List<List<StepAmount>>,
    private val mentions: List<List<StepMention>>,
) {
    /** The text of [step], the one at [index], as it reads; its own text until that is worked out. */
    fun textOf(index: Int, step: Step): StyledText = texts.getOrNull(index) ?: StyledText(step.text, emptyList())

    fun amountsOf(index: Int): List<StepAmount> = amounts.getOrNull(index).orEmpty()

    fun mentionsOf(index: Int): List<StepMention> = mentions.getOrNull(index).orEmpty()
}

/**
 * The steps of [recipe] as they read: temperatures and pan sizes in [measures], and the amounts,
 * scaled by [scale], written in where [amountsInSteps] is set.
 */
@Composable
fun rememberStepTexts(recipe: Recipe?, scale: Double, measures: MeasureSystem, amountsInSteps: Boolean): StepTexts {
    val locale = currentLocale()
    val texts =
        remember(recipe, measures, locale) {
            recipe?.steps.orEmpty().map { Markdown.parse(UnitSpacing.apply(StepMeasures.convert(it.text, measures, locale))) }
        }
    val amounts =
        remember(recipe, texts, scale, measures, locale, amountsInSteps) {
            if (recipe == null || !amountsInSteps) emptyList() else recipe.stepAmounts(texts.map { it.text }, scale, locale, measures)
        }
    val mentions = remember(recipe, texts, locale) { recipe?.stepMentions(texts.map { it.text }, locale).orEmpty() }
    return remember(texts, amounts, mentions) { StepTexts(texts, amounts, mentions) }
}

/** The ingredients of [recipe] as the lists show them: scaled by [scale] and in [measures], under their sections. */
@Composable
fun rememberIngredientRows(recipe: Recipe?, scale: Double, measures: MeasureSystem): List<IngredientRow> {
    val locale = currentLocale()
    val ingredients = recipe?.ingredients.orEmpty()
    return remember(ingredients, scale, measures, locale) {
        ingredients.map { IngredientRow(it.section, IngredientFormatter.format(it, scale, locale, measures)) }
    }
}
