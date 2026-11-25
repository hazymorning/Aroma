package app.aroma.screenshots

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import java.io.File
import kotlin.math.roundToInt

/** How a screenshot is taken: in which language and theme, at which font size, on a phone how wide. */
data class Look(
    val locale: String,
    val dark: Boolean,
    val fontScale: Float = 1f,
    val width: Int = PHONE_WIDTH,
) {
    val suffix =
        "${if (dark) "dark" else "light"}_$locale" +
            (if (fontScale == 1f) "" else "_${(fontScale * 100).roundToInt()}") +
            (if (width == PHONE_WIDTH) "" else "_${width}dp")

    override fun toString() = suffix

    companion object {
        /** A typical phone's window, in dp. */
        const val PHONE_WIDTH = 411
        const val PHONE_HEIGHT = 891
    }
}

/**
 * Base for the screenshots of screens. Recipe photos are drawn as soft gradients ([photo]), from
 * the server's addresses and from files on the phone alike, so the images stay alike from run to run.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(DelicateCoilApi::class)
    @Before
    fun installPhotos() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine =
            FakeImageLoaderEngine
                .Builder()
                .intercept({ it is String && it.startsWith(SERVER) }) { chain ->
                    SuccessResult(photo(chain.request.data as String).asImage(), chain.request, DataSource.MEMORY)
                }.intercept({ it is File }) { chain ->
                    SuccessResult(BitmapFactory.decodeFile((chain.request.data as File).path).asImage(), chain.request, DataSource.DISK)
                }.build()
        SingletonImageLoader.setUnsafe(
            ImageLoader
                .Builder(context)
                .components { add(engine) }
                .coroutineContext(Dispatchers.Unconfined)
                .build(),
        )
    }

    /** Renders [content] in [look]'s theme as build/outputs/roborazzi/[name].png, after [interact] had its way with it. */
    protected fun snapshot(name: String, look: Look, interact: () -> Unit = {}, content: @Composable () -> Unit) {
        composeRule.setContent {
            AromaTheme(darkTheme = look.dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
        }
        composeRule.waitForIdle()
        interact()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
        assertTapsAreNamed(name)
    }

    /**
     * Everything that takes a tap says what it is, in its own words or in a description for TalkBack:
     * a button without either is read out as an unnamed button.
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
}
