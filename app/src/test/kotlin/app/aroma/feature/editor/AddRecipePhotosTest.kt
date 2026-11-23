package app.aroma.feature.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.aroma.data.TestEnvironment
import app.aroma.data.remote.SocialPosts
import app.aroma.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Photos on the add screen: enough to import on their own, sent with the import, kept through the end of the app. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class AddRecipePhotosTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val env = TestEnvironment()
    private val settings by lazy { SettingsRepository(PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb") }) }
    private val screen = CoroutineScope(Dispatchers.Main)

    @Before
    fun setUp() = WorkManagerTestInitHelper.initializeTestWorkManager(context)

    @After
    fun tearDown() {
        screen.cancel()
        env.close()
    }

    @Test
    fun `photos alone are enough to import, and the import takes them along`() {
        val vm = open(SavedStateHandle())
        var shown = vm.state.value
        vm.state.onEach { shown = it }.launchIn(screen)
        assertFalse(shown.canImport)

        vm.addPhotos(listOf(Uri.fromFile(photo())))
        awaitUntil { shown.photos.singleOrNull()?.file != null }
        assertTrue("no link and no text needed", shown.canImport)

        vm.import()
        awaitUntil { shown.import != null }
        val sent =
            shown.import!!
                .request.photos
                .single()
        assertEquals("import-photos", sent.parentFile!!.parentFile!!.name)
        assertTrue(sent.exists())
    }

    @Test
    fun `photos added are still there after Android ended the app`() {
        val saved = SavedStateHandle()
        val vm = open(saved)
        vm.state.launchIn(screen)
        vm.addPhotos(listOf(Uri.fromFile(photo()), Uri.fromFile(photo())))
        awaitUntil {
            vm.state.value.photos
                .count { it.file != null } == 2
        }

        val restored = open(saved).state.value.photos

        assertEquals(
            vm.state.value.photos
                .map { it.file },
            restored.map { it.file },
        )
    }

    @Test
    fun `no more photos are taken than an import sends`() {
        val vm = open(SavedStateHandle())
        vm.state.launchIn(screen)

        vm.addPhotos(List(MAX_IMPORT_PHOTOS + 2) { Uri.fromFile(photo()) })
        awaitUntil {
            vm.state.value.photos
                .count { it.file != null } == MAX_IMPORT_PHOTOS
        }
        vm.addPhotos(listOf(Uri.fromFile(photo())))

        assertEquals(MAX_IMPORT_PHOTOS, vm.state.value.photos.size)
    }

    private fun open(saved: SavedStateHandle): AddRecipeViewModel {
        val importer = RecipeImporter(env.recipes, SocialPosts(OkHttpClient()) { "Test browser" }, untitled = { "Rezept" })
        return AddRecipeViewModel(env.recipes, RecipeImports(context, env.recipes, importer), settings, null, shownJob = null, saved)
    }

    private fun photo(): File {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFC8A165.toInt()) }
        return folder.newFile().apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
    }

    /** Lets the view model's work on the main thread and in the background go on until [done]. */
    private fun awaitUntil(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }
}
