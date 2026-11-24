package app.aroma.feature.cook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.aroma.data.settings.IngredientsInSteps
import app.aroma.onNodeWithWords
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
class StepIngredientsTest {
    @get:Rule
    val compose = createComposeRule()

    /** A soup whose steps use two, nine, none and two of its twelve ingredients. */
    private val curry = SampleData(german = false).curry

    @Test
    fun `a step shows none, the used or all ingredients, as chosen`() {
        var shown by mutableStateOf(IngredientsInSteps.None)
        compose.setContent {
            AromaTheme {
                CookScreen(
                    CookUiState(recipe = curry, isLoading = false, ingredientsInSteps = shown),
                    CookActions(),
                )
            }
        }
        compose.onNodeWithWords("For this step").assertDoesNotExist()
        shown = IngredientsInSteps.Used
        compose.onNodeWithWords("For this step").assertExists()
        compose.onNodeWithWords("0 of 2").assertExists()
        shown = IngredientsInSteps.All
        compose.onNodeWithWords("All ingredients").assertExists()
        compose.onNodeWithWords("0 of 12").assertExists()
    }

    @Test
    fun `ticking off in a step ticks off the recipe's line, as the ingredients sheet does`() {
        val ticked = mutableListOf<Int>()
        show(CookUiState(recipe = curry, isLoading = false), initialPage = 1, actions = CookActions(onToggleIngredient = { ticked += it }))
        compose.onNodeWithWords("garlic cloves").performClick()
        assertEquals(listOf(2), ticked)
    }

    @Test
    fun `the count says how many of the step's ingredients are ticked off`() {
        show(CookUiState(recipe = curry, isLoading = false, checkedIngredients = setOf(2, 3, 11)), initialPage = 1)
        compose.onNodeWithWords("2 of 9").assertExists()
    }

    @Test
    fun `a step that uses nothing shows no ingredients at all`() {
        show(CookUiState(recipe = curry, isLoading = false), initialPage = 2)
        compose.onNodeWithWords("For this step").assertDoesNotExist()
    }

    @Test
    fun `a recipe whose steps name none of its ingredients shows none`() {
        val unlinked = curry.copy(steps = curry.steps.map { it.copy(text = "Stir.", ingredientReferences = emptyList()) })
        show(CookUiState(recipe = unlinked, isLoading = false))
        compose.onNodeWithWords("For this step").assertDoesNotExist()
    }

    @Test
    fun `the step buttons stay where they are as the step turns`() {
        show(CookUiState(recipe = curry, isLoading = false))
        val before = buttons()
        compose.onNodeWithWords("Next").performClick()
        compose.waitForIdle()
        assertEquals(before, buttons())
    }

    private fun show(state: CookUiState, initialPage: Int = 0, actions: CookActions = CookActions()) {
        compose.setContent { AromaTheme { CookScreen(state, actions, initialPage = initialPage) } }
    }

    private fun buttons() =
        listOf(
            compose.onNodeWithWords("Next").fetchSemanticsNode().boundsInRoot,
            compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot,
        )
}
