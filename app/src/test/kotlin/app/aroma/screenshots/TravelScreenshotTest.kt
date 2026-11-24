package app.aroma.screenshots

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.aroma.feature.recipe.RecipeActions
import app.aroma.feature.recipe.RecipeScreen
import app.aroma.feature.recipe.RecipeUiState
import app.aroma.ui.components.LocalScreenTransition
import app.aroma.ui.components.LocalSharedTransition
import app.aroma.ui.components.RecipeCard
import app.aroma.ui.navigation.Transitions
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.PageMargin
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A recipe opening from its card: frames of the page change, as the photo grows into the page's
 * photo and the name moves to the title, and of the way back. The images land in
 * build/outputs/roborazzi/kochbuch/uebergang_*.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "de-w411dp-h891dp-xxhdpi")
class TravelScreenshotTest : ScreenshotTest() {
    @Test
    fun cardToPage() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AromaTheme(darkTheme = false) {
                val nav = rememberNavController()
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedTransition provides this) {
                        NavHost(
                            nav,
                            startDestination = "cards",
                            enterTransition = Transitions.enter,
                            exitTransition = Transitions.exit,
                            popEnterTransition = Transitions.popEnter,
                            popExitTransition = Transitions.popExit,
                        ) {
                            composable("cards") {
                                CompositionLocalProvider(LocalScreenTransition provides this) {
                                    RecipeCard(
                                        recipe = KochbuchSamples.summaries[0],
                                        onClick = { nav.navigate("recipe") },
                                        modifier = Modifier.padding(start = PageMargin, top = 120.dp).width(CARD),
                                    )
                                }
                            }
                            composable("recipe") {
                                CompositionLocalProvider(LocalScreenTransition provides this) {
                                    RecipeScreen(
                                        RecipeUiState(recipe = KochbuchSamples.full, isLoading = false),
                                        RecipeActions(onBack = { nav.popBackStack() }),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(FRAME)
        composeRule.onNodeWithText("Zitronen", substring = true).performClick()
        listOf(0, 1, 2, 3, 4).forEach { step ->
            composeRule.mainClock.advanceTimeBy(if (step == 0) FRAME else (Motion.PAGE / 4).toLong())
            composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/kochbuch/uebergang_hin_$step.png")
        }
        composeRule.mainClock.advanceTimeBy(Motion.PAGE.toLong())
        composeRule.runOnIdle { }
    }

    private companion object {
        val CARD = 180.dp
        const val FRAME = 16L
    }
}
