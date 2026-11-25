package app.aroma

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import app.aroma.testing.ScreenTest
import app.aroma.testing.TestApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The whole app as someone goes through it once: every tab, a recipe and cooking it, the settings, signing out. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class)
class AppTourTest {
    @get:Rule(order = 0)
    val screen = ScreenTest()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Test
    fun `a tour of every tab, a recipe and cooking ends signed out on the login screen`() {
        val mealie = screen.mealie
        val soup = mealie.addRecipe("Linsensuppe", totalTime = "PT20M", categories = listOf("Abendessen"))
        repeat(RECIPES) { mealie.addRecipe("Rezept $it", totalTime = if (it % 2 == 0) "PT25M" else "PT1H") }
        mealie.addCookbook("Suppen", soup)
        mealie.plan(LocalDate.now(), soup)
        mealie.addList("Wocheneinkauf", "Milch", "Eier")

        ActivityScenario.launch(MainActivity::class.java).use {
            tap("Recipes")
            tap(hasContentDescription("Filter"))
            tap("Quick")
            pressBack()
            repeat(2) { compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() } }

            tap("Home")
            tap(hasContentDescription("Linsensuppe"))
            tap("Start cooking")
            tap(hasContentDescription("Leave cooking mode"))
            tap(hasContentDescription("Back"))

            tap("Meal plan")
            shows(reads("Linsensuppe"))
            tap("Shopping")
            shows(reads("Milch"))

            tap("Home")
            tap(hasContentDescription("Profile and settings"))
            tap("Sign out")
            tap(reads("Sign out") and hasAnyAncestor(isDialog()))
            // The login screen, asking for Mealie's address.
            shows(reads("Continue"))
        }
    }

    private fun tap(text: String) = tap(reads(text))

    private fun tap(matcher: SemanticsMatcher) {
        shows(matcher)
        val node = compose.onAllNodes(matcher).onFirst()
        // A node below the fold is brought into view first; one outside any scrolling page is in view already.
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun shows(matcher: SemanticsMatcher) =
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }

    /** Text as people read it, without the invisible marks that guide line breaks. */
    private fun reads(text: String) =
        SemanticsMatcher("reads '$text'") { node ->
            val shown = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            shown.any { it.text.filterNot { char -> char in INVISIBLE } == text }
        }

    private companion object {
        const val RECIPES = 40
        const val WAIT_MILLIS = 5_000L
        const val INVISIBLE = "​⁠"
    }
}
