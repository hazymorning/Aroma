package app.aroma.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performClick
import app.aroma.feature.recipe.RecipeActionsSheet
import app.aroma.feature.recipes.RecipesActions
import app.aroma.feature.recipes.RecipesScreen
import app.aroma.feature.recipes.RecipesUiState
import app.aroma.onNodeWithWords
import app.aroma.ui.theme.AromaTheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A sheet as it closes, frame by frame, over the recipes: the dimming and the sheet's shadow go with
 * the sheet, and the page is clear once it has gone, long before the sheet's window closes.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SheetMotionScreenshotTest(private val look: Look) : ScreenshotTest() {
    private val sample = SampleData(look.locale == "de")

    @Before
    fun applyLook() {
        RuntimeEnvironment.setQualifiers("${look.locale}-w${look.width}dp-h${Look.PHONE_HEIGHT}dp-xxhdpi")
    }

    @Test
    fun sheetCloses() {
        composeRule.setContent {
            AromaTheme(darkTheme = look.dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    var open by remember { mutableStateOf(true) }
                    Box(Modifier.fillMaxSize()) {
                        RecipesScreen(
                            RecipesUiState(recipes = sample.recipes, total = sample.recipes.size, isLoading = false),
                            RecipesActions(),
                        )
                    }
                    if (open) {
                        RecipeActionsSheet(
                            recipe = sample.recipes[1],
                            isFavorite = false,
                            onCook = {},
                            onPlan = {},
                            onShop = {},
                            onCookbook = {},
                            onToggleFavorite = {},
                            onShare = {},
                            onDismiss = { open = false },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/sheet_closing_0_$look.png")
        composeRule.mainClock.autoAdvance = false
        // Sharing lets the sheet slide away first, as every action on it does.
        composeRule.onNodeWithWords(if (look.locale == "de") "Teilen" else "Share").performClick()
        var shown = 0L
        FRAMES.forEach { at ->
            composeRule.mainClock.advanceTimeBy(at - shown)
            shown = at
            captureScreenRoboImage("build/outputs/roborazzi/sheet_closing_${at}_$look.png")
        }
    }

    private companion object {
        /** When each frame is taken, in milliseconds after the tap. */
        val FRAMES = listOf(64L, 128L, 192L, 256L, 320L, 400L, 520L)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks() = listOf(Look("de", dark = false), Look("de", dark = true))
    }
}
