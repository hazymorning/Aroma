package app.aroma.feature.recipe

import app.aroma.core.model.Organizer
import app.aroma.testing.sampleRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RecipeFactsTest {
    private val german = Locale.GERMANY
    private val nnbsp = "\u202F"

    @Test
    fun `a yield that counts the servings gives the stepper its words, unless they are servings`() {
        val sheet = RecipeYield(servings = 1.0, text = "1 Blech")
        assertEquals("Blech", sheet.stepperUnit)
        assertNull(sheet.line(2.0, german))
        val servings = RecipeYield(servings = 4.0, text = "4 Portionen")
        assertNull(servings.stepperUnit)
        assertNull(servings.line(1.0, german))
    }

    @Test
    fun `a yield of its own stands in the line, scaled, and one without a count does not`() {
        val biscuits = RecipeYield(servings = 4.0, text = "ca. 24 Stück")
        assertNull(biscuits.stepperUnit)
        assertEquals("ca. 24${nnbsp}Stück", biscuits.line(1.0, german))
        assertEquals("ca. 48${nnbsp}Stück", biscuits.line(2.0, german))
        assertEquals("8–12${nnbsp}Portionen", RecipeYield(servings = 0.0, text = "4–6 Portionen").line(2.0, german))
        assertNull(RecipeYield(servings = 4.0, text = "Stück").line(1.0, german))
        assertNull(RecipeYield(servings = 0.0, text = null).line(1.0, german))
    }

    @Test
    fun `baking is told by name, category or tag`() {
        assertTrue(recipe("Zitronen-Baiser-Plätzchen vom Blech").bakes())
        assertTrue(recipe("Rhabarber-Erdbeer-Streuselkuchenschnitten").bakes())
        assertTrue(recipe("Sonntagsfrühstück", categories = listOf("Backen")).bakes())
        assertTrue(recipe("Banana bread").bakes())
        assertFalse(recipe("No-Bake Bienenstich-Tiramisu").bakes())
        assertFalse(recipe("Cremiges Auberginen-Tikka-Masala mit Kokos-Joghurt", categories = listOf("Abendbrot")).bakes())
    }

    private fun recipe(name: String, categories: List<String> = emptyList()) =
        sampleRecipe(name, categories = categories.map { Organizer(it, it, it) })
}
