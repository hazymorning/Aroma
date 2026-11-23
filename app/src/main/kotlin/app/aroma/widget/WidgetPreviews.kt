package app.aroma.widget

import android.content.Context
import androidx.annotation.StringRes
import app.aroma.R
import app.aroma.core.model.MealPlanEntry
import app.aroma.core.model.MealType
import app.aroma.core.model.RecipeSummary
import app.aroma.core.model.ShoppingItem
import app.aroma.core.model.ShoppingLabel
import app.aroma.core.model.ShoppingList
import java.time.LocalDate
import java.time.LocalTime

/** A sample day for the launcher's picker, in the app's language: lunch is over, dinner is next. */
internal fun previewToday(context: Context): TodayState {
    val today = LocalDate.now()

    fun meal(
        id: Int,
        date: LocalDate,
        type: MealType,
        @StringRes name: Int,
    ) = MealPlanEntry(id, date, type, title = "", note = "", recipe = recipe(context.getString(name)))
    val entries =
        listOf(
            meal(1, today, MealType.Lunch, R.string.widget_preview_lunch),
            meal(2, today, MealType.Dinner, R.string.widget_preview_dinner),
            meal(3, today, MealType.Dessert, R.string.widget_preview_dessert),
            meal(4, today.plusDays(1), MealType.Dinner, R.string.widget_preview_tomorrow),
        )
    return TodayState.Ready(todayPlan(entries, today.atTime(PREVIEW_TIME)), hero = null, thumbnails = emptyMap())
}

/** A sample list for the launcher's picker, in the app's language, in three aisles. */
internal fun previewShoppingList(context: Context): ShoppingList {
    val produce = ShoppingLabel("produce", context.getString(R.string.widget_preview_produce))
    val dairy = ShoppingLabel("dairy", context.getString(R.string.widget_preview_dairy))
    val baking = ShoppingLabel("baking", context.getString(R.string.widget_preview_baking))

    fun item(
        position: Int,
        @StringRes name: Int,
        label: ShoppingLabel,
    ) = ShoppingItem(
        id = "preview-$position",
        listId = PREVIEW_LIST,
        quantity = 0.0,
        unit = null,
        food = null,
        note = context.getString(name),
        checked = false,
        position = position,
        label = label,
        recipeNames = emptyList(),
    )
    return ShoppingList(
        id = PREVIEW_LIST,
        name = context.getString(R.string.widget_preview_list),
        items =
            listOf(
                item(0, R.string.widget_preview_apples, produce),
                item(1, R.string.widget_preview_lemons, produce),
                item(2, R.string.widget_preview_milk, dairy),
                item(3, R.string.widget_preview_butter, dairy),
                item(4, R.string.widget_preview_flour, baking),
            ),
        labelOrder = listOf(produce.id, dairy.id, baking.id),
    )
}

private fun recipe(name: String) =
    RecipeSummary(
        id = name,
        slug = name,
        name = name,
        description = "",
        image = null,
        totalTime = null,
        prepTime = null,
        cookTime = null,
        servings = 0.0,
        rating = null,
        categories = emptyList(),
        tags = emptyList(),
    )

private val PREVIEW_TIME: LocalTime = LocalTime.of(15, 0)
private const val PREVIEW_LIST = "preview"
