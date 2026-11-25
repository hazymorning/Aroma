package app.aroma.feature.cook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.aroma.core.format.IngredientFormatter
import app.aroma.core.format.ingredientsByStep
import app.aroma.core.model.Ingredient
import app.aroma.core.model.IngredientFood
import app.aroma.core.model.Recipe
import app.aroma.core.model.Step
import app.aroma.data.settings.IngredientsInSteps
import app.aroma.onNodeWithWords
import app.aroma.ui.components.IngredientRow
import app.aroma.ui.theme.AromaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
class StepIngredientsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a step shows none, the used or all ingredients, as chosen`() {
        var shown by mutableStateOf(IngredientsInSteps.None)
        compose.setContent {
            AromaTheme { CookScreen(CookUiState(recipe = soup, isLoading = false, ingredientsInSteps = shown), CookActions()) }
        }
        compose.onNodeWithWords("For this step").assertDoesNotExist()
        shown = IngredientsInSteps.Used
        compose.onNodeWithWords("For this step").assertExists()
        compose.onNodeWithWords("0 of 2").assertExists()
        shown = IngredientsInSteps.All
        compose.onNodeWithWords("All ingredients").assertExists()
        compose.onNodeWithWords("0 of 6").assertExists()
    }

    @Test
    fun `ticking off in a step ticks off the recipe's line and counts the step's own`() {
        val ticked = mutableListOf<Int>()
        val state = CookUiState(recipe = soup, isLoading = false, checkedIngredients = setOf(3, 5))
        show(state, page = 1, actions = CookActions(onToggleIngredient = { ticked += it }))
        compose.onNodeWithWords("1 of 4").assertExists()
        compose.onNodeWithWords("garlic cloves").performClick()
        assertEquals(listOf(2), ticked)
    }

    @Test
    fun `the step buttons stay where they are as the step turns`() {
        show(CookUiState(recipe = soup, isLoading = false))
        val before = buttons()
        compose.onNodeWithWords("Next").performClick()
        compose.waitForIdle()
        assertEquals(before, buttons())
    }

    @Test
    fun `a step that uses nothing shows no ingredients`() {
        val simmer = soup.ingredientsByStep(Locale.ENGLISH)[2]
        assertTrue(stepIngredients(rows(soup), simmer, IngredientsInSteps.Used).rows.isEmpty())
    }

    @Test
    fun `a recipe whose steps link and name none of its ingredients shows none in any step`() {
        val unlinked = soup.copy(steps = soup.steps.map { it.copy(text = "Stir.", ingredientReferences = emptyList()) })
        val shown = unlinked.ingredientsByStep(Locale.ENGLISH).map { stepIngredients(rows(unlinked), it, IngredientsInSteps.Used).rows }
        assertEquals(List(unlinked.steps.size) { emptyList<IngredientRow>() }, shown)
    }

    private fun show(state: CookUiState, page: Int = 0, actions: CookActions = CookActions()) {
        compose.setContent { AromaTheme { CookScreen(state, actions, initialPage = page) } }
    }

    private fun buttons() =
        listOf(
            compose.onNodeWithWords("Next").fetchSemanticsNode().boundsInRoot,
            compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot,
        )

    private fun rows(recipe: Recipe) =
        recipe.ingredients.map { IngredientRow(it.section, IngredientFormatter.format(it, 1.0, Locale.ENGLISH)) }

    private companion object {
        fun ingredient(reference: String, quantity: Double, food: String, plural: String? = null) =
            Ingredient(reference, null, quantity, null, IngredientFood(null, food, plural), "", "", null)

        fun step(text: String, vararg uses: String) = Step(null, null, text, uses.toList(), null)

        /** A soup whose four steps use two, four, none and one of its six ingredients, linked as Mealie links them. */
        val soup =
            Recipe(
                id = "soup",
                slug = "soup",
                name = "Coconut soup",
                description = "",
                image = null,
                totalTime = null,
                prepTime = null,
                cookTime = null,
                servings = 4.0,
                rating = null,
                categories = emptyList(),
                tags = emptyList(),
                tools = emptyList(),
                sourceUrl = null,
                ingredients =
                    listOf(
                        ingredient("oil", 1.0, "spoon of oil", "spoons of oil"),
                        ingredient("onion", 1.0, "onion", "onions"),
                        ingredient("garlic", 2.0, "garlic clove", "garlic cloves"),
                        ingredient("ginger", 1.0, "piece of ginger"),
                        ingredient("coconut", 1.0, "can of coconut milk"),
                        ingredient("lime", 1.0, "lime"),
                    ),
                steps =
                    listOf(
                        step("Soften the onion in the oil.", "oil", "onion"),
                        step("Fry the garlic and ginger with it, then pour in the coconut milk.", "onion", "garlic", "ginger", "coconut"),
                        step("Simmer for 20 minutes."),
                        step("Season with lime.", "lime"),
                    ),
                notes = emptyList(),
                nutrition = emptyList(),
            )
    }
}
