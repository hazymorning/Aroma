package app.aroma.core.format

import app.aroma.core.model.Ingredient
import app.aroma.core.model.Recipe
import app.aroma.core.model.Step
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** A tap on a word of a step ticks off the one ingredient it names, and never a guessed one. */
class StepMentionsTest {
    private fun line(text: String, reference: String? = null) = Ingredient(reference, null, 0.0, null, null, text, text, null)

    private fun recipe(ingredients: List<Ingredient>, step: String, references: List<String> = emptyList()) =
        Recipe(
            id = "r",
            slug = "r",
            name = "R",
            description = "",
            image = null,
            totalTime = null,
            prepTime = null,
            cookTime = null,
            servings = 4.0,
            rating = null,
            categories = emptyList(),
            tags = emptyList(),
            tools = emptyList(),
            sourceUrl = null,
            ingredients = ingredients,
            steps = listOf(Step(null, null, step, references, null)),
            notes = emptyList(),
            nutrition = emptyList(),
        )

    private fun Recipe.words(): List<Pair<String, Int>> =
        stepMentions(
            steps.map { it.text },
            Locale.GERMANY,
        ).single().map { steps.single().text.substring(it.start, it.end) to it.ingredient }

    @Test
    fun `each word that names one ingredient ticks it off`() {
        val recipe =
            recipe(listOf(line("250 g Mehl"), line("2 Eier"), line("Butter, geschmolzen")), "Mehl und Eier verquirlen, Butter unterrühren.")
        assertEquals(listOf("Mehl" to 0, "Eier" to 1, "Butter" to 2), recipe.words())
    }

    @Test
    fun `a word that could name two ingredients names none`() {
        val recipe = recipe(listOf(line("80 g Zucker"), line("150 g Zucker")), "Zucker einrieseln lassen.")
        assertEquals(emptyList<Pair<String, Int>>(), recipe.words())
    }

    @Test
    fun `a word that names one ingredient in full ticks that one off`() {
        val recipe =
            recipe(
                listOf(line("250 g Butter oder Margarine"), line("180 g Zucker"), line("1 Pck. Vanille-Zucker"), line("1 Prise Salz")),
                "Die zimmerwarme Butter, Zucker, Vanillezucker und die Prise Salz cremig schlagen.",
            )
        assertEquals(listOf("Butter" to 0, "Zucker" to 1, "Vanillezucker" to 2, "Salz" to 3), recipe.words())
    }

    @Test
    fun `the ingredient Mealie links to the step settles which one`() {
        val recipe =
            recipe(listOf(line("80 g Zucker", "a"), line("150 g Zucker", "b")), "Zucker einrieseln lassen.", references = listOf("b"))
        assertEquals(listOf("Zucker" to 1), recipe.words())
    }
}
