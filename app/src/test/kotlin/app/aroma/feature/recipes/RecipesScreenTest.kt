package app.aroma.feature.recipes

import app.aroma.core.model.Meal
import app.aroma.core.model.OrganizerKind
import app.aroma.core.model.RecipeSort
import app.aroma.core.model.RecipeSummary
import app.aroma.testing.ScreenTest
import app.aroma.testing.TestApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class)
class RecipesScreenTest {
    @get:Rule
    val screen = ScreenTest()

    private val mealie get() = screen.mealie

    @Test
    fun `quick recipes from pages that overlap show each recipe once, to the end of the list`() {
        // More recipes than the phone reads at once; every fifth is quick, and so are some where pages meet.
        val quick = (0 until RECIPES).filter { it % 5 == 0 } + listOf(131, 33, 32)
        repeat(RECIPES) { number -> mealie.addRecipe("Rezept $number", totalTime = if (number in quick) "PT20M" else "PT1H") }
        mealie.pageOverlap = 2
        val library = screen.recipes()
        screen.awaitState(library.state) { it.recipes.isNotEmpty() }

        library.toggleQuick()
        var shown = screen.awaitState(library.state) { state -> state.recipes.all { it.number() in quick } }
        assertEachOnce(shown)
        while (shown.canLoadMore) {
            library.loadMore()
            val before = shown.recipes.size
            shown = screen.awaitState(library.state) { !it.isLoadingMore && it.recipes.size > before }
            assertEachOnce(shown)
        }

        assertEquals(quick.size, shown.recipes.size)
    }

    @Test
    fun `every filter and order lights the filter's dot, the kind of dish too`() {
        mealie.addRecipe("Linsensuppe", categories = listOf("Abendessen", "Suppen"), tags = listOf("Vegan"))
        val library = screen.recipes()
        val loaded = screen.awaitState(library.state) { it.categories.isNotEmpty() && it.tags.isNotEmpty() }
        assertFalse(loaded.filter.hasFiltersOrSort)

        val filters: Map<String, RecipesViewModel.() -> Unit> =
            mapOf(
                "a meal" to { toggleMeal(Meal.Dinner) },
                "favourites" to { toggleFavorites() },
                "quick" to { toggleQuick() },
                "cooked long ago" to { toggleLongAgo() },
                "a category" to { toggleOrganizer(OrganizerKind.Category, loaded.categories.first { it.name == "Suppen" }) },
                "a tag" to { toggleOrganizer(OrganizerKind.Tag, loaded.tags.single()) },
                "another order" to { setSort(RecipeSort.Name) },
            )
        filters.forEach { (filter, choose) ->
            library.choose()
            assertTrue(filter, library.state.value.filter.hasFiltersOrSort)
            library.resetFilters()
            assertFalse(filter, library.state.value.filter.hasFiltersOrSort)
        }
    }

    @Test
    fun `the filter offers the categories that are no kind of dish, which have a choice of their own`() {
        mealie.addRecipe("Linsensuppe", categories = listOf("Abendessen", "Suppen"))
        val library = screen.recipes()
        val loaded = screen.awaitState(library.state) { it.categories.size == 2 }

        assertEquals(listOf("Suppen"), loaded.choices(OrganizerKind.Category).map { it.name })
    }

    @Test
    fun `the cookbooks lead the library while nothing is searched or narrowed`() {
        val soup = mealie.addRecipe("Linsensuppe", totalTime = "PT20M")
        mealie.addCookbook("Suppen", soup)
        val library = screen.recipes()
        screen.awaitState(library.state) { it.cookbooks.isNotEmpty() && it.recipes.isNotEmpty() }
        assertTrue(library.state.value.showsCookbooks)

        library.toggleQuick()
        assertFalse("narrowed", library.state.value.showsCookbooks)
        library.resetFilters()
        library.setSort(RecipeSort.Name)
        assertTrue("ordered only", library.state.value.showsCookbooks)
        library.onQueryChange("Linsen")
        assertFalse("searched", library.state.value.showsCookbooks)
    }

    @Test
    fun `resetting the filters starts over with the newest first, in one load`() {
        mealie.addRecipe("Linsensuppe", totalTime = "PT20M")
        val library = screen.recipes()
        screen.awaitState(library.state) { it.recipes.isNotEmpty() }
        library.toggleQuick()
        library.toggleLongAgo()
        library.setSort(RecipeSort.Name)
        screen.until { loads().lastOrNull()?.queryParameter("orderBy") == "name" && screen.networkIdle }
        val before = loads().size

        library.resetFilters()
        val reset = screen.awaitState(library.state) { it.recipes.isNotEmpty() && loads().size > before && screen.networkIdle }

        assertEquals(RecipeFilter(), reset.filter)
        val load = loads().drop(before).single()
        assertEquals("created_at" to "desc", load.queryParameter("orderBy") to load.queryParameter("orderDirection"))
        assertNull(load.queryParameter("queryFilter"))
    }

    /** A recipe twice in the grid is the same key twice, which ends the app. */
    private fun assertEachOnce(state: RecipesUiState) {
        val ids = state.recipes.map { it.id }
        assertEquals(ids.distinct(), ids)
    }

    private fun loads() = mealie.requests("GET", "/api/recipes").map { it.url }

    private fun RecipeSummary.number() = name.substringAfter(' ').toInt()

    private companion object {
        const val RECIPES = 230
    }
}
