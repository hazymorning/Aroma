package app.aroma.screenshots

import androidx.compose.runtime.Composable
import app.aroma.feature.recipe.RecipeActions
import app.aroma.feature.recipe.RecipeScreen
import app.aroma.feature.recipe.RecipeUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

/**
 * The screens of the design round with the brief's test texts (section 14), light and dark, at
 * 100, 130 and 200 % font size: full, bare and extreme data. Pages that scroll are also rendered
 * whole, in a window tall enough to hold them. The images land in build/outputs/roborazzi/kochbuch.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "de-w411dp-h891dp-xxhdpi")
class DesignScreenshotTest(private val look: Look) : ScreenshotTest() {
    data class Look(val dark: Boolean, val scale: Float) {
        val suffix = "${if (dark) "dunkel" else "hell"}_${(scale * PERCENT).roundToInt()}"

        override fun toString() = suffix
    }

    private fun shoot(name: String, height: Int = PHONE_HEIGHT, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("de-w411dp-h${height}dp-xxhdpi")
        RuntimeEnvironment.setFontScale(look.scale)
        snapshot("kochbuch/${name}_${look.suffix}", look.dark, content = content)
    }

    private fun recipe(state: RecipeUiState): @Composable () -> Unit = { RecipeScreen(state, RecipeActions()) }

    /** Everything a recipe can have, saved, with 30 ingredients in four groups. */
    @Test
    fun recipeFull() =
        shoot("rezept_voll", content = recipe(RecipeUiState(recipe = KochbuchSamples.full, isLoading = false, isFavorite = true)))

    @Test
    fun recipeFullWhole() =
        shoot(
            "rezept_voll_ganz",
            height = WHOLE_PAGE,
            content = recipe(RecipeUiState(recipe = KochbuchSamples.full, isLoading = false, isFavorite = true)),
        )

    /** Nothing but a name: no photo, times, headnote or rating. */
    @Test
    fun recipeBare() = shoot("rezept_leer", content = recipe(RecipeUiState(recipe = KochbuchSamples.bare, isLoading = false)))

    /** The longest title, a yield of "1 Blech" and times as free text. */
    @Test
    fun recipeExtreme() = shoot("rezept_extrem", content = recipe(RecipeUiState(recipe = KochbuchSamples.longTitle, isLoading = false)))

    @Test
    fun recipeExtremeWhole() =
        shoot(
            "rezept_extrem_ganz",
            height = WHOLE_PAGE,
            content = recipe(RecipeUiState(recipe = KochbuchSamples.longTitle, isLoading = false)),
        )

    @Test
    fun recipeLoading() = shoot("rezept_laden", content = recipe(RecipeUiState(isLoading = true)))

    companion object {
        private const val PERCENT = 100
        private const val PHONE_HEIGHT = 891
        private const val WHOLE_PAGE = 4200

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks() = listOf(false, true).flatMap { dark -> listOf(1f, 1.3f, 2f).map { Look(dark, it) } }
    }
}
