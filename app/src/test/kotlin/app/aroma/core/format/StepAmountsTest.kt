package app.aroma.core.format

import app.aroma.core.model.Ingredient
import app.aroma.core.model.IngredientFood
import app.aroma.core.model.IngredientUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class StepAmountsTest {
    private fun text(line: String) = Ingredient(null, null, 0.0, null, null, line, line, null)

    private fun parsed(quantity: Double, food: String, plural: String? = null, unit: IngredientUnit? = null) =
        Ingredient(null, null, quantity, unit, IngredientFood(null, food, plural), "", "", null)

    /** The [steps] with the amounts of [ingredients] written in. */
    private fun written(
        ingredients: List<Ingredient>,
        steps: List<String>,
        scale: Double = 1.0,
        system: MeasureSystem = MeasureSystem.AsWritten,
        uses: List<List<Int>>? = null,
        locale: Locale = Locale.GERMANY,
    ): List<String> =
        StepAmounts
            .of(ingredients, steps, uses, scale, locale, system)
            .mapIndexed { index, amounts -> StepAmounts.write(steps[index], amounts) }

    private fun german(lines: List<String>, vararg steps: String) = written(lines.map(::text), steps.toList())

    private fun english(lines: List<String>, vararg steps: String) = written(lines.map(::text), steps.toList(), locale = Locale.US)

    @Test
    fun `a step names the amount of what it cooks`() {
        assertEquals(listOf("75 g Couscous kochen."), german(listOf("75 g Couscous"), "Couscous kochen."))
        assertEquals(
            listOf("In einer Schüssel 250 g Mehl, 2 EL Zucker und 1 Prise Salz mischen."),
            german(listOf("250 g Mehl", "2 EL Zucker", "1 Prise Salz"), "In einer Schüssel Mehl, Zucker und Salz mischen."),
        )
        assertEquals(
            listOf("Mix 250 g flour, 2 tbsp sugar and 1 pinch salt in a bowl."),
            english(listOf("250 g flour", "2 tbsp sugar", "1 pinch salt"), "Mix flour, sugar and salt in a bowl."),
        )
    }

    @Test
    fun `an amount takes the place of the article right before its noun`() {
        assertEquals(
            listOf("1 Zwiebel fein hacken, dabei 240 g Zucker einrieseln lassen und mit 200 ml Sahne verrühren."),
            german(
                listOf("1 Zwiebel", "240 g Zucker", "200 ml Sahne"),
                "Die Zwiebel fein hacken, dabei den Zucker einrieseln lassen und mit der Sahne verrühren.",
            ),
        )
        assertEquals(
            listOf("Whisk 2 eggs, then add 30 g melted butter."),
            english(listOf("2 eggs", "30 g butter, melted"), "Whisk the eggs, then add the melted butter."),
        )
    }

    @Test
    fun `words of the line before the noun move behind the amount`() {
        assertEquals(listOf("100 g weiße Schokolade schmelzen."), german(listOf("100 g weiße Schokolade"), "Weiße Schokolade schmelzen."))
        assertEquals(
            listOf("Mit 50 g gehackten Walnüssen bestreuen."),
            german(listOf("50 g Walnüsse, gehackt"), "Mit gehackten Walnüssen bestreuen."),
        )
        assertEquals(
            listOf("Heat 2 tbsp olive oil in a pan."),
            english(listOf("2 tbsp extra virgin olive oil"), "Heat the olive oil in a pan."),
        )
    }

    @Test
    fun `a German adjective after an article keeps the step as it is`() {
        val steps = listOf("Die gehackten Walnüsse unterheben.", "In das kalte Wasser bröckeln.")
        assertEquals(steps, german(listOf("50 g Walnüsse, gehackt", "150 ml Wasser"), *steps.toTypedArray()))
        assertEquals(listOf("Add the cold butter."), english(listOf("30 g butter"), "Add the cold butter."))
    }

    @Test
    fun `an amount or a share in the step keeps it as it is`() {
        val german =
            listOf(
                "200 g Mehl sieben.",
                "In etwas Butter anbraten.",
                "Die Hälfte der Sahne steif schlagen.",
                "Den Saft der Zitrone auspressen.",
                "Einen Schuss Milch angießen.",
                "Eine Prise Salz zugeben.",
                "Zwei Eier verquirlen.",
                "Kein zusätzliches Rapsöl verwenden.",
            )
        val lines = listOf("200 g Mehl", "50 g Butter", "200 ml Sahne", "1 Zitrone", "100 ml Milch", "1 TL Salz", "2 Eier", "2 EL Rapsöl")
        german.forEach { step -> assertEquals(listOf(step), german(lines, step)) }
        val english =
            listOf(
                "Sift 200 g flour.",
                "Add the remaining butter.",
                "Add half the cream.",
                "Add a handful of salt.",
                "Add some of the milk.",
                "Beat two eggs.",
            )
        val englishLines = listOf("200 g flour", "50 g butter", "200 ml cream", "1 tsp salt", "100 ml milk", "2 eggs")
        english.forEach { step -> assertEquals(listOf(step), english(englishLines, step)) }
    }

    @Test
    fun `a share anywhere keeps the amount out of every step`() {
        assertEquals(
            listOf("Butter schmelzen.", "2 Zwiebeln darin andünsten.", "Die restliche Butter unterrühren."),
            german(
                listOf("50 g Butter", "2 Zwiebeln"),
                "Butter schmelzen.",
                "Die Zwiebeln darin andünsten.",
                "Die restliche Butter unterrühren.",
            ),
        )
        val step = "Butter schmelzen und die restliche Butter unterrühren."
        assertEquals(listOf(step), german(listOf("50 g Butter"), step))
    }

    @Test
    fun `an ingredient named in two steps gets no amount`() {
        val steps = listOf("Butter schmelzen.", "Die Form mit Butter einfetten.")
        assertEquals(steps, german(listOf("50 g Butter"), *steps.toTypedArray()))
        val english = listOf("Melt the butter.", "Brush the pan with butter.")
        assertEquals(english, english(listOf("50 g butter"), *english.toTypedArray()))
    }

    @Test
    fun `named twice in one step, it gets its amount the first time`() {
        assertEquals(
            listOf("2 Zwiebeln fein würfeln. Knoblauch hacken. Zwiebeln glasig dünsten."),
            german(listOf("2 Zwiebeln"), "Zwiebeln fein würfeln. Knoblauch hacken. Zwiebeln glasig dünsten."),
        )
    }

    @Test
    fun `what the first step made of an ingredient may come up later`() {
        assertEquals(
            listOf("250 g Paneer in Würfel schneiden.", "Paneerwürfel in der Sauce erwärmen."),
            german(listOf("250 g Paneer"), "Paneer in Würfel schneiden.", "Paneerwürfel in der Sauce erwärmen."),
        )
        // The slaw was not made in the first step by that name: the cucumber may be needed again.
        val steps = listOf("Gurke und Karotten mischen.", "Mit Gurken-Slaw servieren.")
        assertEquals(
            listOf("Gurke und 200 g Karotten mischen.", steps[1]),
            german(listOf("300 g Gurke", "200 g Karotten"), *steps.toTypedArray()),
        )
    }

    @Test
    fun `a count names its own noun in the same number`() {
        val eggs = listOf(parsed(2.0, "Ei", "Eier"))
        assertEquals(listOf("2 Eier verquirlen."), written(eggs, listOf("Eier verquirlen.")))
        assertEquals(listOf("Mit 2 Eiern verquirlen."), written(eggs, listOf("Mit Eiern verquirlen.")))
        // Halved, the list reads "1 Ei", which "Eier" does not fit.
        assertEquals(listOf("Eier verquirlen."), written(eggs, listOf("Eier verquirlen."), scale = 0.5))
        assertEquals(listOf("4 Eier verquirlen."), written(eggs, listOf("Eier verquirlen."), scale = 2.0))
        assertEquals(listOf("Zwiebel würfeln."), german(listOf("2 Zwiebeln"), "Zwiebel würfeln."))
        assertEquals(listOf("Knoblauch hacken."), german(listOf("1 Knolle Knoblauch"), "Knoblauch hacken."))
        assertEquals(listOf("Fry the garlic."), english(listOf("2 garlic cloves"), "Fry the garlic."))
        assertEquals(listOf("Top with ½ banana."), english(listOf("1/2 banana"), "Top with banana."))
    }

    @Test
    fun `the step must name what the amount measures`() {
        assertEquals(listOf("Squeeze in the lemon juice."), english(listOf("1 lemon"), "Squeeze in the lemon juice."))
        assertEquals(listOf("Add the lemon."), english(listOf("1 tbsp lemon juice"), "Add the lemon."))
        assertEquals(listOf("Mit Limettensaft abschmecken."), german(listOf("1 Limette"), "Mit Limettensaft abschmecken."))
        assertEquals(listOf("Tomaten dazugeben."), german(listOf("2 Tomate oder 100 g passierte Tomaten"), "Tomaten dazugeben."))
        assertEquals(listOf("250 g Spinat waschen."), german(listOf("250 g frischer Blattspinat (oder TK-Spinat)"), "Spinat waschen."))
    }

    @Test
    fun `verbs and words built on the food are not the food`() {
        assertEquals(listOf("Leicht salzen und ziehen lassen."), german(listOf("1 TL Salz"), "Leicht salzen und ziehen lassen."))
        assertEquals(listOf("Salt the water."), english(listOf("1 tsp salt"), "Salt the water."))
        assertEquals(listOf("Knoblauchzehen schälen."), german(listOf("20 g Knoblauch"), "Knoblauchzehen schälen."))
    }

    @Test
    fun `nothing without an amount and nothing for two lines of one name`() {
        assertEquals(listOf("Mit Salz und Pfeffer würzen."), german(listOf("Salz und Pfeffer"), "Mit Salz und Pfeffer würzen."))
        val lines = listOf("60 g Honig", "25 g Honig", "200 g Joghurt")
        val steps = listOf("Hähnchen mit Honig marinieren.", "Joghurt und Honig verrühren.")
        assertEquals(listOf(steps[0], "200 g Joghurt und Honig verrühren."), german(lines, *steps.toTypedArray()))
        // Mealie's links tell which honey goes where.
        assertEquals(
            listOf("Hähnchen mit 60 g Honig marinieren.", "200 g Joghurt und 25 g Honig verrühren."),
            written(lines.map(::text), steps, uses = listOf(listOf(0), listOf(1, 2))),
        )
    }

    @Test
    fun `amounts follow the servings and the measures`() {
        assertEquals(listOf("150 g Couscous kochen."), written(listOf(text("75 g Couscous")), listOf("Couscous kochen."), scale = 2.0))
        assertEquals(
            listOf("Warm 1 cup milk."),
            written(listOf(text("250 ml milk")), listOf("Warm the milk."), system = MeasureSystem.US, locale = Locale.US),
        )
        val tablespoon = IngredientUnit("u", "Esslöffel", null, "EL", null, useAbbreviation = true, useFraction = true)
        assertEquals(
            listOf("1 ½ EL Senf unterrühren."),
            written(listOf(parsed(1.0, "Senf", unit = tablespoon)), listOf("Senf unterrühren."), scale = 1.5),
        )
    }

    @Test
    fun `units written short and yeast in cubes count as units`() {
        assertEquals(
            listOf("½ Würfel Hefe in Wasser bröckeln.", "Mehl und 1 Teel. Salz mischen.", "Eine Schüssel mit 1 Essl. Olivenöl einölen."),
            german(
                listOf("1/2 Würfel frische Hefe (= ca. 21 g)", "1 Teel. Salz", "1 Essl. Olivenöl (für die Schüssel)"),
                "Hefe in Wasser bröckeln.",
                "Mehl und Salz mischen.",
                "Eine Schüssel mit Olivenöl einölen.",
            ),
        )
    }

    @Test
    fun `temperatures, pan sizes and timers keep working around the amounts`() {
        val step =
            StepMeasures.convert(
                "Den Ofen auf 350 °F vorheizen. Couscous 10 Minuten in einer 9-inch Form backen.",
                MeasureSystem.Metric,
                Locale.GERMANY,
            )
        val written = written(listOf(text("75 g Couscous")), listOf(step), system = MeasureSystem.Metric).single()
        assertEquals("Den Ofen auf 175 °C vorheizen. 75 g Couscous 10 Minuten in einer 23 cm Form backen.", written)
        assertEquals(listOf("10 Minuten"), DurationDetector.detect(written).map { written.substring(it.start, it.end) })
    }
}
