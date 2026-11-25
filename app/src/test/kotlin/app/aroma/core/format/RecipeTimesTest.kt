package app.aroma.core.format

import app.aroma.core.model.RecipeSummary
import app.aroma.core.model.RecipeTime
import app.aroma.feature.editor.RecipeDraft
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** A recipe's time as Mealie keeps it, in text, reads the same on its page, in the quick filter and in the editor. */
class RecipeTimesTest {
    @Test
    fun `a time reads the same on the recipe's page, in the quick filter and in the editor`() {
        val times =
            listOf(
                "PT1H20M" to ReadTime(80),
                "PT45M" to ReadTime(45),
                "1:20 h" to ReadTime(80),
                "10–15 min" to ReadTime(10, upTo = 15),
                "ca. 2 Std." to ReadTime(120, prefix = "ca."),
                "1 hour 20 minutes" to ReadTime(80),
            )
        times.forEach { (text, onThePage) ->
            assertEquals(text, onThePage, TimeText.read(text))
            // A span takes as long as its end, also in the quick filter and in the editor's one figure.
            val minutes = onThePage.upTo ?: onThePage.minutes
            assertEquals(text, minutes * SECONDS_PER_MINUTE, summary(text).readySeconds())
            assertEquals(
                text,
                minutes.toString(),
                RecipeDraft.formFrom(JsonObject(mapOf("totalTime" to JsonPrimitive(text))), Locale.GERMANY).totalMinutes,
            )
        }
    }

    private fun summary(total: String) =
        RecipeSummary("id", "slug", "Name", "", null, RecipeTime(null, total), null, null, 2.0, null, emptyList(), emptyList())

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}
