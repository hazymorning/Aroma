package app.aroma.screenshots

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
import app.aroma.core.model.TimelineEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Hand-written sample content for screenshot tests, in German and English. */
class SampleData(private val german: Boolean) {
    private fun t(de: String, en: String) = if (german) de else en

    private fun image(id: String) = RecipeImage(SERVER, id, "v1")

    private fun organizer(name: String) = Organizer(name.lowercase(), name, name.lowercase())

    private fun summary(
        id: String,
        de: String,
        en: String,
        minutes: Int?,
        rating: Double?,
    ) = RecipeSummary(
        id = id,
        slug = id,
        name = t(de, en),
        description = "",
        image = image(id),
        totalTime = minutes?.let { RecipeTime(it * 60, null) },
        prepTime = null,
        cookTime = null,
        servings = 4.0,
        rating = rating,
        categories = emptyList(),
        tags = emptyList(),
    )

    val recipes =
        listOf(
            summary("pancakes", "Fluffige Pancakes mit Ahornsirup", "Fluffy pancakes with maple syrup", 25, 4.5),
            summary("curry", "Rote Curry-Suppe mit Kokos", "Red coconut curry soup", 35, 5.0),
            summary("bowl", "Buddha-Bowl mit Avocado", "Buddha bowl with avocado", 20, 4.0),
            summary("pasta", "Farfalle-Salat mit Pesto", "Farfalle salad with pesto", 20, null),
            summary("salmon", "Lachs-Bowl mit Sesam", "Salmon bowl with sesame", 30, 4.5),
            summary("pizza", "Pizza mit Hähnchen und roten Zwiebeln", "Chicken pizza with red onion", 45, 4.0),
        )

    val cookbooks =
        listOf(
            Cookbook("c1", "veggie", t("Vegetarisch", "Vegetarian"), t("Alles ohne Fleisch und Fisch", "Everything without meat or fish")),
            Cookbook("c2", "weeknight", t("Unter der Woche", "Weeknights"), t("Schnell auf dem Tisch", "On the table in no time")),
            Cookbook("c3", "brunch", t("Sonntagsfrühstück", "Sunday brunch"), t("Für lange Vormittage", "For slow mornings")),
        )

    val cookbookCovers =
        cookbooks.mapIndexed { index, book ->
            CookbookCover(
                book,
                12 - 3 * index,
                recipes[(index * 2 + 1) % recipes.size].image,
            )
        }

    private val gram = unit("g", t("Gramm", "gram"), "g", fraction = false)
    private val tablespoon = unit("tbsp", t("Esslöffel", "tablespoon"), t("EL", "tbsp"), fraction = true)
    private val teaspoon = unit("tsp", t("Teelöffel", "teaspoon"), t("TL", "tsp"), fraction = true)
    private val milliliter = unit("ml", "Milliliter", "ml", fraction = false)

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

    private fun step(id: String, de: String, en: String) = Step(id, null, t(de, en), emptyList(), null)

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
            yieldQuantity = 0.0,
            yieldText = "",
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
                        "Mehl, Zucker, Backpulver und Salz in einer Schüssel mischen.",
                        "Mix flour, sugar, baking powder and salt in a bowl.",
                    ),
                    step(
                        "s2",
                        "Eier, Milch und **Butter** verquirlen und kurz unter die trockenen Zutaten rühren. Den Teig 10 Minuten ruhen lassen.",
                        "Whisk eggs, milk and **butter**, then briefly stir into the dry ingredients. Let the batter rest for 10 minutes.",
                    ),
                    step(
                        "s3",
                        "Eine Pfanne auf mittlerer Stufe erhitzen und je Pancake eine kleine Kelle Teig etwa 2 Minuten pro Seite backen.",
                        "Heat a pan over medium heat and cook a small ladle of batter per pancake for about 2 minutes per side.",
                    ),
                    step(
                        "s4",
                        "Stapeln, mit Banane belegen und mit Ahornsirup servieren.",
                        "Stack, top with banana and serve with maple syrup.",
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
            dateAdded = LocalDate.of(2026, 11, 22),
            lastMade = null,
        )

    val today: LocalDate = LocalDate.of(2026, 11, 23)

    private fun at(daysAgo: Long, hour: Int, minute: Int) =
        today
            .minusDays(daysAgo)
            .atTime(LocalTime.of(hour, minute))
            .atZone(ZoneId.systemDefault())
            .toInstant()

    val timeline =
        listOf(
            TimelineEvent(
                "t1",
                recipes[1],
                t("Mara hat das gekocht", "Mara made this"),
                t("Mit extra Limette, nächstes Mal mehr Chili.", "With extra lime; more chili next time."),
                at(0, 19, 42),
            ),
            TimelineEvent("t2", recipes[0], t("Jonas hat das gekocht", "Jonas made this"), null, at(0, 8, 15)),
            TimelineEvent("t3", recipes[4], t("Rezept erstellt", "Recipe created"), null, at(1, 17, 3)),
            TimelineEvent("t4", recipes[2], t("Mara hat das gekocht", "Mara made this"), null, at(3, 12, 30)),
        )

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

    companion object {
        const val SERVER = "https://mealie.test"
    }
}
