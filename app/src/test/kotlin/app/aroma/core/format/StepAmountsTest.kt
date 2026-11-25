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
            .mapIndexed { index, amounts -> write(steps[index], amounts) }

    /** [text] with [amounts] written in, as plain text. */
    private fun write(text: String, amounts: List<StepAmount>): String =
        buildString {
            var position = 0
            amounts.sortedBy { it.start }.forEach {
                append(text, position, it.start).append(it.before).append(it.line.measureText()).append(it.then)
                position = it.end
            }
            append(text, position, text.length)
        }

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
    fun `after an adjective the amount follows the noun in brackets`() {
        // A German adjective after an article would keep an ending that no longer fits: not "50 g gehackten Walnüsse".
        assertEquals(
            listOf("Die gehackten Walnüsse (50 g) unterheben.", "In das kalte Wasser (150 ml) bröckeln."),
            german(
                listOf("50 g Walnüsse, gehackt", "150 ml Wasser"),
                "Die gehackten Walnüsse unterheben.",
                "In das kalte Wasser bröckeln.",
            ),
        )
        assertEquals(listOf("Add the cold butter (30 g)."), english(listOf("30 g butter"), "Add the cold butter."))
        assertEquals(
            listOf("In einer Schüssel den weißen Käse (250 g) mit den Kräutern mischen."),
            german(listOf("250 g weicher weißer Käse"), "In einer Schüssel den weißen Käse mit den Kräutern mischen."),
        )
        // A share before the article still keeps the amount out.
        val half = "Die Hälfte der gehackten Walnüsse unterheben."
        assertEquals(listOf(half), german(listOf("50 g Walnüsse, gehackt"), half))
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
    fun `a later part of an ingredient keeps the amount out of every step`() {
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
        val steps = listOf("Öl in einer Pfanne erhitzen.", "Zum Schluss 1 EL Öl unterrühren.")
        assertEquals(steps, german(listOf("3 EL Öl"), *steps.toTypedArray()))
        val english = listOf("Heat the oil.", "Drizzle with a little oil.")
        assertEquals(english, english(listOf("3 tbsp oil"), *english.toTypedArray()))
    }

    @Test
    fun `a later share of all of it or none leaves the first naming its amount`() {
        assertEquals(
            listOf("50 g Butter schmelzen.", "Die Hälfte der Butter in die Form geben."),
            german(listOf("50 g Butter"), "Butter schmelzen.", "Die Hälfte der Butter in die Form geben."),
        )
        assertEquals(
            listOf("500 g Mehl und Salz mischen.", "Kein zusätzliches Mehl verwenden."),
            german(listOf("500 g Mehl"), "Mehl und Salz mischen.", "Kein zusätzliches Mehl verwenden."),
        )
    }

    @Test
    fun `named again in a step, it gets its amount the first time`() {
        val steps = listOf("Zwiebeln würfeln.", "Ghee erhitzen und Zwiebeln glasig braten.")
        assertEquals(
            listOf("2 Zwiebeln würfeln.", "2 EL Ghee erhitzen und Zwiebeln glasig braten."),
            german(listOf("2 Zwiebeln", "2 EL Ghee"), *steps.toTypedArray()),
        )
        val english = listOf("Dice the onions.", "Fry the onions until soft.")
        assertEquals(listOf("Dice 2 onions.", english[1]), english(listOf("2 onions"), *english.toTypedArray()))
        assertEquals(
            listOf("2 Zwiebeln fein würfeln. Knoblauch hacken. Zwiebeln glasig dünsten."),
            german(listOf("2 Zwiebeln"), "Zwiebeln fein würfeln. Knoblauch hacken. Zwiebeln glasig dünsten."),
        )
        // What the first step made of it may come up later.
        assertEquals(
            listOf("250 g Paneer in Würfel schneiden.", "Paneerwürfel in der Sauce erwärmen."),
            german(listOf("250 g Paneer"), "Paneer in Würfel schneiden.", "Paneerwürfel in der Sauce erwärmen."),
        )
        val slaw = listOf("Gurke und Karotten mischen.", "Mit Gurken-Slaw servieren.")
        assertEquals(
            listOf("300 g Gurke und 200 g Karotten mischen.", slaw[1]),
            german(listOf("300 g Gurke", "200 g Karotten"), *slaw.toTypedArray()),
        )
    }

    @Test
    fun `a count names its own noun in the same number, or follows another in brackets`() {
        val eggs = listOf(parsed(2.0, "Ei", "Eier"))
        assertEquals(listOf("4 Eier verquirlen."), written(eggs, listOf("Eier verquirlen."), scale = 2.0))
        // Halved, the list reads "1 Ei", which "Eier" does not fit.
        assertEquals(listOf("Eier verquirlen."), written(eggs, listOf("Eier verquirlen."), scale = 0.5))
        // Where the step names the food more broadly than the count, the list's own words say what is counted.
        assertEquals(listOf("Knoblauch (2 Knoblauchzehen) hacken."), german(listOf("2 Knoblauchzehen"), "Knoblauch hacken."))
    }

    @Test
    fun `the step must name what the amount measures`() {
        assertEquals(listOf("Squeeze in the lemon juice."), english(listOf("1 lemon"), "Squeeze in the lemon juice."))
        assertEquals(listOf("Add the lemon."), english(listOf("1 tbsp lemon juice"), "Add the lemon."))
        assertEquals(listOf("Mit Limettensaft abschmecken."), german(listOf("1 Limette"), "Mit Limettensaft abschmecken."))
        assertEquals(listOf("250 g Spinat waschen."), german(listOf("250 g frischer Blattspinat (oder TK-Spinat)"), "Spinat waschen."))
    }

    @Test
    fun `each food of a line with two takes its own amount`() {
        assertEquals(
            listOf("Knoblauch (2 Knoblauchzehen) und 2 cm Ingwer fein hacken. Knoblauch und Ingwer dazugeben."),
            german(
                listOf("2 Knoblauchzehen und 2 cm frischer Ingwer"),
                "Knoblauch und Ingwer fein hacken. Knoblauch und Ingwer dazugeben.",
            ),
        )
        // A word that names both of two choices is followed by both, each scaled.
        assertEquals(
            listOf("Tomaten (4 Tomaten oder 200 g passierte Tomaten) dazugeben."),
            written(listOf(text("2 Tomaten oder 100 g passierte Tomaten")), listOf("Tomaten dazugeben."), scale = 2.0),
        )
        // A food without an amount of its own takes none: the 3 EL may be ghee or oil.
        assertEquals(listOf("Öl erhitzen."), german(listOf("3 EL Ghee oder Öl"), "Öl erhitzen."))
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
