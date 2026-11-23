package app.aroma.data.reminder

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.aroma.core.model.Recipe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class RatingReminderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var now = 1_000_000L
    private val reminder by lazy {
        RatingReminder(PreferenceDataStoreFactory.create { folder.newFile("reminder.preferences_pb") }) { now }
    }

    @Test
    fun `a recipe cooked is asked about for a few days, then forgotten`() =
        runBlocking {
            reminder.cooked(recipe("gnocchi"), userId = "mara")
            now += 47.hours.inWholeMilliseconds
            assertEquals("gnocchi", reminder.pending.first()?.slug)
            now += 2.days.inWholeMilliseconds
            assertNull(reminder.pending.first())
        }

    @Test
    fun `rating another recipe leaves the reminder, rating this one ends it`() =
        runBlocking {
            reminder.cooked(recipe("gnocchi"), userId = "mara")
            reminder.clear("id-linsensuppe")
            assertEquals("gnocchi", reminder.pending.first()?.slug)
            reminder.clear("id-gnocchi")
            assertNull(reminder.pending.first())
        }

    @Test
    fun `only the last recipe cooked is asked about`() =
        runBlocking {
            reminder.cooked(recipe("gnocchi"), userId = "mara")
            reminder.cooked(recipe("linsensuppe"), userId = "mara")
            assertEquals("linsensuppe", reminder.pending.first()?.slug)
        }

    private fun recipe(slug: String) =
        Recipe(
            id = "id-$slug",
            slug = slug,
            name = slug,
            description = "",
            image = null,
            totalTime = null,
            prepTime = null,
            cookTime = null,
            servings = 2.0,
            rating = null,
            categories = emptyList(),
            tags = emptyList(),
            tools = emptyList(),
            sourceUrl = null,
            ingredients = emptyList(),
            steps = emptyList(),
            notes = emptyList(),
            nutrition = emptyList(),
        )
}
