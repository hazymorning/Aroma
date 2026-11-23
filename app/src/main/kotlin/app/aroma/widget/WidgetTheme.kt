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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontFamily
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

    private fun scheme(context: Context, dark: Boolean): ColorScheme = widgetPalette(context, dark, wallpaperColors).scheme()
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

/** The app's colour roles (Theme.kt) that the widgets use, under the same names. */
internal data class WidgetPalette(
    val background: Color,
    val card: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val selected: Color,
    val onSelected: Color,
    val cta: Color,
    val onCta: Color,
    val outline: Color,
) {
    /** The roles in the slots of a Material scheme, which Glance's theme carries; [WidgetColors] reads them back. */
    fun scheme(): ColorScheme =
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = selected,
            onPrimaryContainer = onSelected,
            secondary = cta,
            onSecondary = onCta,
            background = background,
            onBackground = onSurface,
            surface = background,
            onSurface = onSurface,
            surfaceVariant = card,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
        )
}

/** The widgets' colours in light or [dark]: Aroma's own, or with [wallpaperColors] the wallpaper's where Android has them. */
internal fun widgetPalette(context: Context, dark: Boolean, wallpaperColors: Boolean): WidgetPalette =
    if (wallpaperColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) wallpaperPalette(context, dark) else aromaPalette(context, dark)

/** Aroma's own colours, from res/values(-night)/widget_colors.xml, which the widgets' XML layouts share. */
private fun aromaPalette(context: Context, dark: Boolean): WidgetPalette {
    val configuration =
        Configuration(context.resources.configuration).apply {
            val night = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        }
    val resources = context.createConfigurationContext(configuration)

    fun color(id: Int) = Color(resources.getColor(id))
    return WidgetPalette(
        background = color(R.color.widget_background),
        card = color(R.color.widget_card),
        onSurface = color(R.color.widget_on_surface),
        onSurfaceVariant = color(R.color.widget_on_surface_variant),
        primary = color(R.color.widget_primary),
        onPrimary = color(R.color.widget_on_primary),
        selected = color(R.color.widget_selected),
        onSelected = color(R.color.widget_on_selected),
        cta = color(R.color.widget_cta),
        onCta = color(R.color.widget_on_cta),
        outline = color(R.color.widget_outline),
    )
}

/** The wallpaper's colours in the roles Theme.kt gives them on the app's screens. */
@RequiresApi(Build.VERSION_CODES.S)
private fun wallpaperPalette(context: Context, dark: Boolean): WidgetPalette {
    val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return WidgetPalette(
        background = scheme.background,
        card = scheme.surfaceContainer,
        onSurface = scheme.onSurface,
        onSurfaceVariant = scheme.onSurfaceVariant,
        primary = scheme.primary,
        onPrimary = scheme.onPrimary,
        selected = scheme.secondaryContainer,
        onSelected = scheme.onSecondaryContainer,
        cta = if (dark) scheme.primaryContainer else scheme.primary,
        onCta = if (dark) scheme.onPrimaryContainer else scheme.onPrimary,
        outline = scheme.outline,
    )
}

/** The roles of [WidgetPalette], read back from the slots of Glance's theme. */
internal object WidgetColors {
    val background: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.background
    val card: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.surfaceVariant
    val onSurface: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.onSurface
    val onSurfaceVariant: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.onSurfaceVariant
    val primary: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.primary
    val onPrimary: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.onPrimary
    val selected: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.primaryContainer
    val onSelected: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.onPrimaryContainer
    val cta: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.secondary
    val onCta: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.onSecondary
    val outline: ColorProvider
        @Composable @ReadOnlyComposable
        get() = GlanceTheme.colors.outline
}

/**
 * The app's type roles (Type.kt) on the app's scale. Launchers set a widget's words in the system's
 * fonts only: its serif stands in for Newsreader, a size smaller as it runs larger, and its
 * sans-serif for Figtree. Items take the scale's next size down, so they keep to one line in a
 * widget two cells wide, as they do on the app's page.
 */
internal object WidgetType {
    /** The list or the plan a widget shows, as the app sets a list's name. */
    val title: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(color = WidgetColors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)

    /** A recipe's name, and what a message is about. */
    val name: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(color = WidgetColors.onSurface, fontSize = 16.sp, fontFamily = FontFamily.Serif)

    /** A count read at a glance, in the size of the app's large type for shopping. */
    val large: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(color = WidgetColors.onSurface, fontSize = 22.sp)

    /** Rows: an item to buy, a line of a message. */
    val body: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(color = WidgetColors.onSurface, fontSize = 14.sp)

    /** Second lines and meta: the kind of meal, how many items are left. */
    val small: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 14.sp)

    /** Buttons and the labels that name a group: a day, an aisle. */
    val label: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(color = WidgetColors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
}

/** The space between a widget's edge and its content: the app's margin, and Android's for widgets. */
internal val WidgetPadding = 16.dp

/** The least size of anything that takes a tap. */
internal val TargetSize = 48.dp

/** The corners of photos and soft fills inside a widget, as on the app's cards. */
internal val CardCorner = 16.dp

/** The corners of a thumbnail beside a row's words. */
internal val ThumbnailCorner = 12.dp
