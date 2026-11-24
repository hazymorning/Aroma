package app.aroma.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import app.aroma.R
import app.aroma.core.model.Meal
import app.aroma.core.model.MealType

@Composable
@ReadOnlyComposable
fun mealTypeLabel(type: MealType): String =
    stringResource(
        when (type) {
            MealType.Breakfast -> R.string.meal_breakfast
            MealType.Lunch -> R.string.meal_lunch
            MealType.Dinner -> R.string.meal_dinner
            MealType.Side -> R.string.meal_side
            MealType.Snack -> R.string.meal_snack
            MealType.Drink -> R.string.meal_drink
            MealType.Dessert -> R.string.meal_dessert
        },
    )

/** The kind of dish, named as the meal plan names it; also the name of a category made for it. */
@Composable
@ReadOnlyComposable
fun mealLabel(meal: Meal): String =
    stringResource(
        when (meal) {
            Meal.Breakfast -> R.string.meal_breakfast
            Meal.Lunch -> R.string.meal_lunch
            Meal.Snack -> R.string.meal_snack
            Meal.Dinner -> R.string.meal_dinner
            Meal.Dessert -> R.string.meal_dessert
            Meal.Side -> R.string.meal_side
            Meal.Drink -> R.string.meal_drink
        },
    )
