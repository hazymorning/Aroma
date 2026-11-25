package app.aroma.core.model

data class Cookbook(
    val id: String,
    val slug: String,
    val name: String,
    val description: String,
    /** Which recipes it holds, as far as Aroma can tell and change it. */
    val rule: CookbookRule = CookbookRule.Own,
)

/** A cookbook with how many recipes it holds and the photo of its newest one. */
data class CookbookCover(val cookbook: Cookbook, val recipeCount: Int, val photo: RecipeImage?)
