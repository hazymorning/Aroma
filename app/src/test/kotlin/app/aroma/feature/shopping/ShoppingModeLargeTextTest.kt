package app.aroma.feature.shopping

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.IdlingPolicy
import app.aroma.core.model.ShoppingItem
import app.aroma.core.model.ShoppingList
import app.aroma.core.model.ShoppingListInfo
import app.aroma.onNodeWithWords
import app.aroma.ui.theme.AromaTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.TimeUnit

/**
 * Shopping mode at 200 % font size on a 360 dp phone. The list's menu is too narrow there for some
 * of its rows, which end in "…"; the menu narrows to the shortened rows and has to come to rest
 * there, rather than widen for the whole words again, and narrow, frame after frame. It shows only
 * with real fonts, which the tests draw with (robolectric.properties).
 */
@RunWith(RobolectricTestRunner::class)
class ShoppingModeLargeTextTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var idling: IdlingPolicy

    /** A page that never comes to rest fails within seconds, not after the minute Compose waits. */
    @Before
    fun waitSeconds() {
        idling = IdlingPolicies.getMasterIdlingPolicy()
        IdlingPolicies.setMasterPolicyTimeout(IDLE_SECONDS, TimeUnit.SECONDS)
    }

    @After
    fun waitAsBefore() = IdlingPolicies.setMasterPolicyTimeout(idling.idleTimeout, idling.idleTimeoutUnit)

    @Test
    fun `the menu and shopping mode come to rest in German`() =
        startShoppingMode("de", more = "Weitere Optionen", toggle = "Einkaufsmodus", hint = "Große Schrift, der Bildschirm bleibt an.")

    @Test
    fun `the menu and shopping mode come to rest in English`() =
        startShoppingMode("en", more = "More options", toggle = "Start shopping", hint = "Large text, and the screen stays on.")

    private fun startShoppingMode(locale: String, more: String, toggle: String, hint: String) {
        RuntimeEnvironment.setQualifiers("$locale-w360dp-h800dp-xxhdpi")
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent {
            AromaTheme(darkTheme = false) {
                ShoppingScreen(
                    state = ShoppingUiState(lists = listOf(ShoppingListInfo(LIST.id, LIST.name)), list = LIST, isLoading = false),
                    actions = ShoppingActions(),
                )
            }
        }
        compose.onNodeWithContentDescription(more).performClick()
        // Each lookup waits for the page to rest first. The row is shortened, so it is found by the words it keeps.
        compose.onNodeWithWords(toggle, substring = true).performClick()
        compose.onNodeWithWords(hint).assertExists()
    }

    private companion object {
        const val IDLE_SECONDS = 5L

        fun item(id: Int, note: String, checked: Boolean = false) =
            ShoppingItem(id.toString(), "list", 0.0, null, null, note, checked, id, null, emptyList())

        val LIST =
            ShoppingList(
                id = "list",
                name = "Wocheneinkauf",
                items = listOf(item(1, "2 Avocados"), item(2, "300 ml Milch"), item(3, "6 Eier"), item(4, "Küchenrolle", checked = true)),
                labelOrder = emptyList(),
            )
    }
}
