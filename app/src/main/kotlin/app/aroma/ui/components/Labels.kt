package app.aroma.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import app.aroma.R
import app.aroma.core.format.MeasureSystem
import app.aroma.core.model.Meal
import app.aroma.core.model.MealType
import app.aroma.data.settings.IngredientsInSteps

@get:StringRes
val MealType.labelRes: Int
    get() =
        when (this) {
            MealType.Breakfast -> R.string.meal_breakfast
            MealType.Lunch -> R.string.meal_lunch
            MealType.Dinner -> R.string.meal_dinner
            MealType.Side -> R.string.meal_side
            MealType.Snack -> R.string.meal_snack
            MealType.Drink -> R.string.meal_drink
            MealType.Dessert -> R.string.meal_dessert
        }

@Composable
@ReadOnlyComposable
fun mealTypeLabel(type: MealType): String = stringResource(type.labelRes)

/** The kind of dish, named as the meal plan names it; also the name of a category made for it. */
@Composable
@ReadOnlyComposable
fun mealLabel(meal: Meal): String = mealTypeLabel(meal.mealType)

/** The name of a choice of measures, as the recipe's sheet and the setting show it. */
@StringRes
fun MeasureSystem.labelRes(): Int =
    when (this) {
        MeasureSystem.AsWritten -> R.string.measures_as_written
        MeasureSystem.Metric -> R.string.measures_metric
        MeasureSystem.US -> R.string.measures_us
    }

/** The name of a choice of ingredients under the steps, as the settings and cook mode show it. */
@StringRes
fun IngredientsInSteps.labelRes(): Int =
    when (this) {
        IngredientsInSteps.None -> R.string.ingredients_in_steps_none
        IngredientsInSteps.Used -> R.string.ingredients_in_steps_used
        IngredientsInSteps.All -> R.string.ingredients_in_steps_all
    }
