package app.aroma.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasAnyDescendant
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import app.aroma.core.model.MealPlanEntry
import app.aroma.core.model.MealType
import app.aroma.core.model.RecipeSummary
import app.aroma.core.model.ShoppingItem
import app.aroma.core.model.ShoppingLabel
import app.aroma.core.model.ShoppingList
import app.aroma.ui.navigation.LaunchRequest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** What each state of the widgets shows and where a tap on it leads. */
@RunWith(RobolectricTestRunner::class)
class WidgetContentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today: LocalDate = LocalDate.of(2026, 11, 23)
    private val pasta = RecipeSummary("pasta", "pasta", "Pasta", "", null, null, null, null, 0.0, null, emptyList(), emptyList())
    private val photo: Bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.RGB_565)
    private val plan =
        todayPlan(
            listOf(
                MealPlanEntry(1, today, MealType.Dinner, "", "", pasta),
                MealPlanEntry(2, today.plusDays(1), MealType.Lunch, "Leftovers", "", null),
            ),
            today.atTime(18, 0),
        )
    private val openPasta = LaunchRequest.Recipe(pasta.slug).intent(context)
    private val openPlan = LaunchRequest.MealPlan.intent(context)

    @Test
    fun `the week names its days, and a planned recipe opens in the app and a note opens the plan`() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(DpSize(276.dp, 455.dp))
            setContext(context)
            provideComposable { TodayContent(TodayState.Ready(plan, null, emptyMap()), onRetry = {}) }

            onNode(hasText("Today")).assertExists()
            onNode(hasText("Tomorrow")).assertExists()
            onNode(hasClickAction() and hasAnyDescendant(hasText("Pasta"))).assert(hasStartActivityClickAction(openPasta))
            onNode(hasClickAction() and hasAnyDescendant(hasText("Leftovers"))).assert(hasStartActivityClickAction(openPlan))
        }

    @Test
    fun `one cell is the next meal's photo, named for screen readers, and opens its recipe`() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(cells(1, 1))
            setContext(context)
            provideComposable { TodayContent(TodayState.Ready(plan, photo, emptyMap()), onRetry = {}) }

            onNode(hasContentDescriptionEqualTo("Pasta")).assert(hasStartActivityClickAction(openPasta))
        }

    @Test
    fun `a day with nothing planned says so and opens the plan`() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(cells(2, 1))
            setContext(context)
            provideComposable { TodayContent(TodayState.Ready(todayPlan(emptyList(), today.atTime(9, 0)), null, emptyMap()), onRetry = {}) }

            onNode(hasTextEqualTo("Today")).assertExists()
            onNode(hasClickAction() and hasAnyDescendant(hasTextEqualTo("Nothing planned"))).assert(hasStartActivityClickAction(openPlan))
        }

    @Test
    fun `signed out, the widget says so and opens the app to sign in`() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(cells(2, 2))
            setContext(context)
            provideComposable { TodayContent(TodayState.SignedOut, onRetry = {}) }

            onNode(hasClickAction() and hasAnyDescendant(hasTextEqualTo("Not signed in"))).assert(hasStartActivityClickAction(openPlan))
        }

    @Test
    fun `the list shows its aisles and items left, every item is checked off with a tap, and the plus adds to the list`() =
        runGlanceAppWidgetUnitTest {
            val produce = ShoppingLabel("produce", "Produce")
            val items =
                listOf(
                    ShoppingItem("apples", "list", 0.0, null, null, "Apples", false, 0, produce, emptyList()),
                    ShoppingItem("lemons", "list", 0.0, null, null, "2 lemons", true, 1, produce, emptyList()),
                )
            setAppWidgetSize(DpSize(276.dp, 455.dp))
            setContext(context)
            provideComposable {
                ShoppingContent(ShoppingState.Ready(ShoppingList("list", "Weekly shop", items, listOf("produce"))), setOf("lemons"), {}, {})
            }

            onNode(hasText("Weekly shop")).assertExists()
            onNode(hasText("1 left")).assertExists()
            onNode(hasText("Produce")).assertExists()
            onNode(hasContentDescriptionEqualTo("Apples, not checked off")).assert(hasClickAction())
            onNode(hasContentDescriptionEqualTo("2 lemons, checked off")).assert(hasClickAction())
            onNode(hasClickAction() and hasAnyDescendant(hasText("Weekly shop")))
                .assert(hasStartActivityClickAction(LaunchRequest.Shopping("list").intent(context)))
            onNode(hasContentDescriptionEqualTo("Add to the list"))
                .assert(hasStartActivityClickAction(LaunchRequest.Shopping("list", add = true).intent(context)))
        }
}
