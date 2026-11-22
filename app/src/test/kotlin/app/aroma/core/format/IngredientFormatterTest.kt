package app.aroma.core.format

import app.aroma.core.model.Ingredient
import app.aroma.core.model.IngredientFood
import app.aroma.core.model.IngredientUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class IngredientFormatterTest {
    private val locale = Locale.GERMANY
    private val gram = IngredientUnit("u1", "Gramm", null, "g", null, useAbbreviation = false, useFraction = true)
    private val tablespoon = IngredientUnit("u2", "Esslöffel", null, "EL", null, useAbbreviation = true, useFraction = true)
    private val egg = IngredientFood("f1", "Ei", "Eier")

    private fun ingredient(quantity: Double = 0.0, unit: IngredientUnit? = null, food: IngredientFood? = null, note: String = "") =
        Ingredient(null, null, quantity, unit, food, note, display = "", linkedRecipe = null)

    @Test
    fun `parsed ingredient uses plural food names above one`() {
        val line = IngredientFormatter.format(ingredient(3.0, food = egg), 1.0, locale)
        assertEquals("3", line.measureText())
        assertEquals("Eier", line.text)
        assertEquals("Ei", IngredientFormatter.format(ingredient(1.0, food = egg), 1.0, locale).text)
    }

    @Test
    fun `unit abbreviation follows the server setting`() {
        assertEquals(
            "200 Gramm",
            IngredientFormatter.format(ingredient(200.0, gram, IngredientFood(null, "Mehl", null)), 1.0, locale).measureText(),
        )
        assertEquals(
            "2 EL",
            IngredientFormatter.format(ingredient(1.0, tablespoon, IngredientFood(null, "Senf", null)), 2.0, locale).measureText(),
        )
    }

    @Test
    fun `note becomes secondary text next to the food`() {
        val line =
            IngredientFormatter.format(
                ingredient(100.0, gram, IngredientFood(null, "Bergkäse", null), note = "gerieben"),
                1.0,
                locale,
            )
        assertEquals("Bergkäse", line.text)
        assertEquals("gerieben", line.note)
    }

    @Test
    fun `free text with a leading amount scales and moves the unit into the measure`() {
        val line = IngredientFormatter.format(ingredient(note = "2 EL Olivenöl"), 2.0, locale)
        assertEquals("4 EL", line.measureText())
        assertEquals("Olivenöl", line.text)
    }

    @Test
    fun `free text ranges scale both bounds`() {
        val line = IngredientFormatter.format(ingredient(note = "3-4 Kartoffeln"), 2.0, locale)
        assertEquals("6–8", line.measureText())
        assertEquals("Kartoffeln", line.text)
    }

    @Test
    fun `free text without amount stays untouched`() {
        val line = IngredientFormatter.format(ingredient(note = "Salz und Pfeffer"), 3.0, locale)
        assertNull(line.amount)
        assertEquals("Salz und Pfeffer", line.text)
    }

    @Test
    fun `quantity already written in the note is not shown twice`() {
        val line = IngredientFormatter.format(ingredient(2.0, note = "2 Stangen Lauch (groß)"), 1.0, locale)
        assertEquals("2 Stangen", line.measureText())
        assertEquals("Lauch (groß)", line.text)
        assertEquals("2 Stangen Lauch (groß)", line.toPlainText())
    }

    @Test
    fun `unknown first words stay in the text column`() {
        val line = IngredientFormatter.format(ingredient(note = "1 rote Zwiebel"), 1.0, locale)
        assertEquals("1", line.measureText())
        assertEquals("rote Zwiebel", line.text)
    }
}
