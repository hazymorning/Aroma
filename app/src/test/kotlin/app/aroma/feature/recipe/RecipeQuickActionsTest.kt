package app.aroma.feature.recipe

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
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
@Config(sdk = [36], qualifiers = "en")
class RecipeQuickActionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val recipe = SampleData(german = false).recipe

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

    @Test
    fun `a recipe without ingredients offers planning only`() {
        show(recipe.copy(ingredients = emptyList()), RecipeActions())
        compose.onNodeWithContentDescription("Plan").assertExists()
        compose.onNodeWithContentDescription("Shop").assertDoesNotExist()
    }

    @Test
    fun `the menu holds what manages the recipe, rating included`() {
        val chosen = mutableListOf<String>()
        show(recipe.copy(rating = null), RecipeActions(onEdit = { chosen += "edit" }))
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithWords("Rate this recipe").assertExists()
        compose.onNodeWithWords("Share").assertExists()
        compose.onNodeWithWords("Delete").assertExists()
        compose.onNodeWithWords("Edit").performClick()
        assertEquals(listOf("edit"), chosen)
    }

    private fun show(recipe: app.aroma.core.model.Recipe, actions: RecipeActions) {
        compose.setContent {
            AromaTheme { RecipeScreen(RecipeUiState(recipe = recipe, isLoading = false), actions) }
        }
    }
}
