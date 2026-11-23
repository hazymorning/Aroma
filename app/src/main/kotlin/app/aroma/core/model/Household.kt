package app.aroma.core.model

import java.time.LocalDate

enum class MealType(val apiValue: String) {
    Breakfast("breakfast"),
    Lunch("lunch"),
    Dinner("dinner"),
    Side("side"),
    Snack("snack"),
    Drink("drink"),
    Dessert("dessert"),
    ;

    companion object {
        fun fromApi(value: String?): MealType = entries.firstOrNull { it.apiValue == value } ?: Dinner
    }
}

data class MealPlanEntry(
    val id: Int,
    val date: LocalDate,
    val type: MealType,
    val title: String,
    val note: String,
    val recipe: RecipeSummary?,
)

data class Cookbook(
    val id: String,
    val slug: String,
    val name: String,
    val description: String,
)

/** A cookbook with how many recipes it holds and the photo of its newest one. */
data class CookbookCover(val cookbook: Cookbook, val recipeCount: Int, val photo: RecipeImage?)

data class ShoppingListInfo(val id: String, val name: String)

data class ShoppingLabel(val id: String, val name: String)

data class ShoppingItem(
    val id: String,
    val listId: String,
    val quantity: Double,
    val unit: IngredientUnit?,
    val food: IngredientFood?,
    val note: String,
    val checked: Boolean,
    val position: Int,
    val label: ShoppingLabel?,
    val recipeNames: List<String>,
)

data class ShoppingList(
    val id: String,
    val name: String,
    val items: List<ShoppingItem>,
    val labelOrder: List<String>,
)

data class User(
    val id: String,
    val username: String,
    val fullName: String,
    val email: String,
    val photo: String? = null,
)

data class ServerInfo(val url: String, val version: String, val allowPasswordLogin: Boolean)
