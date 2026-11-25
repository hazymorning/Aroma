package app.aroma.feature.mealplan

import app.aroma.testing.ScreenTest
import app.aroma.testing.TestApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class)
class MealPlanScreenTest {
    @get:Rule
    val screen = ScreenTest()

    private val mealie get() = screen.mealie

    @Test
    fun `removing a meal says so even to a page that starts listening only afterwards`() {
        mealie.plan(WEDNESDAY, mealie.addRecipe("Linsensuppe"))
        val plan = screen.plan()
        val shown = screen.awaitState(plan.state) { state -> state.days.any { it.entries.isNotEmpty() } }
        val meal = shown.days.flatMap { it.entries }.single()

        plan.delete(meal)
        // The plan is read anew only once the page has said so.
        screen.until { mealie.planned.isEmpty() && mealie.requests("GET", "/api/households/mealplans").size == 2 }
        val messages = screen.collect(plan.events)
        screen.until { messages.isNotEmpty() }

        assertEquals(listOf(MealPlanEvent.Removed(meal)), messages)
    }

    private companion object {
        val WEDNESDAY: LocalDate = LocalDate.of(2026, 11, 25)
    }
}
