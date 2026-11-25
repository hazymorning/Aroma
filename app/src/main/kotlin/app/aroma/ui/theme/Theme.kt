package app.aroma.ui.theme

import android.graphics.Color.TRANSPARENT
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.intl.LocaleList
import app.aroma.data.settings.AppSettings
import app.aroma.data.settings.ThemeMode
import app.aroma.ui.components.PressTint
import java.util.Locale

/** The shapes of the theme, from [Radius]. */
@Immutable
class AromaShapes {
    val box = RoundedCornerShape(Radius.box)
    val cardImage = RoundedCornerShape(Radius.cardImage)
    val thumbnail = RoundedCornerShape(Radius.thumbnail)
    val sheet = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet)
    val round = RoundedCornerShape(percent = 50)
}

/** Material's shape slots, for the parts Aroma takes from Material. */
private val MaterialShapes =
    Shapes(
        extraSmall = RoundedCornerShape(Space.s4),
        small = RoundedCornerShape(Radius.thumbnail),
        medium = RoundedCornerShape(Radius.cardImage),
        large = RoundedCornerShape(Radius.box),
        extraLarge = RoundedCornerShape(Radius.sheet),
    )

val LocalAromaColors = staticCompositionLocalOf { LightColors }
val LocalAromaTypography = staticCompositionLocalOf { AromaTypography(LightColors, LocaleList("de-DE")) }
val LocalAromaShapes = staticCompositionLocalOf { AromaShapes() }
val LocalAromaShadows = staticCompositionLocalOf { AromaShadows(dark = false) }

/**
 * The tonal fill of a control where it stands: [AromaColors.surface] on the page,
 * [AromaColors.surfaceInBox] inside a box, [AromaColors.surfaceOnBar] on a bar or a sheet. A box or
 * a sheet sets it for what it holds, so a button never sinks into the fill around it.
 */
val LocalTonalFill = staticCompositionLocalOf { Color.Unspecified }

/** Aroma's design system: its colours, type, shapes and shadows; spacing is [Space], the same everywhere. */
object AromaTheme {
    val colors: AromaColors
        @Composable @ReadOnlyComposable
        get() = LocalAromaColors.current

    val type: AromaTypography
        @Composable @ReadOnlyComposable
        get() = LocalAromaTypography.current

    val shapes: AromaShapes
        @Composable @ReadOnlyComposable
        get() = LocalAromaShapes.current

    val shadows: AromaShadows
        @Composable @ReadOnlyComposable
        get() = LocalAromaShadows.current

    /** The tonal fill for a control where it stands, see [LocalTonalFill]. */
    val tonalFill: Color
        @Composable @ReadOnlyComposable
        get() = LocalTonalFill.current.takeOrElse { colors.surface }
}

private inline fun Color.takeOrElse(block: () -> Color): Color = if (this != Color.Unspecified) this else block()

/**
 * The theme. With [dynamicColor], "Colours from your wallpaper", the wallpaper colours the sage
 * alone; paper and ink stay. Text breaks lines for the app's language.
 */
@Composable
fun AromaTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val base = if (darkTheme) DarkColors else LightColors
    val colors =
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            base.withWallpaperAccent(if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
        } else {
            base
        }
    val locale = LocalLocale.current.platformLocale
    val type = remember(colors, locale) { AromaTypography(colors, textLocale(locale)) }
    val shadows = remember(darkTheme) { AromaShadows(darkTheme) }
    MaterialTheme(
        colorScheme = colors.toMaterial(),
        motionScheme = AromaMotionScheme,
        shapes = MaterialShapes,
        typography = type.material,
    ) {
        CompositionLocalProvider(
            LocalAromaColors provides colors,
            LocalAromaTypography provides type,
            LocalAromaShadows provides shadows,
            LocalContentColor provides colors.ink,
            LocalTextStyle provides type.body,
            LocalIndication provides PressTint,
            // Material's parts press with the same quiet tint instead of a ripple.
            LocalRippleConfiguration provides null,
            content = content,
        )
    }
}

/**
 * [AromaTheme] for a whole activity, light or dark as [settings] ask, with the system bars drawn
 * over the page and their icons dark or light to match. A new look chosen while the app is open
 * dissolves into view (see [LookFade]).
 */
@Composable
fun ComponentActivity.AromaActivityTheme(settings: AppSettings, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dark =
        when (settings.themeMode) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
    val wanted = Look(dark, settings.dynamicColor)
    var look by remember { mutableStateOf(wanted) }
    val fade = remember { LookFade() }
    LaunchedEffect(wanted) {
        if (wanted == look) return@LaunchedEffect
        // The old look is pictured before the new one is drawn, and fades away over it.
        val covered = fade.cover(window)
        look = wanted
        if (covered) fade.dissolve()
    }
    val shownDark = look.dark
    DisposableEffect(shownDark) {
        val style = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT) { shownDark }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }
    AromaTheme(darkTheme = look.dark, dynamicColor = look.wallpaper) {
        Box(with(fade) { modifier.fillMaxSize().overlay() }) { content() }
    }
}

/** What the theme looks like: light or dark, and whether the wallpaper colours the sage. */
private data class Look(val dark: Boolean, val wallpaper: Boolean)

/** German text breaks by German rules wherever it is set; a language without a region takes its home region. */
private fun textLocale(locale: Locale): LocaleList =
    when {
        locale.country.isNotEmpty() -> LocaleList(locale.toLanguageTag())
        locale.language == "de" -> LocaleList("de-DE")
        locale.language == "en" -> LocaleList("en-US")
        else -> LocaleList(locale.toLanguageTag())
    }
