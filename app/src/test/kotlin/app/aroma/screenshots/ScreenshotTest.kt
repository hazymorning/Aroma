package app.aroma.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.aroma.ui.theme.AromaTheme
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.request.SuccessResult
import coil3.test.FakeImageLoaderEngine
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.robolectric.annotation.GraphicsMode

/**
 * Base for screenshot tests. Recipe photos are replaced by soft generated gradients,
 * so the images are deterministic and free of third-party content.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    protected open fun photoFor(url: String): Bitmap = gradient(url)

    @OptIn(DelicateCoilApi::class)
    @Before
    fun installImages() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine =
            FakeImageLoaderEngine
                .Builder()
                .intercept({ it is String && it.startsWith(SampleData.SERVER) }) { chain ->
                    SuccessResult(
                        image = photoFor(chain.request.data as String).asImage(),
                        request = chain.request,
                        dataSource = DataSource.MEMORY,
                    )
                }.build()
        val loader =
            ImageLoader
                .Builder(context)
                .components { add(engine) }
                .coroutineContext(Dispatchers.Unconfined)
                .build()
        SingletonImageLoader.setUnsafe(loader)
    }

    protected fun snapshot(name: String, dark: Boolean, interact: () -> Unit = {}, content: @Composable () -> Unit) {
        composeRule.setContent {
            AromaTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier) { content() }
            }
        }
        composeRule.waitForIdle()
        interact()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
        assertTapsAreNamed(name)
    }

    /**
     * Everything that takes a tap says what it is, in its own words or in a description for TalkBack
     * (section 9): a button without either is read out as an unnamed button.
     */
    private fun assertTapsAreNamed(name: String) {
        val unnamed =
            composeRule
                .onAllNodes(hasClickAction())
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .filter { node ->
                    val config = node.config
                    // A link inside a text is read with the text it is part of.
                    if (config.any { (key, _) -> key.name == "LinkTestMarker" }) return@filter false
                    val words = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }
                    val description = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString("")
                    words.isBlank() && description.isBlank() && !config.contains(SemanticsProperties.EditableText)
                }.map { node -> "${node.boundsInRoot} ${node.config}" }
        assertTrue("$name: taps without a name for TalkBack:\n" + unnamed.joinToString("\n"), unnamed.isEmpty())
    }

    private fun gradient(seed: String): Bitmap {
        val palettes =
            listOf(
                intArrayOf(0xFFB9A27A.toInt(), 0xFF7E8F62.toInt()),
                intArrayOf(0xFFCFAE82.toInt(), 0xFF9A6B45.toInt()),
                intArrayOf(0xFFA9B78F.toInt(), 0xFF5F7353.toInt()),
                intArrayOf(0xFFD8C3A0.toInt(), 0xFFB07B52.toInt()),
            )
        val colors = palettes[Math.floorMod(seed.hashCode(), palettes.size)]
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 400f, 400f, colors[0], colors[1], Shader.TileMode.CLAMP) }
        Canvas(bitmap).drawRect(0f, 0f, 400f, 400f, paint)
        return bitmap
    }
}
