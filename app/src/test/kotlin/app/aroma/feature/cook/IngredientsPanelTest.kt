package app.aroma.feature.cook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import app.aroma.data.settings.IngredientsInSteps
import app.aroma.screenshots.SampleData
import app.aroma.ui.theme.AromaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-w411dp-h891dp-xxhdpi")
class IngredientsPanelTest {
    @get:Rule
    val compose = createComposeRule()

    /** A soup whose steps use two, nine, none and two of its twelve ingredients. */
    private val curry = SampleData(german = false).curry

    private val panel = hasClickAction() and hasText("ingredients for this step", substring = true)

    @Test
    fun `paging from two ingredients to nine moves nothing around the step, frame by frame`() {
        show(CookUiState(recipe = curry, isLoading = false, timers = listOf(CookTimerUi(1, "Step 2", 432, isRunning = true))))
        val still = frames()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Next").performClick()
        repeat(40) {
            compose.mainClock.advanceTimeByFrame()
            assertEquals(still, frames())
        }
        compose.onNodeWithText("9 ingredients for this step").assertExists()
    }

    @Test
    fun `ticking off in the open panel ticks off the recipe's line, as the ingredients sheet does`() {
        val ticked = mutableListOf<Int>()
        show(CookUiState(recipe = curry, isLoading = false), initialPage = 1, actions = CookActions(onToggleIngredient = { ticked += it }))
        compose.onNode(panel).performClick()
        compose.onNodeWithText("garlic cloves").performClick()
        assertEquals(listOf(2), ticked)
    }

    @Test
    fun `the panel shows none, the used or all ingredients, as chosen`() {
        var shown by mutableStateOf(IngredientsInSteps.None)
        compose.setContent {
            AromaTheme {
                CookScreen(
                    CookUiState(recipe = curry, isLoading = false, ingredientsInSteps = shown),
                    CookActions(),
                )
            }
        }
        compose.onNode(panel).assertDoesNotExist()
        shown = IngredientsInSteps.Used
        compose.onNodeWithText("2 ingredients for this step").assertExists()
        shown = IngredientsInSteps.All
        compose.onNodeWithText("All 12 ingredients").assertExists()
    }

    @Test
    fun `a sideways swipe on the panel turns the step, as one on the step does`() {
        show(CookUiState(recipe = curry, isLoading = false))
        compose.onNode(panel).performTouchInput { swipeLeft() }
        compose.onNodeWithText("9 ingredients for this step").assertExists()
        compose.onNodeWithText("Step 2 of 4").assertIsDisplayed()
        compose.onNode(panel).performTouchInput { swipeRight() }
        compose.onNodeWithText("2 ingredients for this step").assertExists()
        compose.onNodeWithText("Step 1 of 4").assertIsDisplayed()
    }

    @Test
    fun `a step that uses nothing says so, with nothing to open`() {
        show(CookUiState(recipe = curry, isLoading = false), initialPage = 2)
        compose.onNodeWithText("No ingredients in this step").assertHasNoClickAction()
    }

    @Test
    fun `a recipe whose steps name none of its ingredients has no panel`() {
        val unlinked = curry.copy(steps = curry.steps.map { it.copy(text = "Stir.", ingredientReferences = emptyList()) })
        show(CookUiState(recipe = unlinked, isLoading = false))
        compose.onNodeWithText("No ingredients in this step").assertDoesNotExist()
    }

    private fun show(state: CookUiState, initialPage: Int = 0, actions: CookActions = CookActions()) {
        compose.setContent { AromaTheme { CookScreen(state, actions, initialPage = initialPage) } }
    }

    /** The panel, the running timer and the step buttons, where they are on screen. */
    private fun frames() =
        listOf(
            compose.onNode(panel).fetchSemanticsNode().boundsInRoot,
            compose.onNodeWithContentDescription("Pause timer").fetchSemanticsNode().boundsInRoot,
            compose.onNodeWithText("Next").fetchSemanticsNode().boundsInRoot,
            compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot,
        )
}
