package app.aroma.feature.recipe

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.aroma.core.model.Ingredient
import app.aroma.core.model.IngredientFood
import app.aroma.core.model.Step
import app.aroma.onNodeWithWords
import app.aroma.testing.sampleRecipe
import app.aroma.ui.theme.AromaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class RecipeQuickActionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val recipe =
        sampleRecipe(
            name = "Fluffy pancakes",
            ingredients = listOf(Ingredient(null, null, 2.0, null, IngredientFood(null, "egg", "eggs"), "", "", null)),
            steps = listOf(Step(null, null, "Whisk the eggs.", emptyList(), null)),
        )

    @Test
    fun `plan and shop sit in the action bar beside cooking and open their sheets`() {
        val opened = mutableListOf<String>()
        show(
            recipe,
            RecipeActions(
                onAddToMealPlan = { opened += "plan" },
                onAddToShoppingList = { opened += "shop" },
                onStartCooking = { opened += "cook" },
            ),
        )
        compose.onNodeWithContentDescription("Plan").performClick()
        compose.onNodeWithContentDescription("Shop").performClick()
        compose.onNodeWithWords("Start cooking").performClick()
        assertEquals(listOf("plan", "shop", "cook"), opened)
    }

    private fun show(recipe: app.aroma.core.model.Recipe, actions: RecipeActions) {
        compose.setContent {
            AromaTheme { RecipeScreen(RecipeUiState(recipe = recipe, isLoading = false), actions) }
        }
    }
}
