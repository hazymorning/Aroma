package app.aroma.feature.editor

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.aroma.data.TestEnvironment
import app.aroma.data.remote.SocialPosts
import app.aroma.data.settings.SettingsRepository
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
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

    @Before
    fun setUp() = WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = env.close()

    @Test
    fun `a shared web link is imported at once, but not again when Android brings the screen back`() {
        val saved = SavedStateHandle()
        assertTrue(open(LINK, saved).state.value.isImporting)

        val restored = open(LINK, saved)
        assertFalse(restored.state.value.isImporting)
        assertEquals(LINK, restored.state.value.url)
    }

    @Test
    fun `shared text that is no web link waits for the import button`() {
        val shared = open("intent://scan#Intent;end", SavedStateHandle())
        assertFalse(shared.state.value.isImporting)
        assertEquals("intent://scan#Intent;end", shared.state.value.url)
    }

    private fun open(sharedUrl: String, saved: SavedStateHandle): AddRecipeViewModel {
        val importer = RecipeImporter(env.recipes, SocialPosts(OkHttpClient()) { "Test browser" }, untitled = { "Rezept" })
        val imports = RecipeImports(ApplicationProvider.getApplicationContext(), env.recipes, importer)
        return AddRecipeViewModel(env.recipes, imports, settings, sharedUrl, shownJob = null, saved)
    }

    private companion object {
        const val LINK = "https://example.com/lauchpuffer"
    }
}
