package app.aroma.ui.navigation

import app.aroma.R
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class RecipesRoute(
    val focusSearch: Boolean = false,
    val favorites: Boolean = false,
    val organizerKind: String? = null,
    val organizerId: String? = null,
    val organizerName: String? = null,
)

@Serializable
data object MealPlanRoute

@Serializable
data object ShoppingRoute

@Serializable
data class RecipeRoute(val slug: String)

@Serializable
data class CookRoute(val slug: String, val scale: Double)

@Serializable
data class CookbookRoute(val slug: String, val name: String, val description: String)

@Serializable
data class AddRecipeRoute(val sharedUrl: String? = null)

@Serializable
data class EditorRoute(val slug: String)

@Serializable
data object SettingsRoute

@Serializable
data object LicensesRoute

enum class Tab(
    val route: Any,
    val label: Int,
    val icon: Int,
    val selectedIcon: Int,
) {
    Home(HomeRoute, R.string.nav_home, R.drawable.ic_home, R.drawable.ic_home_filled),
    Recipes(RecipesRoute(), R.string.nav_recipes, R.drawable.ic_menu_book, R.drawable.ic_menu_book_filled),
    MealPlan(MealPlanRoute, R.string.nav_meal_plan, R.drawable.ic_calendar_month, R.drawable.ic_calendar_month_filled),
    Shopping(ShoppingRoute, R.string.nav_shopping, R.drawable.ic_shopping_basket, R.drawable.ic_shopping_basket_filled),
}
