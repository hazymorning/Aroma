package app.aroma.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import app.aroma.core.model.Cookbook
import app.aroma.core.model.CookbookCover
import app.aroma.core.model.Ingredient
import app.aroma.core.model.IngredientFood
import app.aroma.core.model.IngredientUnit
import app.aroma.core.model.MealPlanEntry
import app.aroma.core.model.MealType
import app.aroma.core.model.NutritionKind
import app.aroma.core.model.NutritionValue
import app.aroma.core.model.Organizer
import app.aroma.core.model.Recipe
import app.aroma.core.model.RecipeImage
import app.aroma.core.model.RecipeNote
import app.aroma.core.model.RecipeSummary
import app.aroma.core.model.RecipeTime
import app.aroma.core.model.ShoppingItem
import app.aroma.core.model.ShoppingLabel
import app.aroma.core.model.ShoppingList
import app.aroma.core.model.Step
import app.aroma.feature.mealplan.MealPlanDay
import java.time.LocalDate

/** Where the sample recipes' photos come from; the screenshots draw [photo]s in their place. */
const val SERVER = "https://mealie.test"

/**
 * A soft gradient in place of a recipe's photo, the same for the same [seed], so the images stay
 * alike from run to run and free of third-party content.
 */
fun photo(seed: String, size: Int = 400, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
    val palettes =
        listOf(
            intArrayOf(0xFFB9A27A.toInt(), 0xFF7E8F62.toInt()),
            intArrayOf(0xFFCFAE82.toInt(), 0xFF9A6B45.toInt()),
            intArrayOf(0xFFA9B78F.toInt(), 0xFF5F7353.toInt()),
            intArrayOf(0xFFD8C3A0.toInt(), 0xFFB07B52.toInt()),
        )
    val colors = palettes[Math.floorMod(seed.hashCode(), palettes.size)]
    val side = size.toFloat()
    val bitmap = Bitmap.createBitmap(size, size, config)
    val paint = Paint().apply { shader = LinearGradient(0f, 0f, side, side, colors[0], colors[1], Shader.TileMode.CLAMP) }
    Canvas(bitmap).drawRect(0f, 0f, side, side, paint)
    return bitmap
}

private fun image(id: String) = RecipeImage(SERVER, id, "v1")

private fun organizer(name: String) = Organizer(name.lowercase(), name, name.lowercase())

private fun unit(id: String, name: String, abbreviation: String, fraction: Boolean) =
    IngredientUnit(id, name, null, abbreviation, null, useAbbreviation = true, useFraction = fraction)

private fun parsed(
    quantity: Double,
    unit: IngredientUnit?,
    food: String,
    plural: String? = null,
    note: String = "",
    section: String? = null,
) = Ingredient(null, section, quantity, unit, IngredientFood(null, food, plural), note, "", null)

private fun text(note: String, section: String? = null) = Ingredient(null, section, 0.0, null, null, note, note, null)

private fun step(id: String, text: String, title: String? = null, uses: List<String> = emptyList()) = Step(id, title, text, uses, null)

private fun summary(
    id: String,
    name: String,
    time: RecipeTime?,
    rating: Double? = null,
    categories: List<Organizer> = emptyList(),
    image: RecipeImage? = image(id),
) = RecipeSummary(id, id, name, "", image, time, null, null, 4.0, rating, categories, emptyList())

private fun weekFrom(monday: LocalDate, entries: List<MealPlanEntry>) =
    (0L until 7L).map { offset -> monday.plusDays(offset).let { day -> MealPlanDay(day, entries.filter { it.date == day }) } }

/** Hand-written sample content, in German or English. */
class SampleData(private val german: Boolean) {
    private fun t(de: String, en: String) = if (german) de else en

    private fun minutes(minutes: Int) = RecipeTime(minutes * 60, null)

    val recipes =
        listOf(
            summary("pancakes", t("Fluffige Pancakes mit Ahornsirup", "Fluffy pancakes with maple syrup"), minutes(25), 4.5),
            summary("curry", t("Rote Curry-Suppe mit Kokos", "Red coconut curry soup"), minutes(35), 5.0),
            summary("bowl", t("Buddha-Bowl mit Avocado", "Buddha bowl with avocado"), minutes(20), 4.0),
            summary("pasta", t("Farfalle-Salat mit Pesto", "Farfalle salad with pesto"), minutes(20)),
            summary("salmon", t("Lachs-Bowl mit Sesam", "Salmon bowl with sesame"), minutes(30), 4.5),
            summary("pizza", t("Pizza mit Hähnchen und roten Zwiebeln", "Chicken pizza with red onion"), minutes(45), 4.0),
        )

    /** The household's categories: two meals and two kinds of dish. */
    val categories =
        listOf(
            organizer(t("Frühstück", "Breakfast")),
            organizer(t("Abendessen", "Dinner")),
            organizer(t("Suppen", "Soups")),
            organizer(t("Pasta", "Pasta")),
        )

    val tags = listOf(organizer(t("Vegetarisch", "Vegetarian")), organizer(t("Scharf", "Spicy")))

    val cookbooks =
        listOf(
            Cookbook("c1", "veggie", t("Vegetarisch", "Vegetarian"), t("Alles ohne Fleisch und Fisch", "Everything without meat or fish")),
            Cookbook("c2", "weeknight", t("Unter der Woche", "Weeknights"), t("Schnell auf dem Tisch", "On the table in no time")),
            Cookbook("c3", "brunch", t("Sonntagsfrühstück", "Sunday brunch"), t("Für lange Vormittage", "For slow mornings")),
        )

    val cookbookCovers =
        cookbooks.mapIndexed { index, book -> CookbookCover(book, 12 - 3 * index, recipes[(index * 2 + 1) % recipes.size].image) }

    private val gram = unit("g", t("Gramm", "gram"), "g", fraction = false)
    private val tablespoon = unit("tbsp", t("Esslöffel", "tablespoon"), t("EL", "tbsp"), fraction = true)
    private val teaspoon = unit("tsp", t("Teelöffel", "teaspoon"), t("TL", "tsp"), fraction = true)
    private val milliliter = unit("ml", "Milliliter", "ml", fraction = false)

    val recipe =
        Recipe(
            id = "pancakes",
            slug = "pancakes",
            name = t("Fluffige Pancakes mit Ahornsirup", "Fluffy pancakes with maple syrup"),
            description =
                t(
                    "Dicke, luftige Pancakes wie im Diner. Der Teig ist in fünf Minuten angerührt und braucht nur eine kurze Pause.",
                    "Thick, airy pancakes like in a diner. The batter takes five minutes and only needs a short rest.",
                ),
            image = image("pancakes"),
            totalTime = RecipeTime(1500, null),
            prepTime = RecipeTime(600, null),
            cookTime = RecipeTime(900, null),
            servings = 4.0,
            rating = 4.5,
            categories = listOf(organizer(t("Frühstück", "Breakfast"))),
            tags = listOf(organizer(t("Vegetarisch", "Vegetarian")), organizer(t("Schnell", "Quick"))),
            tools = listOf(organizer(t("Pfanne", "Frying pan"))),
            sourceUrl = "https://example.com/fluffy-pancakes",
            ingredients =
                listOf(
                    parsed(250.0, gram, t("Mehl", "flour"), section = t("Für den Teig", "For the batter")),
                    parsed(2.0, tablespoon, t("Zucker", "sugar")),
                    parsed(1.5, teaspoon, t("Backpulver", "baking powder")),
                    text(t("1 Prise Salz", "1 pinch salt")),
                    parsed(2.0, null, t("Ei", "egg"), t("Eier", "eggs")),
                    parsed(300.0, milliliter, t("Milch", "milk")),
                    parsed(30.0, gram, "Butter", note = t("geschmolzen", "melted")),
                    text(t("Ahornsirup", "Maple syrup"), section = t("Zum Servieren", "To serve")),
                    parsed(0.5, null, t("Banane", "banana"), note = t("in Scheiben", "sliced")),
                ),
            steps =
                listOf(
                    step(
                        "s1",
                        t(
                            "Mehl, Zucker, Backpulver und Salz in einer Schüssel mischen.",
                            "Mix flour, sugar, baking powder and salt in a bowl.",
                        ),
                    ),
                    step(
                        "s2",
                        t(
                            "Eier, Milch und **Butter** verquirlen und kurz unter die trockenen Zutaten rühren. Den Teig 10 Minuten ruhen lassen.",
                            "Whisk eggs, milk and **butter**, then briefly stir into the dry ingredients. Let the batter rest for 10 minutes.",
                        ),
                    ),
                    step(
                        "s3",
                        t(
                            "Eine Pfanne auf mittlerer Stufe erhitzen und je Pancake eine kleine Kelle Teig etwa 2 Minuten pro Seite backen.",
                            "Heat a pan over medium heat and cook a small ladle of batter per pancake for about 2 minutes per side.",
                        ),
                    ),
                    step(
                        "s4",
                        t(
                            "Stapeln, mit Banane belegen und mit Ahornsirup servieren.",
                            "Stack, top with banana and serve with maple syrup.",
                        ),
                    ),
                ),
            notes =
                listOf(
                    RecipeNote(
                        t("Tipp", "Tip"),
                        t(
                            "Für extra luftige Pancakes das Eiweiß getrennt steif schlagen und zum Schluss unterheben.",
                            "For extra airy pancakes, beat the egg whites separately and fold them in last.",
                        ),
                    ),
                ),
            nutrition =
                listOf(
                    NutritionValue(NutritionKind.Calories, "380"),
                    NutritionValue(NutritionKind.Protein, "11"),
                    NutritionValue(NutritionKind.Fat, "10"),
                    NutritionValue(NutritionKind.Carbohydrates, "58"),
                ),
        )

    private fun linked(ingredient: Ingredient, reference: String) = ingredient.copy(referenceId = reference)

    /** A soup whose steps use two, nine, none and two of its ingredients, linked as Mealie links them. */
    val curry =
        Recipe(
            id = "curry",
            slug = "curry",
            name = t("Rote Curry-Suppe mit Kokos", "Red coconut curry soup"),
            description = "",
            image = image("curry"),
            totalTime = RecipeTime(2100, null),
            prepTime = RecipeTime(600, null),
            cookTime = RecipeTime(1500, null),
            servings = 4.0,
            rating = 5.0,
            categories = emptyList(),
            tags = emptyList(),
            tools = emptyList(),
            sourceUrl = null,
            ingredients =
                listOf(
                    linked(parsed(1.0, tablespoon, t("Öl", "oil"), section = t("Für die Suppe", "For the soup")), "oil"),
                    linked(parsed(1.0, null, t("Zwiebel", "onion"), t("Zwiebeln", "onions"), note = t("gewürfelt", "diced")), "onion"),
                    linked(parsed(2.0, null, t("Knoblauchzehe", "garlic clove"), t("Knoblauchzehen", "garlic cloves")), "garlic"),
                    linked(parsed(20.0, gram, t("Ingwer", "ginger"), note = t("gerieben", "grated")), "ginger"),
                    linked(parsed(2.0, tablespoon, t("rote Currypaste", "red curry paste")), "paste"),
                    linked(parsed(400.0, milliliter, t("Kokosmilch", "coconut milk")), "coconut"),
                    linked(parsed(500.0, milliliter, t("Gemüsebrühe", "vegetable stock")), "stock"),
                    linked(parsed(150.0, gram, t("rote Linsen", "red lentils")), "lentils"),
                    linked(parsed(400.0, gram, t("stückige Tomaten", "chopped tomatoes")), "tomatoes"),
                    linked(parsed(100.0, gram, t("Babyspinat", "baby spinach")), "spinach"),
                    linked(
                        parsed(1.0, null, t("Limette", "lime"), t("Limetten", "limes"), section = t("Zum Servieren", "To serve")),
                        "lime",
                    ),
                    linked(text(t("Koriander", "Coriander")), "coriander"),
                ),
            steps =
                listOf(
                    step(
                        "c1",
                        t(
                            "Das Öl in einem großen Topf erhitzen und die Zwiebel darin glasig dünsten.",
                            "Heat the oil in a large pot and soften the onion in it.",
                        ),
                        uses = listOf("oil", "onion"),
                    ),
                    step(
                        "c2",
                        t(
                            "Knoblauch, Ingwer und Currypaste eine Minute mitbraten. Kokosmilch, Brühe und Tomaten angießen, " +
                                "dann Linsen und Spinat dazugeben.",
                            "Fry the garlic, ginger and curry paste with it for a minute. Pour in the coconut milk, stock and tomatoes, " +
                                "then add the lentils and spinach.",
                        ),
                        uses = listOf("onion", "garlic", "ginger", "paste", "coconut", "stock", "lentils", "tomatoes", "spinach"),
                    ),
                    step(
                        "c3",
                        t(
                            "Zugedeckt 20 Minuten sanft köcheln lassen und ab und zu umrühren.",
                            "Cover and simmer gently for 20 minutes, stirring now and then.",
                        ),
                    ),
                    step(
                        "c4",
                        t("Mit Limettensaft abschmecken und mit Koriander servieren.", "Season with lime juice and serve with coriander."),
                        uses = listOf("lime", "coriander"),
                    ),
                ),
            notes = emptyList(),
            nutrition = emptyList(),
        )

    val today: LocalDate = LocalDate.of(2026, 11, 23)

    val mealPlan =
        listOf(
            MealPlanEntry(1, today, MealType.Dinner, "", "", recipes[1]),
            MealPlanEntry(2, today.plusDays(1), MealType.Breakfast, "", "", recipes[0]),
            MealPlanEntry(3, today.plusDays(1), MealType.Dinner, "", "", recipes[4]),
            MealPlanEntry(4, today.plusDays(2), MealType.Lunch, "", "", recipes[2]),
            MealPlanEntry(
                5,
                today.plusDays(3),
                MealType.Dinner,
                t("Reste essen", "Leftovers"),
                t("Was der Kühlschrank hergibt", "Whatever is in the fridge"),
                null,
            ),
            MealPlanEntry(6, today.plusDays(4), MealType.Dinner, "", "", recipes[5]),
            MealPlanEntry(7, today.plusDays(5), MealType.Lunch, "", "", recipes[3]),
        )

    /** The week from [today] on, as the plan shows it. */
    val week = weekFrom(today, mealPlan)

    private val produce = ShoppingLabel("produce", t("Obst & Gemüse", "Produce"))
    private val dairy = ShoppingLabel("dairy", t("Milchprodukte", "Dairy"))
    private val bakery = ShoppingLabel("bakery", t("Backwaren", "Bakery"))
    private val pantry = ShoppingLabel("pantry", t("Vorrat", "Pantry"))

    private fun item(
        id: String,
        note: String,
        label: ShoppingLabel?,
        checked: Boolean = false,
        recipe: String? = null,
    ) = ShoppingItem(id, "list", 0.0, null, null, note, checked, id.removePrefix("i").toInt(), label, listOfNotNull(recipe))

    val shoppingList =
        ShoppingList(
            id = "list",
            name = t("Wocheneinkauf", "Weekly shop"),
            items =
                listOf(
                    item("i1", t("2 Avocados", "2 avocados"), produce),
                    item("i2", t("1 Bund Koriander", "1 bunch coriander"), produce),
                    item("i3", t("2 Bananen", "2 bananas"), produce, recipe = recipes[0].name),
                    item("i4", t("300 g Kirschtomaten", "300 g cherry tomatoes"), produce, checked = true),
                    item("i5", t("300 ml Milch", "300 ml milk"), dairy, recipe = recipes[0].name),
                    item("i6", t("6 Eier", "6 eggs"), dairy),
                    item("i7", t("1 Ciabatta", "1 ciabatta"), bakery),
                    item("i8", t("400 ml Kokosmilch", "400 ml coconut milk"), pantry, recipe = recipes[1].name),
                    item("i9", t("Küchenrolle", "Paper towels"), null, checked = true),
                    item("i10", t("Ahornsirup", "Maple syrup"), null),
                ),
            labelOrder = listOf("produce", "dairy", "bakery", "pantry"),
        )

    /** Photos of a recipe to import: a page of a cookbook and a handwritten card. */
    val importPhotos = listOf("$SERVER/import/cookbook-page.jpg", "$SERVER/import/handwritten.jpg")

    /** A recipe's page and a post with one, to import, and the post's caption. */
    val importLink = t("https://www.chefkoch.de/rezepte/lauchpuffer", "https://www.bbcgoodfood.com/recipes/leek-fritters")
    val importPost = "https://www.instagram.com/p/AromaTest01/"
    val importCaption =
        t(
            "Ofen-Gnocchi mit Kirschtomaten\nZutaten:\n500 g Gnocchi\n250 g Kirschtomaten\n2 EL Olivenöl",
            "Sheet-pan gnocchi with cherry tomatoes\nIngredients:\n500 g gnocchi\n250 g cherry tomatoes\n2 tbsp olive oil",
        )
}

/**
 * German edge cases: long hyphenated titles and the shortest one, times as Mealie keeps them, a
 * recipe with nothing but a name and one with 30 ingredients in four groups, a week under way and
 * one across the turn of the month, and forty things to buy.
 */
object Extremes {
    /** Compound titles that have to break well, and the shortest one. */
    private val titles =
        listOf(
            "Zitronen-Baiser-Plätzchen vom Blech",
            "Cremiges Auberginen-Tikka-Masala mit Kokos-Joghurt",
            "Koreanische BBQ-Hähnchen-Wraps",
            "Kürbis-Couscous-Taler mit Feta-Füllung",
            "No-Bake Bienenstich-Tiramisu",
            "Rhabarber-Erdbeer-Streuselkuchenschnitten",
            "Ei",
        )

    /** Times as Mealie sends them: an ISO duration, free text, or nothing. */
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
    val recipes =
        titles.mapIndexed { index, title ->
            val image = if (title == "Ei") null else image("k$index")
            summary("k$index", title, times[index], categories = listOf(organizer(kinds[index])), image = image)
        }

    private val gram = unit("g", "Gramm", "g", fraction = false)
    private val teaspoon = unit("tsp", "Teelöffel", "TL", fraction = true)
    private val tablespoon = unit("tbsp", "Esslöffel", "EL", fraction = true)
    private val milliliter = unit("ml", "Milliliter", "ml", fraction = false)
    private val pinch = unit("pinch", "Prise", "Prise", fraction = false)

    /** A recipe with all there is: photo, rating, source, headnote, times, 30 ingredients in four groups, steps, notes and tools. */
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
            ingredients =
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
                ),
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
                        title = "Creme",
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

    /** The longest title, with "1 Blech" as its yield, as Mealie 2 kept servings, and its times as free text. */
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

    /** Today is Tuesday, 24 November 2026. */
    val today: LocalDate = LocalDate.of(2026, 11, 24)
    val monday: LocalDate = LocalDate.of(2026, 11, 23)

    /** The Monday of the week across the turn of the month, 30 November to 6 December. */
    val nextMonday: LocalDate = LocalDate.of(2026, 11, 30)

    private fun entry(
        id: Int,
        day: Long,
        type: MealType,
        recipe: Int?,
        title: String = "",
        note: String = "",
        from: LocalDate = monday,
    ) = MealPlanEntry(id, from.plusDays(day), type, title, note, recipe?.let { recipes[it] })

    /** Monday past with two meals, today with a long title and a short one, two empty days, a full Saturday. */
    val entries =
        listOf(
            entry(1, 0, MealType.Lunch, null, title = "Reste vom Sonntag", note = "Was der Kühlschrank hergibt"),
            entry(2, 0, MealType.Dinner, 3),
            entry(3, 1, MealType.Lunch, 6),
            entry(4, 1, MealType.Dinner, 1),
            entry(5, 3, MealType.Dinner, 2),
            entry(6, 5, MealType.Snack, 0),
            entry(7, 5, MealType.Dessert, 4),
            entry(8, 6, MealType.Dinner, 5),
        )

    val week: List<MealPlanDay> = weekFrom(monday, entries)

    val emptyWeek: List<MealPlanDay> = weekFrom(monday, emptyList())

    /** Two meals in the week from 30 November to 6 December. */
    val turnOfMonth: List<MealPlanDay> =
        weekFrom(
            nextMonday,
            listOf(entry(11, 1, MealType.Dinner, 1, from = nextMonday), entry(12, 3, MealType.Lunch, 2, from = nextMonday)),
        )

    private val produce = ShoppingLabel("produce", "Obst & Gemüse")
    private val dairy = ShoppingLabel("dairy", "Milchprodukte")
    private val bakery = ShoppingLabel("bakery", "Backwaren")
    private val pantry = ShoppingLabel("pantry", "Vorrat")

    private val goods =
        listOf(
            "2 Bio-Zitronen" to produce,
            "1 kg Rhabarber" to produce,
            "500 g Erdbeeren" to produce,
            "1 Hokkaido-Kürbis" to produce,
            "2 Auberginen" to produce,
            "1 Bund Koriander" to produce,
            "3 Frühlingszwiebeln" to produce,
            "1 Knolle Ingwer" to produce,
            "200 g Feta" to dairy,
            "500 g Kokos-Joghurt" to dairy,
            "250 g Butter" to dairy,
            "10 Eier" to dairy,
            "400 ml Sahne" to dairy,
            "250 g Mascarpone" to dairy,
            "1 l Milch" to dairy,
            "6 Weizen-Tortillas" to bakery,
            "1 Packung Löffelbiskuits" to bakery,
            "1 Bauernbrot" to bakery,
            "500 g Mehl" to pantry,
            "250 g Zucker" to pantry,
            "150 g Puderzucker" to pantry,
            "1 Päckchen Backpulver" to pantry,
            "200 g Couscous" to pantry,
            "400 ml Kokosmilch" to pantry,
            "2 EL Tikka-Masala-Paste" to pantry,
            "100 g Mandelblättchen" to pantry,
            "1 Glas Honig" to pantry,
            "Salz" to pantry,
            "1 Flasche BBQ-Soße" to pantry,
            "50 g Pistazien" to pantry,
            "Speisestärke" to pantry,
            "1 Dose Kichererbsen" to pantry,
            "Vanilleextrakt" to pantry,
            "Zimt" to pantry,
            "Küchenrolle" to null,
            "Backpapier" to null,
            "Spülmittel" to null,
            "Gefrierbeutel" to null,
            "Alufolie" to null,
            "Kerzen" to null,
        )

    /** Forty things to buy in four groups and some without, three of them ticked off. */
    val longList =
        ShoppingList(
            id = "list",
            name = "Wocheneinkauf",
            items =
                goods.mapIndexed { index, (note, label) ->
                    ShoppingItem("i$index", "list", 0.0, null, null, note, index % 13 == 5, index, label, emptyList())
                },
            labelOrder = listOf("produce", "dairy", "bakery", "pantry"),
        )
}
