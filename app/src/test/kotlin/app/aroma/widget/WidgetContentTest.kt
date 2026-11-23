package app.aroma.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasAnyDescendant
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
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
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetContentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today: LocalDate = LocalDate.of(2026, 11, 23)
    private val pasta = RecipeSummary("pasta", "pasta", "Pasta", "", null, null, null, null, 0.0, null, emptyList(), emptyList())
    private val plan =
        todayPlan(
            listOf(
                MealPlanEntry(1, today, MealType.Dinner, "", "", pasta),
                MealPlanEntry(2, today.plusDays(1), MealType.Lunch, "Leftovers", "", null),
            ),
            today.atTime(18, 0),
        )

    @Test
    fun `the week names its days, and a planned recipe opens in the app`() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(DpSize(276.dp, 455.dp))
            setContext(context)
            provideComposable { TodayContent(TodayState.Ready(plan, null, emptyMap()), onRetry = {}) }

            onNode(hasText("Today")).assertExists()
            onNode(hasText("Tomorrow")).assertExists()
            onNode(hasClickAction() and hasAnyDescendant(hasText("Pasta")))
                .assert(hasStartActivityClickAction(LaunchRequest.Recipe(pasta.slug).intent(context)))
            onNode(hasClickAction() and hasAnyDescendant(hasText("Leftovers")))
                .assert(hasStartActivityClickAction(LaunchRequest.MealPlan.intent(context)))
        }

    @Test
    fun `the photo beside the day is named for screen readers and opens its recipe`() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(DpSize(276.dp, 220.dp))
            setContext(context)
            provideComposable { TodayContent(TodayState.Ready(plan, null, emptyMap()), onRetry = {}) }

            onNode(hasContentDescriptionEqualTo("Pasta"))
                .assert(hasStartActivityClickAction(LaunchRequest.Recipe(pasta.slug).intent(context)))
        }

    @Test
    fun `the list shows its aisles and items left, and every item is checked off with a tap`() =
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
        }
}
