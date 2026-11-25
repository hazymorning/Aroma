package app.aroma.testing

import android.os.Looper
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Lets the main thread run, where view models work, until [done]; meanwhile their work in the
 * background goes on, and the server answers.
 */
internal fun until(done: () -> Boolean) {
    val deadline = TimeSource.Monotonic.markNow() + 10.seconds
    while (true) {
        shadowOf(Looper.getMainLooper()).idle()
        if (done()) return
        check(deadline.hasNotPassedNow()) { "Gave up waiting" }
        Thread.sleep(1)
    }
}
