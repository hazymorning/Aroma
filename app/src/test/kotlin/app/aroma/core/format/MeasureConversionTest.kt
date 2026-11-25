package app.aroma.core.format

import app.aroma.core.model.Ingredient
import app.aroma.core.model.IngredientFood
import app.aroma.core.model.IngredientUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MeasureConversionTest {
    private val english = Locale.US
    private val german = Locale.GERMANY

    private fun line(text: String, system: MeasureSystem, scale: Double = 1.0, locale: Locale = english) =
        IngredientFormatter.format(Ingredient(null, null, 0.0, null, null, text, "", null), scale, locale, system)

    private fun free(text: String, system: MeasureSystem, scale: Double = 1.0, locale: Locale = english) =
        line(text, system, scale, locale).toPlainText()

    private fun assertConverts(system: MeasureSystem, cases: List<Pair<String, String>>) =
        cases.forEach { (written, shown) -> assertEquals(written, shown, free(written, system)) }

    private fun assertKeeps(system: MeasureSystem, lines: List<String>) = assertConverts(system, lines.map { it to it })

    /** A unit as Mealie 3.13 and newer sends it, with its standard; without one it is an older server's. */
    private fun unit(name: String, quantity: Double? = null, standard: String? = null) =
        IngredientUnit("u-$name", name, null, "", null, useAbbreviation = false, useFraction = true, quantity, standard)

    private fun parsed(
        quantity: Double,
        unit: IngredientUnit,
        food: IngredientFood,
        system: MeasureSystem,
        note: String = "",
    ) = IngredientFormatter.format(Ingredient(null, null, quantity, unit, food, note, "", null), 1.0, english, system).toPlainText()

    private fun food(name: String, plural: String? = null) = IngredientFood("f-$name", name, plural)

    @Test
    fun `US amounts become grams and millilitres in the steps a scale or jug shows`() =
        assertConverts(
            MeasureSystem.Metric,
            listOf(
                // Single grams below 10, then steps of 5, 10, 25 and 50.
                "1/4 oz active dry yeast" to "7 g active dry yeast",
                "2 oz pancetta" to "55 g pancetta",
                "1 lb ground beef" to "450 g ground beef",
                "1 1/2 lbs potatoes" to "675 g potatoes",
                "2 1/2 lb flour" to "1.15 kg flour",
                // Cups of a food whose weight is known are weighed, liquids poured.
                "1 cup flour" to "120 g flour",
                "1 c. sugar" to "200 g sugar",
                "1 Tasse Mehl" to "120 g Mehl",
                "1 stick butter" to "115 g butter",
                "6 cups water" to "1.45 l water",
                // Brands of kosher salt differ twofold, whole walnuts by a quarter.
                "1 cup kosher salt" to "240 ml kosher salt",
                "1 cup walnuts" to "240 ml walnuts",
                "1 Pfund Hackfleisch" to "500 g Hackfleisch",
            ),
        )

    @Test
    fun `metric amounts become the closest cup, spoon, ounce or pound`() =
        assertConverts(
            MeasureSystem.US,
            listOf(
                "30 g Butter" to "1 oz Butter",
                "250 g Mehl" to "8 ¾ oz Mehl",
                "300 g Spaghetti" to "10 ½ oz Spaghetti",
                "500 g Hackfleisch" to "17 ½ oz Hackfleisch",
                "1 kg Kartoffeln" to "2 ¼ lb Kartoffeln",
                "10 ml Zitronensaft" to "2 tsp Zitronensaft",
                "50 ml Öl" to "3 ½ tbsp Öl",
                "250 ml Milch" to "1 cup Milch",
                "400 ml Kokosmilch" to "1 ⅔ cups Kokosmilch",
                "1 l Milch" to "1 qt Milch",
                // No cup or spoon measures this within 5 %; the jug's fluid ounces do.
                "150 ml Milch" to "5 fl oz Milch",
                "1 Pfund Kartoffeln" to "17 ½ oz Kartoffeln",
            ),
        )

    @Test
    fun `what has no clean measure in the other system, or gives a second amount, stays as written`() {
        assertKeeps(MeasureSystem.Metric, listOf("2 tbsp butter", "3 eggs", "1 stick cinnamon", "250 g flour"))
        assertKeeps(MeasureSystem.US, listOf("20 g Butter", "2 EL Olivenöl", "1 Prise Salz", "2 cups flour", "Salz und Pfeffer"))
        assertKeeps(MeasureSystem.AsWritten, listOf("1 cup flour", "250 g Mehl"))
        // Converting only the first amount would show the second twice, or half of it.
        assertKeeps(MeasureSystem.Metric, listOf("1 cup (240 ml) milk", "1 lb 4 oz flour", "1 cup plus 2 tablespoons flour"))
        assertKeeps(MeasureSystem.US, listOf("250 g (2 cups) flour"))
    }

    @Test
    fun `decimals follow the locale`() {
        assertEquals("1,15 kg Kartoffeln", free("2 1/2 lb Kartoffeln", MeasureSystem.Metric, locale = german))
        assertEquals("1 ½ qt Wasser", free("1,5 l Wasser", MeasureSystem.US, locale = german))
    }

    @Test
    fun `ranges convert both ends in one unit`() {
        assertEquals("240–360 g flour", free("2-3 cups flour", MeasureSystem.Metric))
        assertEquals("7–10 ½ oz Mehl", free("200-300 g Mehl", MeasureSystem.US))
    }

    @Test
    fun `scaling happens before converting, so amounts are rounded once`() =
        assertEquals("225 g butter", free("1/2 cup butter", MeasureSystem.Metric, scale = 2.0))

    @Test
    fun `Mealie's units convert by their standard, or by their name on older servers`() {
        val cup = unit("cup", 1.0, "cup")
        assertEquals("120 g flour", parsed(1.0, cup, food("flour"), MeasureSystem.Metric))
        assertEquals("1 cup flour", parsed(1.0, cup, food("flour"), MeasureSystem.AsWritten))
        assertEquals("6 ½ fl oz Sahne", parsed(1.0, unit("Becher", 200.0, "milliliter"), food("Sahne"), MeasureSystem.US))
        // Kitchens of both systems measure with the same spoons.
        assertEquals("2 tablespoon olive oil", parsed(2.0, unit("tablespoon", 0.5, "fluid_ounce"), food("olive oil"), MeasureSystem.Metric))
        assertEquals("115 g butter", parsed(1.0, unit("stick"), food("butter"), MeasureSystem.Metric))
        assertEquals("1 Esslöffel Öl", parsed(1.0, unit("Esslöffel"), food("Öl"), MeasureSystem.US))
    }

    @Test
    fun `the note tells how a food is cut, or gives the amount itself`() {
        val cup = unit("cup", 1.0, "cup")
        assertEquals("85 g almonds sliced", parsed(1.0, cup, food("almonds"), MeasureSystem.Metric, note = "sliced"))
        assertEquals("240 ml almonds chopped", parsed(1.0, cup, food("almonds"), MeasureSystem.Metric, note = "chopped"))
        assertEquals("115 g Walnuss gehackt", parsed(1.0, cup, food("Walnuss", "Walnüsse"), MeasureSystem.Metric, note = "gehackt"))
        assertEquals("1 cup flour (125 g)", parsed(1.0, cup, food("flour"), MeasureSystem.Metric, note = "(125 g)"))
    }

    @Test
    fun `converted amounts are laid out like written ones`() {
        val millilitres = line("1 1/2 cups milk", MeasureSystem.Metric)
        assertEquals(DisplayQuantity.Decimal("360"), millilitres.amount)
        assertEquals("ml", millilitres.unit)
        assertEquals("milk", millilitres.text)
        val cups = line("350 ml Milch", MeasureSystem.US, locale = german)
        assertEquals(DisplayQuantity.Fraction(1, 1, 2), cups.amount)
        assertEquals("cups", cups.unit)
    }
}
