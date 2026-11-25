package app.aroma.data.reminder

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.aroma.testing.sampleRecipe
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
    fun `the last recipe cooked is asked about until it is rated or a few days have passed`() =
        runBlocking {
            reminder.cooked(sampleRecipe("Linsensuppe"), userId = "mara")
            reminder.cooked(sampleRecipe("Gnocchi"), userId = "mara")
            reminder.clear("id-linsensuppe")
            assertEquals("gnocchi", reminder.pending.first()?.slug)
            reminder.clear("id-gnocchi")
            assertNull(reminder.pending.first())

            reminder.cooked(sampleRecipe("Gnocchi"), userId = "mara")
            now += 47.hours.inWholeMilliseconds
            assertEquals("gnocchi", reminder.pending.first()?.slug)
            now += 2.days.inWholeMilliseconds
            assertNull(reminder.pending.first())
        }
}
