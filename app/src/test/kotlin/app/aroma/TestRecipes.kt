package app.aroma

import app.aroma.core.model.Ingredient
import app.aroma.core.model.IngredientFood
import app.aroma.core.model.Organizer
import app.aroma.core.model.Recipe
import app.aroma.core.model.RecipeTime
import app.aroma.core.model.Step

/** A recipe as Mealie sends it, in German or English, for tests that show its page. */
class TestRecipes(private val german: Boolean = false) {
    private fun t(de: String, en: String) = if (german) de else en

    private fun ingredient(quantity: Double, food: String, plural: String? = null) =
        Ingredient(null, null, quantity, null, IngredientFood(null, food, plural), "", "", null)

    val recipe =
        Recipe(
            id = "pancakes",
            slug = "pancakes",
            name = t("Fluffige Pancakes", "Fluffy pancakes"),
            description = t("Dick und luftig wie im Diner.", "Thick and airy, like in a diner."),
            image = null,
            totalTime = RecipeTime(1500, null),
            prepTime = null,
            cookTime = null,
            servings = 4.0,
            rating = 4.5,
            categories = listOf(Organizer("breakfast", t("Frühstück", "Breakfast"), "breakfast")),
            tags = emptyList(),
            tools = emptyList(),
            sourceUrl = null,
            ingredients = listOf(ingredient(2.0, t("Ei", "egg"), t("Eier", "eggs")), ingredient(300.0, t("ml Milch", "ml milk"))),
            steps =
                listOf(
                    Step(null, null, t("Eier und Milch verquirlen.", "Whisk the eggs and milk."), emptyList(), null),
                    Step(null, null, t("In einer Pfanne goldbraun backen.", "Cook in a pan until golden."), emptyList(), null),
                ),
            notes = emptyList(),
            nutrition = emptyList(),
        )
}
