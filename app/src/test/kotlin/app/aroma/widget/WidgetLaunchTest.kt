package app.aroma.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.aroma.ui.navigation.LaunchRequest
import app.aroma.ui.navigation.Shortcut
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the widgets open: each intent they send comes back in the app as the request it was made from. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetLaunchTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun roundTrip(request: LaunchRequest, intent: Intent) = assertEquals(request, LaunchRequest.from(intent))

    @Test
    fun `a shopping widget's header opens its own list`() {
        val request = LaunchRequest.Shopping("weekly")
        roundTrip(request, request.intent(context))
    }

    @Test
    fun `a shopping widget's plus opens its list ready to add to`() {
        val request = LaunchRequest.Shopping("weekly", add = true)
        roundTrip(request, request.intent(context))
    }

    @Test
    fun `the address Glance gives each of a widget's intents changes nothing`() {
        val request = LaunchRequest.Shopping("weekly", add = true)
        roundTrip(request, request.intent(context).setData(Uri.parse("glance-action://plus")))
    }

    @Test
    fun `the shopping shortcut still opens the tab as it was`() {
        roundTrip(LaunchRequest.Shopping(), Shortcut.Shopping.intent(context))
        roundTrip(LaunchRequest.Shopping(), Intent(Shortcut.Shopping.action))
    }

    @Test
    fun `a planned recipe and the plan open as themselves`() {
        roundTrip(LaunchRequest.Recipe("lentil-soup"), LaunchRequest.Recipe("lentil-soup").intent(context))
        roundTrip(LaunchRequest.MealPlan, LaunchRequest.MealPlan.intent(context))
    }
}
