package app.aroma.core.format

import app.aroma.core.model.RecipeSummary
import app.aroma.core.model.RecipeTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadyTimeTest {
    private fun recipe(total: RecipeTime? = null, prep: RecipeTime? = null, cook: RecipeTime? = null) =
        RecipeSummary("id", "slug", "Name", "", null, total, prep, cook, 2.0, null, emptyList(), emptyList())

    @Test
    fun `the total time counts, in seconds or as Mealie writes it`() {
        assertEquals(1500, recipe(total = RecipeTime(1500, "1 hour")).readySeconds())
        assertEquals(4800, recipe(total = RecipeTime(null, "1 hour 20 minutes")).readySeconds())
        assertEquals(1200, recipe(total = RecipeTime(null, "20 Minuten")).readySeconds())
    }

    @Test
    fun `without a total, preparation and cooking add up`() {
        assertEquals(2100, recipe(prep = RecipeTime(600, null), cook = RecipeTime(null, "25 min")).readySeconds())
        assertEquals(900, recipe(cook = RecipeTime(900, null)).readySeconds())
    }

    @Test
    fun `a range counts by its end, so a quick recipe is quick in any case`() {
        assertEquals(2100, recipe(total = RecipeTime(null, "25-35 Minuten")).readySeconds())
        assertFalse(recipe(total = RecipeTime(null, "25-35 Minuten")).isReadyWithin(30))
        assertTrue(recipe(total = RecipeTime(null, "30 minutes")).isReadyWithin(30))
    }

    @Test
    fun `a recipe without a time it can read is never quick`() {
        assertNull(recipe().readySeconds())
        assertNull(recipe(total = RecipeTime(null, "schnell")).readySeconds())
        assertFalse(recipe(total = RecipeTime(null, "schnell")).isReadyWithin(30))
    }
}
