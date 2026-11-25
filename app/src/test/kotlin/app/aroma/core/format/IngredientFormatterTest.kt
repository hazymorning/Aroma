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

    private fun format(ingredient: Ingredient, scale: Double = 1.0, system: MeasureSystem = MeasureSystem.AsWritten) =
        IngredientFormatter.format(ingredient, scale, locale, system)

    @Test
    fun `a parsed ingredient reads as Mealie writes it, its note beside the food`() {
        assertEquals("3 Eier", format(ingredient(3.0, food = egg)).toPlainText())
        assertEquals("1 Ei", format(ingredient(1.0, food = egg)).toPlainText())
        assertEquals("200 Gramm Mehl", format(ingredient(200.0, gram, IngredientFood(null, "Mehl", null))).toPlainText())
        assertEquals("2 EL Senf", format(ingredient(1.0, tablespoon, IngredientFood(null, "Senf", null)), scale = 2.0).toPlainText())
        val cheese = format(ingredient(100.0, gram, IngredientFood(null, "Bergkäse", null), note = "🧀 gerieben"))
        assertEquals("Bergkäse", cheese.text)
        assertEquals("gerieben", cheese.note)
    }

    @Test
    fun `free text scales its leading amount, a range at both ends, and moves the unit into the measure`() {
        val oil = format(ingredient(note = "2 EL Olivenöl"), scale = 2.0)
        assertEquals("4 EL", oil.measureText())
        assertEquals("Olivenöl", oil.text)
        val potatoes = format(ingredient(note = "3-4 Kartoffeln"), scale = 2.0)
        assertEquals("6–8", potatoes.measureText())
        assertEquals("Kartoffeln", potatoes.text)
    }

    @Test
    fun `free text without amount stays untouched`() {
        val line = format(ingredient(note = "Salz und Pfeffer"), scale = 3.0)
        assertNull(line.amount)
        assertEquals("Salz und Pfeffer", line.text)
    }

    @Test
    fun `quantity already written in the note is not shown twice`() {
        val line = format(ingredient(2.0, note = "2 Stangen Lauch (groß)"))
        assertEquals("2 Stangen", line.measureText())
        assertEquals("Lauch (groß)", line.text)
        assertEquals("2 Stangen Lauch (groß)", line.toPlainText())
    }

    @Test
    fun `unknown first words stay in the text column`() {
        val line = format(ingredient(note = "1 rote Zwiebel"))
        assertEquals("1", line.measureText())
        assertEquals("rote Zwiebel", line.text)
    }

    @Test
    fun `an emoji before free text no longer hides its amount`() {
        assertEquals("1000 g Mehl", format(ingredient(note = "🌾 500 g Mehl"), scale = 2.0).toPlainText())
        assertEquals("17 ½ oz Mehl", format(ingredient(note = "🌾 500 g Mehl"), system = MeasureSystem.US).toPlainText())
    }
}
