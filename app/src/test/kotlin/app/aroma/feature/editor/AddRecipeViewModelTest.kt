package app.aroma.feature.editor

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import app.aroma.data.TestEnvironment
import app.aroma.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AddRecipeViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val env = TestEnvironment()
    private val settings by lazy { SettingsRepository(PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb") }) }

    @After
    fun tearDown() = env.close()

    @Test
    fun `a shared web link is imported at once, but not again when Android brings the screen back`() {
        val saved = SavedStateHandle()
        assertEquals(AddMode.Import, open(LINK, saved).state.value.working)

        val restored = open(LINK, saved)
        assertNull(restored.state.value.working)
        assertEquals(LINK, restored.state.value.url)
    }

    @Test
    fun `shared text that is no web link waits for the import button`() {
        val shared = open("intent://scan#Intent;end", SavedStateHandle())
        assertNull(shared.state.value.working)
        assertEquals("intent://scan#Intent;end", shared.state.value.url)
    }

    private fun open(sharedUrl: String, saved: SavedStateHandle) =
        AddRecipeViewModel(env.recipes, RecipeImporter(env.recipes, "Rezept"), settings, sharedUrl, CoroutineScope(Job()), saved)

    private companion object {
        const val LINK = "https://example.com/lauchpuffer"
    }
}
