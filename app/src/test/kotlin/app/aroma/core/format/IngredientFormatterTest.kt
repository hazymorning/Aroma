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

    /** A tart's ingredients as a post lists them, which Mealie keeps as unparsed notes. */
    private val tart =
        listOf(
            "🌾 500 g Mehl",
            "🧈 300 g weiche Butter",
            "🤍 150 g Zucker",
            "🥚 4 Eigelb",
            "🌼 2 TL Vanilleextrakt",
            "🧂 1 Prise Salz",
            "🍋 350 g Lemon Curd",
        )

    private fun tart(scale: Double, system: MeasureSystem = MeasureSystem.AsWritten) =
        tart.map { IngredientFormatter.format(ingredient(note = it), scale, locale, system) }

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

    @Test
    fun `an emoji before free text no longer hides its amount`() {
        val lines = tart(1.0)
        assertEquals(listOf("500 g", "300 g", "150 g", "4", "2 TL", "1 Prise", "350 g"), lines.map { it.measureText() })
        assertEquals(listOf("Mehl", "weiche Butter", "Zucker", "Eigelb", "Vanilleextrakt", "Salz", "Lemon Curd"), lines.map { it.text })
        assertEquals(tart.map { it.substringAfter(' ') }, lines.map { it.toPlainText() })
    }

    @Test
    fun `free text behind an emoji scales and converts`() {
        assertEquals(listOf("1000 g", "600 g", "300 g", "8", "4 TL", "2 Prise", "700 g"), tart(2.0).map { it.measureText() })
        val us = tart(1.0, MeasureSystem.US)
        assertEquals(listOf("17 ½ oz", "10 ½ oz", "5 ¼ oz", "4", "2 TL", "1 Prise", "12 ½ oz"), us.map { it.measureText() })
        assertEquals(listOf("Mehl", "weiche Butter", "Zucker", "Eigelb", "Vanilleextrakt", "Salz", "Lemon Curd"), us.map { it.text })
        val doubledUs = tart(2.0, MeasureSystem.US)
        assertEquals(listOf("2 ¼ lb", "1 ¼ lb", "10 ½ oz", "8", "4 TL", "2 Prise", "1 ½ lb"), doubledUs.map { it.measureText() })
        assertEquals("2 ¼ lb Mehl", doubledUs.first().toPlainText())
    }

    @Test
    fun `emoji anywhere in free text or in a note are left out`() {
        val salt = IngredientFormatter.format(ingredient(note = "Salz 🧂 und Pfeffer 🌶️"), 2.0, locale)
        assertNull(salt.amount)
        assertEquals("Salz und Pfeffer", salt.text)
        val cheese =
            IngredientFormatter.format(
                ingredient(100.0, gram, IngredientFood(null, "Bergkäse", null), note = "🧀 gerieben"),
                1.0,
                locale,
            )
        assertEquals("100 Gramm", cheese.measureText())
        assertEquals("Bergkäse", cheese.text)
        assertEquals("gerieben", cheese.note)
        val oil = IngredientFormatter.format(ingredient(1.0, tablespoon, note = "Olivenöl 🫒"), 2.0, locale)
        assertEquals("2 EL Olivenöl", oil.toPlainText())
    }
}
