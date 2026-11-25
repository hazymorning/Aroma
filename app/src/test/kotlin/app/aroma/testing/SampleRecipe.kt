package app.aroma.testing

import app.aroma.core.model.Ingredient
import app.aroma.core.model.Organizer
import app.aroma.core.model.Recipe
import app.aroma.core.model.Step

/** A recipe of four servings with only what a test gives it; its id is its slug's with "id-" before it. */
fun sampleRecipe(
    name: String = "Gnocchi",
    slug: String = name.lowercase(),
    categories: List<Organizer> = emptyList(),
    ingredients: List<Ingredient> = emptyList(),
    steps: List<Step> = emptyList(),
): Recipe =
    Recipe(
        id = "id-$slug",
        slug = slug,
        name = name,
        description = "",
        image = null,
        totalTime = null,
        prepTime = null,
        cookTime = null,
        servings = 4.0,
        rating = null,
        categories = categories,
        tags = emptyList(),
        tools = emptyList(),
        sourceUrl = null,
        ingredients = ingredients,
        steps = steps,
        notes = emptyList(),
        nutrition = emptyList(),
    )
