package app.aroma.feature.home

import app.aroma.core.model.Meal
import app.aroma.core.model.RecipeSummary
import app.aroma.data.settings.HomeSection
import app.aroma.testing.ScreenTest
import app.aroma.testing.TestApp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestApp::class)
class HomeScreenTest {
    @get:Rule
    val screen = ScreenTest()

    private val mealie get() = screen.mealie

    @Test
    fun `on a Monday evening Home offers dinner, the quick dishes, the week and what is left to buy, each recipe once`() {
        val dinner = listOf("Abendessen")
        val soup = mealie.addRecipe("Linsensuppe", totalTime = "PT1H20M", categories = dinner)
        mealie.addRecipe("Gnocchi", totalTime = "PT20M", categories = dinner)
        mealie.addRecipe("Curry", totalTime = "10–15 min", categories = dinner)
        mealie.addRecipe("Ofengemüse", totalTime = "45 min", categories = dinner)
        mealie.addRecipe("Schmorbraten", totalTime = "1:20 h", categories = dinner)
        mealie.addRecipe("Risotto", totalTime = "ca. 2 Std.", categories = dinner)
        mealie.addRecipe("Porridge", totalTime = "PT10M", categories = listOf("Frühstück"))
        mealie.addRecipe("Salat", prepTime = "15 min", performTime = "10 min")
        mealie.addRecipe("Rührei", totalTime = "5 Minuten")
        mealie.addRecipe("Toast", totalTime = "10 min")
        mealie.addRecipe("Brot", totalTime = "1 hour 20 minutes")
        mealie.addRecipe("Pfannkuchen")
        mealie.plan(WEDNESDAY, soup)
        mealie.addList("Wocheneinkauf", "Milch", "Eier", "Mehl", checked = setOf("Mehl"))
        // Home counts what is left to buy where it shows the list, without the shopping tab ever opened.
        runBlocking { screen.container.settings.setHomeSection(HomeSection.Shopping, shown = true) }

        val home = screen.home()
        val state = screen.awaitState(home.state) { !it.isLoading && it.openItems > 0 }

        assertEquals(Greeting.Evening, state.greeting)
        assertEquals(Meal.Dinner, state.meal)
        assertTrue(state.suggestionsForMeal)
        assertEquals(setOf("Linsensuppe", "Gnocchi", "Curry", "Ofengemüse", "Schmorbraten", "Risotto"), state.suggestions.names())
        assertEquals(setOf("Gnocchi", "Curry", "Porridge", "Salat", "Rührei", "Toast"), state.quick.names())
        assertEquals(listOf(WEDNESDAY to "Linsensuppe"), state.week.map { it.date to it.recipe?.name })
        assertEquals(2, state.openItems)

        val shown = state.inspiration()
        assertTrue(shown.forToday.isNotEmpty() && shown.ideas.isNotEmpty() && shown.quick.isNotEmpty())
        assertNotNull(shown.newest)
        val ids = (shown.forToday + shown.ideas + shown.quick + listOfNotNull(shown.newest)).map { it.id }
        assertEquals(ids.distinct(), ids)

        val tonight = shown.forToday.first()
        home.planToday(tonight)
        screen.until { mealie.planned.size == 2 }
        val sent = Json.parseToJsonElement(mealie.requests("POST", "/api/households/mealplans").single().body).jsonObject
        assertEquals(
            listOf(MONDAY.toString(), "dinner", tonight.id),
            listOf("date", "entryType", "recipeId").map { sent.getValue(it).jsonPrimitive.content },
        )
    }

    @Test
    fun `planning a suggestion for today says so even to a page that starts listening only afterwards`() {
        mealie.addRecipe("Linsensuppe", categories = listOf("Abendessen"))
        val home = screen.home()
        val shown = screen.awaitState(home.state) { it.suggestions.isNotEmpty() }

        home.planToday(shown.suggestions.single())
        // Home shows the plan anew only once it has said so.
        screen.awaitState(home.state) { it.todays.isNotEmpty() }
        val messages = screen.collect(home.events)
        screen.until { messages.isNotEmpty() }

        assertEquals(listOf(HomeEvent.Planned(MONDAY)), messages)
    }

    private fun List<RecipeSummary>.names() = mapTo(mutableSetOf()) { it.name }

    private companion object {
        val MONDAY: LocalDate = LocalDate.of(2026, 11, 23)
        val WEDNESDAY: LocalDate = MONDAY.plusDays(2)
    }
}
