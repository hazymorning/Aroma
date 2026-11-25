package app.aroma.ui.components

import androidx.test.core.app.ApplicationProvider
import app.aroma.core.model.RecipeTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every time a recipe comes with reads the same way on every page, whether Mealie keeps it as a duration or as free text. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de")
class DurationsTest {
    private val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private fun text(text: String) = resources.recipeTimeText(RecipeTime(null, text))

    @Test
    fun `times read one way, number and unit held together`() {
        assertEquals("1 Std. 20 Min.", text("PT1H20M"))
        assertEquals("10–15 Min.", text("10–15 Minuten"))
        assertEquals("45 Min.", text("45 min"))
        assertEquals("ca. 2 Std.", text("ca. 2 Std."))
        assertEquals("1 Std. 20 Min.", resources.recipeTimeText(RecipeTime(4800, null)))
    }

    @Test
    fun `no time is left out rather than written as nothing`() {
        assertNull(text(""))
        assertNull(text("0 min"))
        assertNull(resources.recipeTimeText(null))
    }

    @Test
    fun `text that says something else stays as written`() {
        assertEquals("Über Nacht", text("Über Nacht"))
    }

    @Test
    fun `a missing total is prep and cook added up`() {
        val total = totalTime(null, RecipeTime(600, null), RecipeTime(null, "25 min"))
        assertEquals("35 Min.", resources.recipeTimeText(total))
        assertNull(totalTime(null, RecipeTime(600, null), null))
        assertEquals(RecipeTime(60, null), totalTime(RecipeTime(60, null), RecipeTime(600, null), RecipeTime(600, null)))
    }

    @Test
    fun `text for Mealie keeps plain spaces`() {
        assertEquals("1 Std. 20 Min.", resources.formatDurationPlain(4800))
    }
}
