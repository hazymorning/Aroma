package app.aroma.screenshots

import app.aroma.core.model.Ingredient
import app.aroma.core.model.IngredientFood
import app.aroma.core.model.IngredientUnit
import app.aroma.core.model.Organizer
import app.aroma.core.model.Recipe
import app.aroma.core.model.RecipeImage
import app.aroma.core.model.RecipeNote
import app.aroma.core.model.RecipeSummary
import app.aroma.core.model.RecipeTime
import app.aroma.core.model.Step

/**
 * The test texts and cases of the brief (docs/design/AUFTRAG.md, section 14), in German: long
 * hyphenated titles and the shortest one, times as Mealie keeps them, ingredients without an amount,
 * a recipe with nothing but a name, and one with 30 ingredients in four groups.
 */
object KochbuchSamples {
    private fun image(id: String) = RecipeImage(SampleData.SERVER, id, "v1")

    private fun organizer(name: String) = Organizer(name.lowercase(), name, name.lowercase())

    /** The titles of section 14, longest breaks first. */
    val titles =
        listOf(
            "Zitronen-Baiser-Plätzchen vom Blech",
            "Cremiges Auberginen-Tikka-Masala mit Kokos-Joghurt",
            "Koreanische BBQ-Hähnchen-Wraps",
            "Kürbis-Couscous-Taler mit Feta-Füllung",
            "No-Bake Bienenstich-Tiramisu",
            "Rhabarber-Erdbeer-Streuselkuchenschnitten",
            "Ei",
        )

    /** The times of section 14, as Mealie sends them: an ISO duration, free text, or nothing. */
    private val times =
        listOf(
            RecipeTime(null, "PT1H20M"),
            RecipeTime(null, "10–15 Minuten"),
            RecipeTime(null, "45 min"),
            RecipeTime(null, "ca. 2 Std."),
            null,
            RecipeTime(null, "45 min"),
            null,
        )

    private val kinds = listOf("Backen", "Abendessen", "Abendessen", "Mittagessen", "Dessert", "Backen", "Frühstück")

    /** A card for each title, with its time, or its kind where it has none. */
    val summaries =
        titles.mapIndexed { index, title ->
            RecipeSummary(
                id = "k$index",
                slug = "k$index",
                name = title,
                description = "",
                image = if (title == "Ei") null else image("k$index"),
                totalTime = times[index],
                prepTime = null,
                cookTime = null,
                servings = 4.0,
                rating = null,
                categories = listOf(organizer(kinds[index])),
                tags = emptyList(),
            )
        }

    private val gram = unit("g", "Gramm", "g", fraction = false)
    private val teaspoon = unit("tsp", "Teelöffel", "TL", fraction = true)
    private val tablespoon = unit("tbsp", "Esslöffel", "EL", fraction = true)
    private val milliliter = unit("ml", "Milliliter", "ml", fraction = false)
    private val pinch = unit("pinch", "Prise", "Prise", fraction = false)

    private fun unit(id: String, name: String, abbreviation: String, fraction: Boolean) =
        IngredientUnit(id, name, null, abbreviation, null, useAbbreviation = true, useFraction = fraction)

    private fun parsed(
        quantity: Double,
        unit: IngredientUnit?,
        food: String,
        note: String = "",
        section: String? = null,
    ) = Ingredient(null, section, quantity, unit, IngredientFood(null, food, null), note, "", null)

    private fun text(note: String, section: String? = null) = Ingredient(null, section, 0.0, null, null, note, note, null)

    /** 30 ingredients in four groups, with the four ingredients of section 14 among them. */
    private val ingredients =
        listOf(
            parsed(250.0, gram, "Mehl", section = "Mürbeteig"),
            parsed(125.0, gram, "Butter", note = "kalt, in Würfeln"),
            parsed(80.0, gram, "Zucker"),
            parsed(1.0, null, "Ei"),
            parsed(1.0, pinch, "Salz"),
            text("Abrieb von 1–2 Bio-Zitronen"),
            parsed(0.5, teaspoon, "Backpulver"),
            parsed(2.0, tablespoon, "Milch"),
            parsed(150.0, gram, "Zucker", section = "Zitronencreme"),
            parsed(3.0, null, "Eigelb"),
            parsed(120.0, milliliter, "Zitronensaft", note = "frisch gepresst"),
            parsed(2.0, tablespoon, "Speisestärke"),
            parsed(60.0, gram, "Butter"),
            text("Salz"),
            parsed(200.0, milliliter, "Wasser"),
            parsed(1.0, teaspoon, "Vanilleextrakt"),
            parsed(3.0, null, "Eiweiß", section = "Baiser"),
            parsed(150.0, gram, "Puderzucker"),
            parsed(1.0, pinch, "Salz"),
            parsed(1.0, teaspoon, "Zitronensaft"),
            parsed(0.5, teaspoon, "Speisestärke"),
            text("1 Prise Weinstein"),
            parsed(2.0, tablespoon, "Mandelblättchen", section = "Zum Bestreuen"),
            parsed(1.0, tablespoon, "Puderzucker"),
            text("Zitronenmelisse nach Belieben"),
            parsed(1.0, teaspoon, "Pistazien", note = "gehackt"),
            text("Abrieb von 1 Bio-Limette"),
            parsed(50.0, gram, "weiße Schokolade"),
            parsed(1.0, tablespoon, "gefriergetrocknete Himbeeren"),
            text("Salz"),
        )

    private fun step(id: String, text: String, title: String? = null) = Step(id, title, text, emptyList(), null)

    /** A recipe with all there is: photo, rating, source, headnote, times, 30 ingredients, steps, notes and tools. */
    val full =
        Recipe(
            id = "k0",
            slug = "k0",
            name = titles[0],
            description =
                "Mürber Boden, säuerliche Zitronencreme und eine Haube aus Baiser, die beim Schneiden leise knistert. " +
                    "Vom Blech schneidet man 24 kleine Stücke, die sich gut verschenken lassen.",
            image = image("k0"),
            totalTime = RecipeTime(null, "PT1H20M"),
            prepTime = RecipeTime(null, "45 min"),
            cookTime = RecipeTime(null, "35 Minuten"),
            servings = 4.0,
            rating = 4.5,
            categories = listOf(organizer("Backen")),
            tags = listOf(organizer("Weihnachten"), organizer("Plätzchen"), organizer("Vegetarisch")),
            tools = listOf(organizer("Backblech"), organizer("Handrührgerät"), organizer("Spritzbeutel")),
            sourceUrl = "https://www.chefkoch-blog.de/rezepte/zitronen-baiser",
            ingredients = ingredients,
            steps =
                listOf(
                    step(
                        "s1",
                        "Mehl, Butter, Zucker, Ei und Salz rasch zu einem glatten Teig verkneten, in Folie wickeln und 30 Minuten kalt stellen.",
                    ),
                    step(
                        "s2",
                        "Den Ofen auf 180 °C Ober- und Unterhitze vorheizen. Den Teig auf dem Blech ausrollen und 15 Minuten blind backen.",
                    ),
                    step(
                        "s3",
                        "Zucker, Eigelb, Zitronensaft und Stärke unter Rühren aufkochen, bis die Creme bindet; die Butter unterrühren.",
                        "Creme",
                    ),
                    step("s4", "Eiweiß mit Salz steif schlagen, Puderzucker einrieseln lassen und weiterschlagen, bis der Baiser glänzt."),
                    step("s5", "Creme auf den Boden streichen, Baiser aufspritzen und weitere 20 Minuten backen."),
                    step("s6", "Auskühlen lassen, in 24 Stücke schneiden und mit Mandelblättchen bestreuen."),
                    step("s7", "In einer Dose halten sie sich drei Tage."),
                    step("s8", "Reste der Creme schmecken auf Joghurt."),
                    step("s9", "Wer mag, gibt zum Schluss gehackte Pistazien darüber."),
                    step("s10", "Mit Puderzucker bestäuben und servieren."),
                ),
            notes = listOf(RecipeNote("Tipp", "Der Baiser wird besonders fest, wenn das Eiweiß einen Tag alt ist.")),
            nutrition = emptyList(),
            yieldText = "ca. 24 Stück",
        )

    /** A recipe that is nothing but its name: no photo, no times, no headnote, no rating. */
    val bare =
        Recipe(
            id = "k6",
            slug = "k6",
            name = "Ei",
            description = "",
            image = null,
            totalTime = null,
            prepTime = null,
            cookTime = null,
            servings = 0.0,
            rating = null,
            categories = emptyList(),
            tags = emptyList(),
            tools = emptyList(),
            sourceUrl = null,
            ingredients = listOf(parsed(1.0, null, "Ei"), text("Salz")),
            steps = listOf(step("e1", "Das Ei in kochendes Wasser legen und 7 Minuten garen.")),
            notes = emptyList(),
            nutrition = emptyList(),
        )

    /** The longest title, with "1 Blech" as its yield, as Mealie 2 kept servings. */
    val longTitle =
        full.copy(
            id = "k1",
            slug = "k1",
            name = titles[1],
            image = image("k1"),
            rating = null,
            categories = listOf(organizer("Abendessen")),
            tags = listOf(organizer("Indisch")),
            sourceUrl = null,
            servings = 1.0,
            yieldText = "1 Blech",
            prepTime = RecipeTime(null, "10–15 Minuten"),
            cookTime = RecipeTime(null, "ca. 2 Std."),
            totalTime = null,
        )
}
