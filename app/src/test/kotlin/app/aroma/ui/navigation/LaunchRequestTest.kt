package app.aroma.ui.navigation

import android.content.Intent
import android.net.Uri
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
    fun sharedTextWithoutLinkWaitsOnTheAddScreen() {
        assertEquals(
            LaunchRequest.Shared(text = "Leek fritters with 2 leeks"),
            LaunchRequest.from(shared("  Leek fritters with 2 leeks\n")),
        )
    }

    @Test
    fun aRecipesTextSharedWithItsLinkWaitsOnTheAddScreenWhole() {
        val text = "https://www.instagram.com/p/abc/\n\nLeek fritters\nIngredients:\n2 leeks"

        assertEquals(LaunchRequest.Shared(text = text), LaunchRequest.from(shared(text)))
    }

    @Test
    fun blankSharedTextAsksForNothing() {
        assertNull(LaunchRequest.from(shared(" ")))
    }

    @Test
    fun aSharedPhotoWaitsOnTheAddScreen() {
        val photo = Uri.parse("content://media/external/images/media/12")
        val intent = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, photo)

        assertEquals(LaunchRequest.Shared(photos = listOf(photo)), LaunchRequest.from(intent))
    }

    @Test
    fun sharedPhotosWaitOnTheAddScreenTogether() {
        val photos =
            arrayListOf(Uri.parse("content://media/external/images/media/12"), Uri.parse("content://media/external/images/media/13"))
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, photos)

        assertEquals(LaunchRequest.Shared(photos = photos), LaunchRequest.from(intent))
    }

    @Test
    fun aSharedFileIsNoPhoto() {
        val file = Uri.parse("file:///data/data/app.aroma/shared_prefs/session.xml")
        val intent = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, file)

        assertNull(LaunchRequest.from(intent))
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
