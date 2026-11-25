package app.aroma.ui.theme

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * A change of the app's look, light to dark or the wallpaper's colours, dissolves instead of
 * switching in one frame: a picture of the window as it was lies over the app in its new colours
 * and fades away in the time of a change of state. Where the phone cannot take the picture, the
 * look switches at once, as before.
 */
@Stable
internal class LookFade {
    private var picture by mutableStateOf<ImageBitmap?>(null)
    private val shown = Animatable(0f)
    private var origin = Offset.Zero

    /** Takes the picture of [window] as it shows now, in the old look, and lays it over the app; false where it cannot. */
    suspend fun cover(window: Window): Boolean {
        val bitmap = window.picture() ?: return false
        shown.snapTo(1f)
        picture = bitmap.asImageBitmap()
        return true
    }

    /** Lets the picture fade away over the app, which shows its new look by now. */
    suspend fun dissolve() {
        shown.animateTo(0f, Motion.state())
        picture = null
    }

    /** Draws the picture over what [this] holds while it fades. */
    fun Modifier.overlay(): Modifier =
        onPlaced { origin = it.positionInWindow() }
            .drawWithContent {
                drawContent()
                val image = picture ?: return@drawWithContent
                val alpha = shown.value
                if (alpha > 0f) drawImage(image, topLeft = -origin, alpha = alpha)
            }
}

/** The window's pixels as they show now, or null where the phone cannot copy them in time. */
private suspend fun Window.picture(): Bitmap? {
    val view = decorView
    if (view.width <= 0 || view.height <= 0) return null
    val bitmap = createBitmap(view.width, view.height)
    return withTimeoutOrNull(PICTURE_TIMEOUT) {
        suspendCancellableCoroutine { copied ->
            try {
                PixelCopy.request(
                    this@picture,
                    bitmap,
                    { result -> if (copied.isActive) copied.resume(bitmap.takeIf { result == PixelCopy.SUCCESS }) },
                    Handler(Looper.getMainLooper()),
                )
            } catch (_: IllegalArgumentException) {
                // A window without a surface to copy from.
                copied.resume(null)
            }
        }
    }
}

/** How long the picture may take; a frame or two is usual. */
private const val PICTURE_TIMEOUT = 200L
