package app.aroma.feature.cook

import app.aroma.testing.FakeMealie
import app.aroma.testing.ScreenTest
import app.aroma.testing.TestApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestApp::class)
class CookScreenTest {
    @get:Rule
    val screen = ScreenTest()

    private val mealie get() = screen.mealie

    @Test
    fun `the stars at the end are your own rating, not the recipe's average`() {
        val soup = mealie.addRecipe("Linsensuppe", othersRatings = listOf(4.0))
        val cook = screen.cook(soup.slug)
        val shown = screen.awaitState(cook.state) { it.recipe != null }

        assertEquals(4.0, shown.recipe?.rating)
        assertNull(shown.userRating)

        cook.rate(4)
        screen.until { mealie.ratingOf(soup) != null }
        assertEquals(listOf("""{"rating":4.0}"""), mealie.ratingsSent(soup))
        assertEquals(4.0, cook.state.value.userRating)
    }

    @Test
    fun `a dish finished without stars is rated from the card on Home, which then goes`() {
        val soup = mealie.addRecipe("Linsensuppe", othersRatings = listOf(4.0))
        val cook = screen.cook(soup.slug)
        screen.awaitState(cook.state) { it.recipe != null }
        cook.finished()

        val home = screen.home()
        screen.awaitState(home.state) { it.toRate?.recipeId == soup.id }
        home.rateCooked(5)
        val card = home.state.value.toRate
        assertEquals(5, card?.stars)

        // The card stays a moment, long enough to see the stars fill, then goes.
        screen.until(clockRunning = true) { home.state.value.toRate == null }
        assertEquals(5.0, mealie.ratingOf(soup)?.rating)
        assertEquals(listOf("""{"rating":5.0}"""), mealie.ratingsSent(soup))
    }

    private fun FakeMealie.ratingsSent(recipe: FakeMealie.Recipe) =
        requests("POST", "/api/users/$userId/ratings/${recipe.slug}").map { it.body }
}
