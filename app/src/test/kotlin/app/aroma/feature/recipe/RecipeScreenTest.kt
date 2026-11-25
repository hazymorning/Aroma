package app.aroma.feature.recipe

import app.aroma.core.model.MealType
import app.aroma.testing.ScreenTest
import app.aroma.testing.TestApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestApp::class)
class RecipeScreenTest {
    @get:Rule
    val screen = ScreenTest()

    private val mealie get() = screen.mealie

    @Test
    fun `your own stars show rather than the average, and taking them back leaves none of yours`() {
        val soup = mealie.addRecipe("Linsensuppe", othersRatings = listOf(5.0))
        mealie.rate(soup, 4.0)
        val page = screen.recipe(soup.slug)
        val shown = screen.awaitState(page.state) { it.recipe != null && it.userRating != null }
        assertEquals(4.0, shown.userRating)
        assertEquals(4.5, shown.recipe?.rating)

        page.rate(0)
        // Mealie works out the average anew, without the rating taken back.
        val unrated = screen.awaitState(page.state) { it.recipe?.rating == 5.0 }
        assertNull(unrated.userRating)
        assertEquals(
            listOf("""{"rating":0.0}"""),
            mealie.requests("POST", "/api/users/${mealie.userId}/ratings/${soup.slug}").map { it.body },
        )

        // The offer to put the stars back reaches a page that starts listening only now.
        val messages = screen.collect(page.events)
        screen.until { messages.isNotEmpty() }
        assertEquals(listOf(RecipeEvent.Unrated(4)), messages)
    }

    @Test
    fun `the menu keeps the recipe pressed last when the one pressed before answers late`() {
        val gnocchi = mealie.addRecipe("Gnocchi")
        val soup = mealie.addRecipe("Linsensuppe")
        val late = mealie.hold("/api/recipes/${gnocchi.slug}")
        val menu = screen.recipe(gnocchi.slug)
        screen.until { late.waiting == 1 }

        menu.show(soup.slug)
        // Shown, and the answer for the gnocchi no longer awaited.
        screen.awaitState(menu.state) { it.recipe?.slug == soup.slug && screen.networkIdle }
        late.release()
        val shown = screen.awaitState(menu.state) { screen.networkIdle }

        assertEquals(soup.slug, shown.recipe?.slug)
    }

    @Test
    fun `deleting from the menu removes the recipe pressed last, not the one pressed before`() {
        val gnocchi = mealie.addRecipe("Gnocchi")
        val soup = mealie.addRecipe("Linsensuppe")
        val menu = screen.recipe(gnocchi.slug)
        screen.awaitState(menu.state) { it.recipe?.slug == gnocchi.slug }
        menu.show(soup.slug)
        screen.awaitState(menu.state) { it.recipe?.slug == soup.slug }

        menu.delete()
        screen.until { mealie.recipeNamed("Linsensuppe") == null }

        assertEquals(listOf("/api/recipes/${soup.slug}"), mealie.requests.filter { it.method == "DELETE" }.map { it.path })
        assertNotNull(mealie.recipeNamed("Gnocchi"))
    }

    @Test
    fun `undoing an unsave saves that recipe again, even once the menu shows another`() {
        val gnocchi = mealie.addRecipe("Gnocchi")
        mealie.rate(gnocchi, null, favorite = true)
        val soup = mealie.addRecipe("Linsensuppe")
        val menu = screen.recipe(gnocchi.slug)
        screen.awaitState(menu.state) { it.isFavorite }
        menu.toggleFavorite()
        screen.until { mealie.ratingOf(gnocchi)?.isFavorite == false }
        menu.show(soup.slug)
        screen.awaitState(menu.state) { it.recipe?.slug == soup.slug }

        val messages = screen.collect(menu.events)
        screen.until { messages.isNotEmpty() }
        menu.resave((messages.single() as RecipeEvent.Unsaved).recipe)

        screen.until { mealie.ratingOf(gnocchi)?.isFavorite == true }
        assertNull(mealie.ratingOf(soup))
    }

    @Test
    fun `planning says so even to a page that starts listening only afterwards`() {
        val soup = mealie.addRecipe("Linsensuppe")
        val page = screen.recipe(soup.slug)
        screen.awaitState(page.state) { it.recipe != null }

        page.plan(WEDNESDAY, MealType.Dinner)
        screen.awaitState(page.state) { it.confirmed == QuickAction.Plan }
        val messages = screen.collect(page.events)
        screen.until { messages.isNotEmpty() }
        // The check on the button goes after a moment; nothing more is said.
        screen.advanceBy(CONFIRM_MILLIS)
        screen.awaitState(page.state) { it.confirmed == null }

        assertEquals(listOf(RecipeEvent.Planned(WEDNESDAY)), messages)
        assertEquals(listOf(WEDNESDAY to soup.id), mealie.planned.map { it.date to it.recipeId })
    }

    private companion object {
        val WEDNESDAY: LocalDate = LocalDate.of(2026, 11, 25)
        const val CONFIRM_MILLIS = 1_500L
    }
}
