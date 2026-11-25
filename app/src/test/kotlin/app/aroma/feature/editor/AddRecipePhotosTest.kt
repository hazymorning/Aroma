package app.aroma.feature.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.aroma.data.TestEnvironment
import app.aroma.data.address
import app.aroma.data.json
import app.aroma.data.mealieServer
import app.aroma.data.remote.SocialPosts
import app.aroma.data.session.Session
import app.aroma.data.until
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Photos on the add screen: enough to import on their own, sent with the import, kept through the
 * end of the app; shared from another app, copied in for a look, where the server reads photos.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class AddRecipePhotosTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val env = TestEnvironment()
    private val screen = CoroutineScope(Dispatchers.Main)
    private val opened = mutableListOf<AddRecipeViewModel>()

    /** A server whose group has Mealie's AI with a provider for images. */
    private val server =
        mealieServer { request ->
            if (request.url.encodedPath == "/api/groups/self") {
                json("""{"aiProviderSettings":{"aiEnabled":true,"imageProviderEnabled":true}}""")
            } else {
                MockResponse.Builder().code(404).build()
            }
        }

    @Before
    fun setUp() = WorkManagerTestInitHelper.initializeTestWorkManager(context)

    @After
    fun tearDown() {
        // What the screens started would otherwise go on in the next test.
        opened.forEach { it.viewModelScope.cancel() }
        screen.cancel()
        env.close()
        server.close()
    }

    @Test
    fun `photos alone are enough to import, and the import takes them along`() {
        val vm = open(SavedStateHandle())
        var shown = vm.state.value
        vm.state.onEach { shown = it }.launchIn(screen)
        assertFalse(shown.canImport)

        vm.addPhotos(listOf(Uri.fromFile(photo())))
        until { shown.photos.singleOrNull()?.file != null }
        assertTrue("no link and no text needed", shown.canImport)

        vm.import()
        until { shown.import != null }
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
        until {
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
    fun `photos shared to Aroma are copied in for the photo import, and wait for the import button`() {
        signIn()
        val shared = listOf(photo(), photo())

        val vm = open(SavedStateHandle(), shared.map(Uri::fromFile))
        vm.state.launchIn(screen)
        until {
            vm.state.value.photosAvailable && vm.state.value.photos
                .count { it.file != null } == 2
        }

        val copies =
            vm.state.value.photos
                .map { it.file!! }
        assertTrue(copies.all { it.exists() && it.parentFile!!.name == "import-photos" })
        assertTrue("the shared files stay the other app's", copies.none { it in shared })
        assertFalse(vm.state.value.isImporting)
        assertTrue(vm.state.value.canImport)
        assertNull(vm.state.value.photosNote)
    }

    @Test
    fun `of more photos shared than an import sends, the first are taken, and the screen says so`() {
        signIn()

        val vm = open(SavedStateHandle(), List(MAX_IMPORT_PHOTOS + 2) { Uri.fromFile(photo()) })
        vm.state.launchIn(screen)
        until {
            vm.state.value.photosAvailable && vm.state.value.photos
                .count { it.file != null } == MAX_IMPORT_PHOTOS
        }

        assertEquals(PhotosNote.FirstOnly, vm.state.value.photosNote)
    }

    private fun open(saved: SavedStateHandle, shared: List<Uri> = emptyList()): AddRecipeViewModel {
        val importer = RecipeImporter(env.recipes, SocialPosts(OkHttpClient()) { "Test browser" }, untitled = { "Rezept" })
        val imports = RecipeImports(context, env.recipes, importer)
        return AddRecipeViewModel(env.recipes, imports, env.settings, null, shownJob = null, saved, sharedPhotos = shared).also(opened::add)
    }

    private fun signIn() {
        runBlocking {
            env.sessions.start(
                Session(server.address, token = "t", userId = "user-1", username = "mara", serverVersion = "v3.31.0"),
            )
        }
    }

    private fun photo(): File {
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFC8A165.toInt()) }
        return folder.newFile().apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
    }
}
