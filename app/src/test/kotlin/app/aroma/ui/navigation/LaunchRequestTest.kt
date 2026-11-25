package app.aroma.ui.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
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

    /** What the shortcuts, the widgets and the import notifications open comes back in the app as the request it was made from. */
    @Test
    fun everyIntentAromaMakesOpensWhatItWasMadeFor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val made =
            Shortcut.entries.map { it.request to it.intent(context) } +
                listOf(
                    LaunchRequest.Recipe("lentil-soup").let { it to it.intent(context) },
                    LaunchRequest.ShowImport(UUID(0, 1)).let { it to it.intent(context) },
                    LaunchRequest.MealPlan to LaunchRequest.MealPlan.intent(context),
                    LaunchRequest.Shopping("weekly").let { it to it.intent(context) },
                    LaunchRequest.Shopping("weekly", add = true).let { it to it.intent(context) },
                )
        made.forEach { (request, intent) ->
            assertEquals(request, LaunchRequest.from(intent))
            // Glance gives each intent of a widget an address of its own.
            assertEquals(request, LaunchRequest.from(Intent(intent).setData(Uri.parse("glance-action:ACTIVITY?appWidgetId=1&viewId=2"))))
        }
    }

    @Test
    fun aPlainStartAsksForNothing() {
        assertNull(LaunchRequest.from(Intent(Intent.ACTION_MAIN)))
    }
}
