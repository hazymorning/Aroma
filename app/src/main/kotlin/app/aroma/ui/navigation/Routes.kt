package app.aroma.ui.navigation

import app.aroma.R
import app.aroma.feature.recipes.CollectionKind
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object RecipesRoute

@Serializable
data object MealPlanRoute

@Serializable
data object ShoppingRoute

@Serializable
data class RecipeRoute(val slug: String)

@Serializable
data class CookRoute(val slug: String, val scale: Double)

/** Recipes that belong together, on a page of their own; [id] names the cookbook, category, tag or tool. */
@Serializable
data class CollectionRoute(
    val kind: CollectionKind,
    val name: String,
    val id: String = "",
    val description: String = "",
)

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
    Home(HomeRoute, R.string.nav_home, R.drawable.ic_home, R.drawable.ic_home_selected),
    Recipes(RecipesRoute, R.string.nav_recipes, R.drawable.ic_recipes, R.drawable.ic_recipes_selected),
    MealPlan(MealPlanRoute, R.string.nav_meal_plan, R.drawable.ic_plan, R.drawable.ic_plan_selected),
    Shopping(ShoppingRoute, R.string.nav_shopping, R.drawable.ic_basket, R.drawable.ic_basket_selected),
}
