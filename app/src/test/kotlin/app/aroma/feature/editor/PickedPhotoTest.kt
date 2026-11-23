package app.aroma.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Photos are scaled down on the phone, turned upright and written as JPEG before they are sent. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PickedPhotoTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a large photo is scaled down to 2048 pixels on its longest side`() =
        runBlocking {
            val prepared = prepare(photo(4400, 3300))

            assertEquals(2048, prepared.width)
            assertEquals(1536, prepared.height)
        }

    @Test
    fun `a small photo keeps its size`() =
        runBlocking {
            val prepared = prepare(photo(800, 600))

            assertEquals(800, prepared.width)
            assertEquals(600, prepared.height)
        }

    @Test
    fun `a photo the camera took turned comes out upright`() =
        runBlocking {
            // Saved as the sensor saw it: red on the left; the camera was held so that the left is the top.
            val prepared = prepare(photo(600, 300, orientation = ExifInterface.ORIENTATION_ROTATE_90))

            assertEquals(300, prepared.width)
            assertEquals(600, prepared.height)
            assertTrue("red on top", prepared.getPixel(150, 100).isRed())
            assertFalse("blue at the bottom", prepared.getPixel(150, 500).isRed())
        }

    @Test
    fun `a mirrored photo comes out the right way round`() =
        runBlocking {
            val prepared = prepare(photo(600, 300, orientation = ExifInterface.ORIENTATION_FLIP_HORIZONTAL))

            assertEquals(600, prepared.width)
            assertFalse(prepared.getPixel(100, 150).isRed())
            assertTrue(prepared.getPixel(500, 150).isRed())
        }

    @Test
    fun `the editor's upload is scaled down the same way`() =
        runBlocking {
            val picked = readPickedPhoto(context, Uri.fromFile(photo(4400, 3300, orientation = ExifInterface.ORIENTATION_ROTATE_270)))!!

            val decoded = BitmapFactory.decodeByteArray(picked.bytes, 0, picked.bytes.size)
            assertEquals("jpg", picked.extension)
            assertEquals(1536, decoded.width)
            assertEquals(2048, decoded.height)
        }

    @Test
    fun `a file that is no photo is not taken`() =
        runBlocking {
            val notAPhoto = folder.newFile("notes.jpg").apply { writeText("Zutaten: Mehl, Eier") }
            val target = File(folder.root, "prepared.jpg")

            assertFalse(savePickedPhoto(context, Uri.fromFile(notAPhoto), target))
            assertFalse("nothing is left behind", target.exists())
            assertNull(readPickedPhoto(context, Uri.fromFile(notAPhoto)))
        }

    private suspend fun prepare(source: File): Bitmap {
        val target = File(folder.root, "prepared-${source.name}")
        assertTrue(savePickedPhoto(context, Uri.fromFile(source), target))
        return BitmapFactory.decodeFile(target.path)
    }

    /** A JPEG red in its left half and blue in its right half, with the camera's [orientation] noted in it. */
    private fun photo(width: Int, height: Int, orientation: Int = ExifInterface.ORIENTATION_NORMAL): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
            drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
        }
        val file = folder.newFile()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        if (orientation != ExifInterface.ORIENTATION_NORMAL) {
            ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
        return file
    }

    private fun Int.isRed() = Color.red(this) > 180 && Color.blue(this) < 80
}
