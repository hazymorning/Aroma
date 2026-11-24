package app.aroma.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.Meal
import app.aroma.core.model.MealType
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Space

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

/** Quiet hint shown while Aroma works from its offline copy. */
@Composable
fun OfflineNotice(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Icon(
            painterResource(R.drawable.ic_cloud_off),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Glyph.s),
        )
        Text(
            text = stringResource(R.string.offline_notice),
            style = AromaType.Small,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
