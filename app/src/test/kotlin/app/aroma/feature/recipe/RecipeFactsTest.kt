package app.aroma.feature.recipe

import app.aroma.core.model.Organizer
import app.aroma.core.model.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RecipeFactsTest {
    private val german = Locale.GERMANY
    private val nnbsp = " "

    @Test
    fun `a yield that counts the servings gives the stepper its words`() {
        val yields = RecipeYield(servings = 1.0, text = "1 Blech")
        assertEquals("Blech", yields.stepperUnit)
        assertNull(yields.line(2.0, german))
    }

    @Test
    fun `servings by name are counted as servings`() {
        val yields = RecipeYield(servings = 4.0, text = "4 Portionen")
        assertNull(yields.stepperUnit)
        assertNull(yields.line(1.0, german))
    }

    @Test
    fun `a yield of its own stands in the line, scaled`() {
        val yields = RecipeYield(servings = 4.0, text = "ca. 24 Stück")
        assertNull(yields.stepperUnit)
        assertEquals("ca. 24${nnbsp}Stück", yields.line(1.0, german))
        assertEquals("ca. 48${nnbsp}Stück", yields.line(2.0, german))
    }

    @Test
    fun `a yield without a count shows nothing on its own`() {
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
        Recipe(
            id = name,
            slug = name,
            name = name,
            description = "",
            image = null,
            totalTime = null,
            prepTime = null,
            cookTime = null,
            servings = 0.0,
            rating = null,
            categories = categories.map { Organizer(it, it, it) },
            tags = emptyList(),
            tools = emptyList(),
            sourceUrl = null,
            ingredients = emptyList(),
            steps = emptyList(),
            notes = emptyList(),
            nutrition = emptyList(),
        )
}
