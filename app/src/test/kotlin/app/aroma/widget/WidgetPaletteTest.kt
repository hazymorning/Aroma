package app.aroma.widget

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.extras
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The widgets' colours are the app's: each role as Theme.kt sets it, light and dark, in Aroma's colours and the wallpaper's. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetPaletteTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every role a widget uses has the app's colour`() {
        val looks = listOf(false, true).flatMap { dark -> listOf(false, true).map { wallpaper -> dark to wallpaper } }
        val app = mutableMapOf<Pair<Boolean, Boolean>, WidgetPalette>()
        compose.setContent {
            looks.forEach { (dark, wallpaper) ->
                AromaTheme(darkTheme = dark, dynamicColor = wallpaper) {
                    val scheme = MaterialTheme.colorScheme
                    app[dark to wallpaper] =
                        WidgetPalette(
                            background = scheme.background,
                            card = extras.card,
                            onSurface = scheme.onSurface,
                            onSurfaceVariant = scheme.onSurfaceVariant,
                            primary = scheme.primary,
                            onPrimary = scheme.onPrimary,
                            selected = extras.selected,
                            onSelected = extras.onSelected,
                            cta = extras.cta,
                            onCta = extras.onCta,
                            outline = scheme.outline,
                        )
                }
            }
        }
        compose.waitForIdle()

        looks.forEach { (dark, wallpaper) ->
            assertEquals("dark: $dark, wallpaper: $wallpaper", app.getValue(dark to wallpaper), widgetPalette(context, dark, wallpaper))
        }
    }
}
