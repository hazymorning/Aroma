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
import app.aroma.data.session.Session
import app.aroma.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
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
import org.robolectric.Shadows.shadowOf
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
    private val settings by lazy { SettingsRepository(PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb") }) }
    private val screen = CoroutineScope(Dispatchers.Main)
    private val server = MockWebServer()

    @Before
    fun setUp() = WorkManagerTestInitHelper.initializeTestWorkManager(context)

    @After
    fun tearDown() {
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

    @Test
    fun `photos shared to Aroma are copied in for the photo import, and wait for the import button`() {
        serverReadsPhotos()
        val shared = listOf(photo(), photo())

        val vm = open(SavedStateHandle(), shared.map(Uri::fromFile))
        vm.state.launchIn(screen)
        awaitUntil {
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
        serverReadsPhotos()

        val vm = open(SavedStateHandle(), List(MAX_IMPORT_PHOTOS + 2) { Uri.fromFile(photo()) })
        vm.state.launchIn(screen)
        awaitUntil {
            vm.state.value.photosAvailable && vm.state.value.photos
                .count { it.file != null } == MAX_IMPORT_PHOTOS
        }

        assertEquals(PhotosNote.FirstOnly, vm.state.value.photosNote)
    }

    @Test
    fun `photos shared to a server that cannot read them are set aside, and the screen says why`() {
        val vm = open(SavedStateHandle(), listOf(Uri.fromFile(photo())))
        vm.state.launchIn(screen)
        awaitUntil { vm.state.value.photosNote == PhotosNote.Unreadable }

        assertTrue(
            vm.state.value.photos
                .isEmpty(),
        )
        assertFalse("nothing is left to import", vm.state.value.canImport)
        awaitUntil { File(context.cacheDir, "import-photos").listFiles().isNullOrEmpty() }
    }

    private fun open(saved: SavedStateHandle, shared: List<Uri> = emptyList()): AddRecipeViewModel {
        val importer = RecipeImporter(env.recipes, SocialPosts(OkHttpClient()) { "Test browser" }, untitled = { "Rezept" })
        val imports = RecipeImports(context, env.recipes, importer)
        return AddRecipeViewModel(env.recipes, imports, settings, null, shownJob = null, saved, sharedPhotos = shared)
    }

    /** Signs in to a server whose group has Mealie's AI with a provider for images. */
    private fun serverReadsPhotos() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.url.encodedPath == "/api/groups/self") {
                        MockResponse
                            .Builder()
                            .addHeader("Content-Type", "application/json")
                            .body("""{"aiProviderSettings":{"aiEnabled":true,"imageProviderEnabled":true}}""")
                            .build()
                    } else {
                        MockResponse.Builder().code(404).build()
                    }
            }
        server.start()
        val address = server.url("/").toString().trimEnd('/')
        runBlocking { env.sessions.start(Session(address, token = "t", userId = "user-1", username = "mara", serverVersion = "v3.31.0")) }
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
