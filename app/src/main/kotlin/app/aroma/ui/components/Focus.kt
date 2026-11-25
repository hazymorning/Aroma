package app.aroma.ui.components

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester

/**
 * Asks for focus a frame at a time, for up to [frames] frames, until what it is attached to is laid
 * out: a field that has just appeared, or one in a list's first item, is there only a frame or two
 * later. Says whether the focus was taken.
 */
suspend fun FocusRequester.requestWhenAttached(frames: Int = FOCUS_FRAMES): Boolean {
    repeat(frames) {
        withFrameNanos {}
        val taken =
            try {
                requestFocus(FocusDirection.Enter)
            } catch (_: IllegalStateException) {
                // Not attached yet.
                false
            }
        if (taken) return true
    }
    return false
}

/** How many frames a field is asked for focus while the page settles. */
private const val FOCUS_FRAMES = 10
