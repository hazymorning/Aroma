package app.aroma.feature.shopping

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.aroma.core.model.ShoppingItem
import app.aroma.core.model.ShoppingLabel
import app.aroma.core.model.ShoppingList
import app.aroma.core.model.ShoppingListInfo
import app.aroma.hasWords
import app.aroma.onNodeWithWords
import app.aroma.ui.theme.AromaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

/** What a finger and TalkBack do with the rows of the list: hold, drag, edit and move them. */
@RunWith(RobolectricTestRunner::class)
class ShoppingGesturesTest {
    @get:Rule
    val compose = createComposeRule()

    private val reordered = mutableListOf<List<String>>()
    private val toggled = mutableListOf<String>()
    private val edited = mutableListOf<Triple<String, String?, ShoppingLabel?>>()

    @Before
    fun showList() {
        RuntimeEnvironment.setQualifiers("en-w360dp-h800dp-xxhdpi")
        compose.setContent {
            AromaTheme(darkTheme = false) {
                ShoppingScreen(
                    state = ShoppingUiState(lists = listOf(ShoppingListInfo(LIST.id, LIST.name)), list = LIST, isLoading = false),
                    actions =
                        ShoppingActions(
                            onReorder = { reordered += it },
                            onToggle = { toggled += it.id },
                            onEditItem = { item, text, aisle -> edited += Triple(item.id, text, aisle) },
                        ),
                )
            }
        }
    }

    @Test
    fun `an item held and dragged down its aisle takes the place of the one it passed`() {
        val avocados = compose.onNodeWithWords("2 avocados", substring = true)
        avocados.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HOLD_MILLIS)
        avocados.performTouchInput { moveBy(Offset(0f, 72.dp.toPx())) }
        compose.mainClock.advanceTimeBy(HOLD_MILLIS)
        avocados.performTouchInput { up() }
        compose.waitForIdle()

        assertEquals(listOf(listOf("coriander", "avocado", "bananas")), reordered)
        assertTrue("a drag is no tap", toggled.isEmpty())
    }

    @Test
    fun `an item held and let go where it was opens to be edited, and no tick`() {
        compose.onNodeWithWords("2 avocados", substring = true).performTouchInput { longClick(center) }
        compose.waitForIdle()

        compose.onNodeWithWords("Edit item").assertIsDisplayed()
        assertTrue("nothing moved", reordered.isEmpty())
        assertTrue("a hold is no tap", toggled.isEmpty())
    }

    @Test
    fun `an item edited into another aisle sends the aisle, and not its text, which stayed`() {
        compose.onNodeWithWords("2 avocados", substring = true).performTouchInput { longClick(center) }
        compose.onNode(hasWords("Pantry") and hasClickAction()).performClick()
        compose.onNodeWithWords("Save").performClick()
        compose.waitForIdle()

        assertEquals(listOf(Triple("avocado", null, PANTRY)), edited)
    }

    @Test
    fun `TalkBack moves an item down its aisle as dragging would`() {
        val row =
            compose.onNode(
                SemanticsMatcher("avocados") { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains("avocados") } ==
                        true
                },
            )
        val down = row.fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Move down" }
        compose.runOnIdle { down.action() }

        assertEquals(listOf(listOf("coriander", "avocado", "bananas")), reordered)
        assertTrue(
            "the first can go no further up",
            row.fetchSemanticsNode().config[SemanticsActions.CustomActions].none {
                it.label ==
                    "Move up"
            },
        )
    }

    @Test
    fun `the list is shared as its aisles with what is still open in them`() {
        val text = shareText(LIST.name, LIST.grouped(), "Other", Locale.ENGLISH)

        assertEquals("Weekly shop\n\nProduce\n• 2 avocados\n• 1 bunch coriander\n• 2 bananas\n\nPantry\n• Rice", text)
    }

    @Test
    fun `the list's menu offers to tick everything off`() {
        compose.onNodeWithContentDescription("More options").performClick()

        compose.onNodeWithWords("Check off everything").assertIsDisplayed()
        compose.onNodeWithWords("Order the aisles").assertIsDisplayed()
    }

    private companion object {
        /** Longer than a long press, so a row is held. */
        const val HOLD_MILLIS = 700L

        val PRODUCE = ShoppingLabel("produce", "Produce")
        val PANTRY = ShoppingLabel("pantry", "Pantry")

        fun item(
            id: String,
            note: String,
            position: Int,
            label: ShoppingLabel?,
            checked: Boolean = false,
        ) = ShoppingItem(id, "list", 0.0, null, null, note, checked, position, label, emptyList())

        val LIST =
            ShoppingList(
                id = "list",
                name = "Weekly shop",
                items =
                    listOf(
                        item("avocado", "2 avocados", 0, PRODUCE),
                        item("coriander", "1 bunch coriander", 1, PRODUCE),
                        item("bananas", "2 bananas", 2, PRODUCE),
                        item("rice", "Rice", 3, PANTRY),
                        item("towels", "Paper towels", 4, null, checked = true),
                    ),
                labelOrder = listOf("produce", "pantry"),
                labels = listOf(PRODUCE, PANTRY),
            )
    }
}
