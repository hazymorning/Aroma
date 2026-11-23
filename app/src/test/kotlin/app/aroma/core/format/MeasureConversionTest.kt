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

    private fun assertConverts(system: MeasureSystem, cases: List<Pair<String, String>>, locale: Locale = english) =
        cases.forEach { (written, shown) -> assertEquals(written, shown, free(written, system, locale = locale)) }

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
        locale: Locale = english,
    ) = IngredientFormatter.format(Ingredient(null, null, quantity, unit, food, note, "", null), 1.0, locale, system).toPlainText()

    private fun food(name: String, plural: String? = null) = IngredientFood("f-$name", name, plural)

    @Test
    fun `cups of foods with a known weight become grams`() =
        assertConverts(
            MeasureSystem.Metric,
            listOf(
                "1 cup flour" to "120 g flour",
                "2 cups all-purpose flour, sifted" to "240 g all-purpose flour, sifted",
                "1 cup sugar" to "200 g sugar",
                "3/4 cup packed light brown sugar" to "160 g packed light brown sugar",
                "1/2 cup powdered sugar" to "55 g powdered sugar",
                "1 cup unsalted butter, softened" to "225 g unsalted butter, softened",
                "1/2 cup honey" to "170 g honey",
                "1/3 cup extra-virgin olive oil" to "70 g extra-virgin olive oil",
                "1/4 cup vegetable oil" to "55 g vegetable oil",
                "1 cup long-grain white rice" to "200 g long-grain white rice",
                "1 cup old-fashioned rolled oats" to "90 g old-fashioned rolled oats",
                "1/2 cup unsweetened cocoa powder" to "40 g unsweetened cocoa powder",
                "1 cup chopped walnuts" to "115 g chopped walnuts",
                "1 cup sliced almonds" to "85 g sliced almonds",
                "1 cup shredded cheddar cheese" to "115 g shredded cheddar cheese",
                "1/2 cup freshly grated Parmesan" to "50 g freshly grated Parmesan",
                "1 cup semi-sweet chocolate chips" to "170 g semi-sweet chocolate chips",
                "1/4 cup cornstarch" to "30 g cornstarch",
                "1 cup panko" to "50 g panko",
                "1 cup creamy peanut butter" to "260 g creamy peanut butter",
                "1 cup plain yogurt" to "245 g plain yogurt",
                "1/2 cup sour cream" to "115 g sour cream",
                "1 c. sugar" to "200 g sugar",
            ),
        )

    @Test
    fun `German cups convert by the German food`() =
        assertConverts(
            MeasureSystem.Metric,
            listOf(
                "1 Tasse Mehl" to "120 g Mehl",
                "1/2 Tasse brauner Zucker" to "105 g brauner Zucker",
                "1 Tasse zarte Haferflocken" to "90 g zarte Haferflocken",
                "1/2 Tasse Puderzucker" to "55 g Puderzucker",
                "1 Tasse gehackte Walnüsse" to "115 g gehackte Walnüsse",
                "1/2 Tasse geriebener Käse" to "55 g geriebener Käse",
                "1/4 Tasse Honig" to "85 g Honig",
                "2 Tassen Milch" to "480 ml Milch",
            ),
            german,
        )

    @Test
    fun `liquids and foods of unknown weight stay volumes`() =
        assertConverts(
            MeasureSystem.Metric,
            listOf(
                "1 cup milk" to "240 ml milk",
                "1/2 cup heavy cream" to "120 ml heavy cream",
                "4 cups chicken broth" to "950 ml chicken broth",
                "1 cup coconut milk" to "240 ml coconut milk",
                "1/2 cup dry white wine" to "120 ml dry white wine",
                "1/3 cup orange juice" to "80 ml orange juice",
                "8 fl oz water" to "240 ml water",
                "1 pint blueberries" to "480 ml blueberries",
                "1 quart water" to "950 ml water",
                "6 cups water" to "1.45 l water",
                // Ambiguous weights: brands of kosher salt differ twofold, whole walnuts by a quarter.
                "1 cup kosher salt" to "240 ml kosher salt",
                "1 cup walnuts" to "240 ml walnuts",
                "1 cup almond flour" to "240 ml almond flour",
                "1 cup sifted powdered sugar" to "240 ml sifted powdered sugar",
            ),
        )

    @Test
    fun `US weights become grams and kilograms`() =
        assertConverts(
            MeasureSystem.Metric,
            listOf(
                "1/4 oz active dry yeast" to "7 g active dry yeast",
                "1/2 oz butter" to "15 g butter",
                "1 oz dark chocolate" to "30 g dark chocolate",
                "2 oz pancetta" to "55 g pancetta",
                "4 oz feta" to "115 g feta",
                "8 oz cream cheese" to "225 g cream cheese",
                "12 oz chocolate chips" to "340 g chocolate chips",
                "1 lb ground beef" to "450 g ground beef",
                "1 1/2 lbs potatoes" to "675 g potatoes",
                "2 pounds chicken thighs" to "900 g chicken thighs",
                "2 1/2 lb flour" to "1.15 kg flour",
                "5 lb potatoes" to "2.25 kg potatoes",
                "1 stick butter" to "115 g butter",
                "2 sticks unsalted butter" to "225 g unsalted butter",
            ),
        )

    @Test
    fun `a German Pfund is 500 g`() {
        assertEquals("500 g Hackfleisch", free("1 Pfund Hackfleisch", MeasureSystem.Metric, locale = german))
        assertEquals("250 g Butter", free("1/2 Pfund Butter", MeasureSystem.Metric, locale = german))
        assertEquals("17 ½ oz Kartoffeln", free("1 Pfund Kartoffeln", MeasureSystem.US, locale = german))
    }

    @Test
    fun `decimals follow the locale`() {
        assertEquals("1,15 kg Kartoffeln", free("2 1/2 lb Kartoffeln", MeasureSystem.Metric, locale = german))
        assertEquals("2 ¼ lb Kartoffeln", free("1 kg Kartoffeln", MeasureSystem.US, locale = german))
        assertEquals("1 ½ qt Wasser", free("1,5 l Wasser", MeasureSystem.US, locale = german))
    }

    @Test
    fun `metric weights become ounces and pounds`() =
        assertConverts(
            MeasureSystem.US,
            listOf(
                "7 g Hefe" to "¼ oz Hefe",
                "15 g Butter" to "½ oz Butter",
                "30 g Butter" to "1 oz Butter",
                "100 g Zartbitterschokolade" to "3 ½ oz Zartbitterschokolade",
                "125 g Butter" to "4 ½ oz Butter",
                "200 g Zucker" to "7 oz Zucker",
                "250 g Mehl" to "8 ¾ oz Mehl",
                "300 g Spaghetti" to "10 ½ oz Spaghetti",
                "450 g Hackfleisch" to "1 lb Hackfleisch",
                "500 g Hackfleisch" to "17 ½ oz Hackfleisch",
                "1 kg Kartoffeln" to "2 ¼ lb Kartoffeln",
                "2 kg Kartoffeln" to "4 ½ lb Kartoffeln",
            ),
        )

    @Test
    fun `metric volumes become cups, spoons and quarts`() =
        assertConverts(
            MeasureSystem.US,
            listOf(
                "10 ml Zitronensaft" to "2 tsp Zitronensaft",
                "15 ml Essig" to "1 tbsp Essig",
                "2 cl Rum" to "4 tsp Rum",
                "50 ml Öl" to "3 ½ tbsp Öl",
                "100 ml Wasser" to "6 ½ tbsp Wasser",
                "1 dl Rahm" to "6 ½ tbsp Rahm",
                "125 ml Milch" to "½ cup Milch",
                "250 ml Milch" to "1 cup Milch",
                "400 ml Kokosmilch" to "1 ⅔ cups Kokosmilch",
                "500 ml Brühe" to "2 cups Brühe",
                "750 ml Weißwein" to "3 cups Weißwein",
                "1 l Milch" to "1 qt Milch",
                "2 l Wasser" to "2 qt Wasser",
                // No cup or spoon measures these within 5 %; the jug's fluid ounces do.
                "150 ml Milch" to "5 fl oz Milch",
                "200 ml Sahne" to "6 ½ fl oz Sahne",
            ),
        )

    @Test
    fun `amounts without a clean US measure stay metric`() =
        assertKeeps(MeasureSystem.US, listOf("5 g Salz", "20 g Butter", "40 ml Sahne", "500 mg Safran"))

    @Test
    fun `ranges convert both ends in one unit`() {
        assertEquals("240–360 g flour", free("2-3 cups flour", MeasureSystem.Metric))
        assertEquals("240–480 ml milk", free("1–2 cups milk", MeasureSystem.Metric))
        assertEquals("7–10 ½ oz Mehl", free("200-300 g Mehl", MeasureSystem.US))
        assertEquals("1–2 qt Wasser", free("1-2 l Wasser", MeasureSystem.US))
        assertEquals("3 ½–6 ½ fl oz Sahne", free("100-200 ml Sahne", MeasureSystem.US))
    }

    @Test
    fun `scaling happens before converting`() {
        assertEquals("240 g flour", free("1 cup flour", MeasureSystem.Metric, scale = 2.0))
        assertEquals("225 g butter", free("1/2 cup butter", MeasureSystem.Metric, scale = 2.0))
        assertEquals("675 g beef", free("1 lb beef", MeasureSystem.Metric, scale = 1.5))
        assertEquals("1 cup Milch", free("125 ml Milch", MeasureSystem.US, scale = 2.0))
        assertEquals("60 g flour", free("1 cup flour", MeasureSystem.Metric, scale = 0.5))
    }

    @Test
    fun `spoons, counts and amounts in the target system stay as written`() {
        assertKeeps(
            MeasureSystem.Metric,
            listOf(
                "2 tbsp butter",
                "1 tsp salt",
                "1 pinch salt",
                "3 eggs",
                "2 cloves garlic",
                "1 can tomatoes",
                "1 stick cinnamon",
                "250 g flour",
            ),
        )
        assertKeeps(
            MeasureSystem.US,
            listOf("2 EL Olivenöl", "1 TL Salz", "1 Prise Salz", "1 Bund Petersilie", "2 cups flour", "1 lb beef"),
        )
        assertEquals("Salz und Pfeffer", free("Salz und Pfeffer", MeasureSystem.US))
    }

    @Test
    fun `a line that gives a second amount stays as written`() {
        assertKeeps(
            MeasureSystem.Metric,
            listOf(
                "1 cup (240 ml) milk",
                "2 cups (250 g) flour",
                "1 lb 4 oz flour",
                "1 cup plus 2 tablespoons flour",
                "1 cup sugar (or 3/4 cup honey)",
            ),
        )
        assertKeeps(MeasureSystem.US, listOf("250 g (2 cups) flour"))
        assertEquals("225 g (2 sticks) butter", free("1 cup (2 sticks) butter", MeasureSystem.Metric))
    }

    @Test
    fun `as written changes nothing`() {
        assertKeeps(MeasureSystem.AsWritten, listOf("1 cup flour", "250 g Mehl", "1 Pfund Kartoffeln"))
        assertEquals("1 cup flour", parsed(1.0, unit("cup", 1.0, "cup"), food("flour"), MeasureSystem.AsWritten))
    }

    @Test
    fun `Mealie's standard units convert`() {
        val cup = unit("cup", 1.0, "cup")
        assertEquals("120 g flour", parsed(1.0, cup, food("flour"), MeasureSystem.Metric))
        assertEquals("240 ml milk", parsed(1.0, cup, food("milk"), MeasureSystem.Metric))
        assertEquals("1 cup flour", parsed(1.0, cup, food("flour"), MeasureSystem.US))
        assertEquals("480 ml milk", parsed(1.0, unit("pint", 2.0, "cup"), food("milk"), MeasureSystem.Metric))
        assertEquals("30 g cheddar", parsed(1.0, unit("ounce", 1.0, "ounce"), food("cheddar"), MeasureSystem.Metric))
        assertEquals("8 ¾ oz Mehl", parsed(250.0, unit("Gramm", 1.0, "gram"), food("Mehl"), MeasureSystem.US, locale = german))
        assertEquals("2 ¼ lb Kartoffeln", parsed(1.0, unit("Kilogramm", 1.0, "kilogram"), food("Kartoffeln"), MeasureSystem.US))
        assertEquals("1 qt Milch", parsed(1.0, unit("Liter", 1.0, "liter"), food("Milch"), MeasureSystem.US))
        assertEquals("4 tsp Rum", parsed(2.0, unit("cl", 10.0, "milliliter"), food("Rum"), MeasureSystem.US))
    }

    @Test
    fun `a standard makes any unit convertible`() {
        val cream = unit("Becher", 200.0, "milliliter")
        assertEquals("6 ½ fl oz Sahne", parsed(1.0, cream, food("Sahne"), MeasureSystem.US))
    }

    @Test
    fun `Mealie's spoons stay spoons`() {
        val tablespoon = unit("tablespoon", 0.5, "fluid_ounce")
        val teaspoon = unit("teaspoon", 1.0 / 6, "fluid_ounce")
        assertEquals("2 tablespoon olive oil", parsed(2.0, tablespoon, food("olive oil"), MeasureSystem.Metric))
        assertEquals("1 teaspoon salt", parsed(1.0, teaspoon, food("salt"), MeasureSystem.Metric))
    }

    @Test
    fun `units of servers without standards are known by name`() {
        assertEquals("120 g flour", parsed(1.0, unit("cup"), food("flour"), MeasureSystem.Metric))
        assertEquals("500 g Kartoffeln", parsed(1.0, unit("Pfund"), food("Kartoffeln"), MeasureSystem.Metric))
        assertEquals("115 g butter", parsed(1.0, unit("stick"), food("butter"), MeasureSystem.Metric))
        assertEquals("1 Esslöffel Öl", parsed(1.0, unit("Esslöffel"), food("Öl"), MeasureSystem.US))
        assertEquals("1 Prise Salz", parsed(1.0, unit("Prise"), food("Salz"), MeasureSystem.US))
    }

    @Test
    fun `the note says how a food is cut`() {
        val cup = unit("cup", 1.0, "cup")
        assertEquals("85 g almonds sliced", parsed(1.0, cup, food("almonds"), MeasureSystem.Metric, note = "sliced"))
        assertEquals("140 g almonds toasted", parsed(1.0, cup, food("almonds"), MeasureSystem.Metric, note = "toasted"))
        assertEquals("240 ml almonds chopped", parsed(1.0, cup, food("almonds"), MeasureSystem.Metric, note = "chopped"))
        assertEquals("115 g Walnuss gehackt", parsed(1.0, cup, food("Walnuss", "Walnüsse"), MeasureSystem.Metric, note = "gehackt"))
    }

    @Test
    fun `a note that gives the amount keeps the line as written`() =
        assertEquals("1 cup flour (125 g)", parsed(1.0, unit("cup", 1.0, "cup"), food("flour"), MeasureSystem.Metric, note = "(125 g)"))

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
