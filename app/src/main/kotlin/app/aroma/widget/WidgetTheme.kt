package app.aroma.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.aroma.AppContainer
import app.aroma.R
import app.aroma.data.settings.ThemeMode
import app.aroma.data.settings.inAppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** How the app looks, as far as the widgets follow it: light, dark or as the system, in Aroma's or the wallpaper's colours. */
internal data class WidgetLook(val mode: ThemeMode, val wallpaperColors: Boolean) {
    /** Day and night colours the launcher picks between, or one set when the app keeps to either. */
    fun colors(context: Context): ColorProviders =
        when (mode) {
            ThemeMode.System -> ColorProviders(light = scheme(context, dark = false), dark = scheme(context, dark = true))
            ThemeMode.Light -> ColorProviders(scheme(context, dark = false))
            ThemeMode.Dark -> ColorProviders(scheme(context, dark = true))
        }

    private fun scheme(context: Context, dark: Boolean): ColorScheme =
        if (wallpaperColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            wallpaperPalette(context, dark).scheme()
        } else {
            aromaPalette(context, dark).scheme()
        }
}

/** The look the widgets follow, as the app's settings change. */
internal val AppContainer.widgetLooks: Flow<WidgetLook>
    get() = settings.settings.map { WidgetLook(it.themeMode, it.dynamicColor) }.distinctUntilChanged()

/** Glance's theme in the colours of [look], with words in the language chosen in the app. */
@Composable
internal fun WidgetTheme(look: WidgetLook, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val localized = remember(context) { context.inAppLanguage() }
    val colors = remember(look, localized) { look.colors(localized) }
    CompositionLocalProvider(LocalContext provides localized) {
        GlanceTheme(colors = colors, content = content)
    }
}

/** The colours of a widget by the role they play. */
private class WidgetPalette(
    val page: Color,
    val card: Color,
    val ink: Color,
    val softInk: Color,
    val accent: Color,
    val selected: Color,
    val onSelected: Color,
    val button: Color,
    val onButton: Color,
    val ring: Color,
) {
    /** The roles in the slots of a Material scheme, which Glance's theme carries; [WidgetColors] reads them back. */
    fun scheme(): ColorScheme =
        lightColorScheme(
            primary = accent,
            primaryContainer = selected,
            onPrimaryContainer = onSelected,
            secondary = button,
            onSecondary = onButton,
            background = page,
            onBackground = ink,
            surface = page,
            onSurface = ink,
            surfaceVariant = card,
            onSurfaceVariant = softInk,
            outline = ring,
        )
}

/** Aroma's own colours, from res/values(-night)/widget_colors.xml. */
private fun aromaPalette(context: Context, dark: Boolean): WidgetPalette {
    val configuration =
        Configuration(context.resources.configuration).apply {
            val night = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        }
    val resources = context.createConfigurationContext(configuration)

    fun color(id: Int) = Color(resources.getColor(id))
    return WidgetPalette(
        page = color(R.color.widget_page),
        card = color(R.color.widget_card),
        ink = color(R.color.widget_ink),
        softInk = color(R.color.widget_soft_ink),
        accent = color(R.color.widget_accent),
        selected = color(R.color.widget_selected),
        onSelected = color(R.color.widget_on_selected),
        button = color(R.color.widget_button),
        onButton = color(R.color.widget_on_button),
        ring = color(R.color.widget_ring),
    )
}

/** The wallpaper's colours in the roles the app gives them on its screens. */
@RequiresApi(Build.VERSION_CODES.S)
private fun wallpaperPalette(context: Context, dark: Boolean): WidgetPalette {
    val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return WidgetPalette(
        page = scheme.background,
        card = scheme.surfaceContainer,
        ink = scheme.onSurface,
        softInk = scheme.onSurfaceVariant,
        accent = scheme.primary,
        selected = scheme.primaryContainer.copy(alpha = SELECTED_WASH).compositeOver(scheme.background),
        onSelected = scheme.onPrimaryContainer,
        button = if (dark) scheme.primaryContainer else scheme.primary,
        onButton = if (dark) scheme.onPrimaryContainer else scheme.onPrimary,
        ring = scheme.outline,
    )
}

/** How much of the wallpaper's container colour washes the selection, as on the app's highlighted cards. */
private const val SELECTED_WASH = 0.45f

/** The roles of [WidgetPalette], read back from the slots of Glance's theme. */
internal object WidgetColors {
    val page: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.background
    val card: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.surfaceVariant
    val ink: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.onSurface
    val softInk: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.onSurfaceVariant
    val accent: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.primary
    val selected: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.primaryContainer
    val onSelected: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.onPrimaryContainer
    val button: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.secondary
    val onButton: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.onSecondary
    val ring: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.outline
}

/** The system's sans-serif in a few fixed sizes, which stay as they are however large the widget grows. */
internal object WidgetType {
    val title: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(color = WidgetColors.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    val body: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(color = WidgetColors.ink, fontSize = 14.sp)
    val label: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(color = WidgetColors.softInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val caption: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(color = WidgetColors.softInk, fontSize = 12.sp)
}

/** The space between a widget's edge and its content. */
internal val WidgetPadding = 12.dp

/** The least size of anything that takes a tap. */
internal val TargetSize = 48.dp

/** The corners of photos inside a widget, as round as its soft fills (widget_card.xml). */
internal val InnerCorner = 12.dp
