package app.aroma.ui.navigation

import android.content.Intent
import android.text.SpannableString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LaunchRequestTest {
    private fun shared(text: CharSequence) = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)

    @Test
    fun sharedTextImportsItsLinkWithoutTrailingPunctuation() {
        assertEquals(
            LaunchRequest.Import("https://www.instagram.com/p/abc/"),
            LaunchRequest.from(shared("Look at this (https://www.instagram.com/p/abc/).")),
        )
    }

    @Test
    fun styledSharedTextIsReadToo() {
        assertEquals(
            LaunchRequest.Import("https://example.com/soup"),
            LaunchRequest.from(shared(SpannableString("https://example.com/soup"))),
        )
    }

    @Test
    fun sharedTextWithoutLinkAsksForNothing() {
        assertNull(LaunchRequest.from(shared("Just some words")))
    }

    @Test
    fun eachShortcutOpensWhatItNames() {
        Shortcut.entries.forEach { assertEquals(it.request, LaunchRequest.from(Intent(it.action))) }
    }

    @Test
    fun aPlainStartAsksForNothing() {
        assertNull(LaunchRequest.from(Intent(Intent.ACTION_MAIN)))
    }
}
