package app.aroma.feature.editor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.aroma.data.TestEnvironment
import app.aroma.data.remote.SocialPosts
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** An import keeps its photos where nothing removes them while it runs, and removes them when it ends. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ImportPhotoFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val env = TestEnvironment()
    private val imports =
        RecipeImports(
            context,
            env.recipes,
            RecipeImporter(env.recipes, SocialPosts(OkHttpClient()) { "Test browser" }, untitled = { "Rezept" }),
        )

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        ImportNotifications.createChannel(context)
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun `an import keeps copies of its photos until it ends, then deletes them`() =
        runBlocking {
            val prepared = listOf(photo("a"), photo("b"))

            val id = imports.start(ImportRequest("", photos = prepared))

            val kept =
                imports
                    .job(id)
                    .value!!
                    .request.photos
            assertEquals(2, kept.size)
            kept.zip(prepared).forEach { (copy, original) ->
                assertEquals(File(context.noBackupFilesDir, "import-photos/$id"), copy.parentFile)
                assertArrayEquals(original.readBytes(), copy.readBytes())
            }
            assertTrue("the screen keeps its own for another try", prepared.all { it.exists() })

            // Signed out, the import fails at once, which is all this test needs.
            imports.run(id, ImportRequest("", photos = kept), System.currentTimeMillis(), rerun = false)

            assertTrue(imports.job(id).value?.outcome is ImportOutcome.Failed)
            assertFalse(kept.any { it.exists() })
            assertFalse(File(context.noBackupFilesDir, "import-photos/$id").exists())
        }

    @Test
    fun `the worker keeps the photos with the rest of the request`() {
        val request =
            ImportRequest(
                "https://www.example.com/kartoffelsuppe",
                text = "Omas Rezept",
                useAi = true,
                translateTo = "de-DE",
                photos = listOf(File("/data/import-photos/1/photo-1.jpg"), File("/data/import-photos/1/photo-2.jpg")),
            )

        assertEquals(request, ImportWorker.request(ImportWorker.input(request, startedAt = 1L)))
    }

    @Test
    fun `signing out drops the photos of the imports still queued`() =
        runBlocking {
            val id = imports.start(ImportRequest("", photos = listOf(photo("a"))))

            imports.cancelAll()

            assertFalse(File(context.noBackupFilesDir, "import-photos/$id").exists())
        }

    @Test
    fun `a photo is prepared in the cache, and a photo the camera took for it is removed`() =
        runBlocking {
            val taken = newCameraPhoto(context)
            context.contentResolver.openOutputStream(taken)!!.use { it.write(photo("camera").readBytes()) }

            val prepared = imports.preparePhoto(taken)

            assertNotNull(prepared)
            assertEquals(File(context.cacheDir, "import-photos"), prepared!!.parentFile)
            assertNotNull(BitmapFactory.decodeFile(prepared.path))
            assertTrue(File(context.cacheDir, "camera").listFiles().isNullOrEmpty())
        }

    @Test
    fun `a photo that cannot be read is not prepared`() =
        runBlocking {
            val notAPhoto = folder.newFile("notes.jpg").apply { writeText("Zutaten") }

            assertEquals(null, imports.preparePhoto(Uri.fromFile(notAPhoto)))
        }

    private fun photo(name: String): File {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFC8A165.toInt()) }
        return folder.newFile("$name.jpg").apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
    }
}
