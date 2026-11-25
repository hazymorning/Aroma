package app.aroma.core.model

data class Cookbook(
    val id: String,
    val slug: String,
    val name: String,
    val description: String,
)

/** A cookbook with how many recipes it holds and the photo of its newest one. */
data class CookbookCover(val cookbook: Cookbook, val recipeCount: Int, val photo: RecipeImage?)
