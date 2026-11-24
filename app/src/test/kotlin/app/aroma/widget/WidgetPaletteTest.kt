package app.aroma.widget

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.aroma.ui.theme.AromaTheme
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
                    val colors = AromaTheme.colors
                    app[dark to wallpaper] =
                        WidgetPalette(
                            background = colors.background,
                            card = colors.surface,
                            onSurface = colors.ink,
                            onSurfaceVariant = colors.inkSecondary,
                            primary = colors.accent,
                            onPrimary = colors.onInk,
                            selected = colors.accentSoft,
                            onSelected = colors.ink,
                            cta = colors.ink,
                            onCta = colors.onInk,
                            outline = colors.inkTertiary,
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
