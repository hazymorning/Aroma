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

    private fun text(time: String) = RecipeTime(null, time)

    @Test
    fun `the total counts, or else preparation and cooking together, in seconds where Mealie has them`() {
        assertEquals(1500, recipe(total = RecipeTime(1500, "1 hour")).readySeconds())
        assertEquals(4800, recipe(total = text("1:20 h")).readySeconds())
        assertEquals(2100, recipe(prep = RecipeTime(600, null), cook = text("25 min")).readySeconds())
    }

    @Test
    fun `a recipe is quick by the end of its range, and never without a time it can read`() {
        assertTrue(recipe(total = text("30 minutes")).isReadyWithin(30))
        assertFalse(recipe(total = text("25-35 Minuten")).isReadyWithin(30))
        assertFalse(recipe(total = text("schnell")).isReadyWithin(30))
        assertNull(recipe().readySeconds())
        assertFalse(recipe().isReadyWithin(30))
    }
}
