package app.aroma.feature.recipe

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aroma.screenshots.SampleData
import app.aroma.ui.theme.AromaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en")
class RecipeQuickActionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val recipe = SampleData(german = false).recipe

    @Test
    fun `plan and shop sit right under the title and open their sheets`() {
        val opened = mutableListOf<String>()
        show(recipe, RecipeActions(onAddToMealPlan = { opened += "plan" }, onAddToShoppingList = { opened += "shop" }))
        compose.onNodeWithText("Plan").performClick()
        compose.onNodeWithText("Shop").performClick()
        assertEquals(listOf("plan", "shop"), opened)
    }

    @Test
    fun `a recipe without ingredients offers planning only`() {
        show(recipe.copy(ingredients = emptyList()), RecipeActions())
        compose.onNodeWithText("Plan").assertExists()
        compose.onNodeWithText("Shop").assertDoesNotExist()
    }

    @Test
    fun `both actions stay reachable from the menu once the row has scrolled away`() {
        val opened = mutableListOf<String>()
        show(recipe, RecipeActions(onAddToMealPlan = { opened += "plan" }, onAddToShoppingList = { opened += "shop" }))
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Add to meal plan").performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Add to shopping list").performClick()
        assertEquals(listOf("plan", "shop"), opened)
    }

    private fun show(recipe: app.aroma.core.model.Recipe, actions: RecipeActions) {
        compose.setContent {
            AromaTheme { RecipeScreen(RecipeUiState(recipe = recipe, isLoading = false), actions) }
        }
    }
}
