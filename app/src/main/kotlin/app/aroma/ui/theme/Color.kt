package app.aroma.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Aroma's colours: a cookbook in two-colour print. Paper for the page and the tones on it, ink for
 * text, icons and the one main action, sage for amounts, links, progress and what is chosen. All
 * other colour comes from the photos; red speaks of errors and nothing else.
 */
@Immutable
data class AromaColors(
    /** The page, behind the status bar too. */
    val background: Color,
    /** Boxes and tonal fills on the page. */
    val surface: Color,
    /** Tonal fills inside a box. */
    val surfaceInBox: Color,
    /** A pressed fill, skeletons, open segments of progress, a switch that is off. */
    val surfacePressed: Color,
    /** What floats: the navigation bar, action bars, the collapsed head, sheets, dialogs, menus. */
    val bar: Color,
    /** Text, the primary button, icons in icon buttons. */
    val ink: Color,
    /** Second lines, meta, icons in rows. */
    val inkSecondary: Color,
    /** What is past or turned off, and nothing that matters. */
    val inkTertiary: Color,
    /** Amounts, text actions, italic accents, progress, the bookmark ribbon. */
    val accent: Color,
    /** What is chosen: the navigation's pill, the chosen segment. Ink on it, never second ink. */
    val accentSoft: Color,
    /** Text and icons on [ink] and on [accent]. */
    val onInk: Color,
    /** The one line there is: between the rows of a grouped list in a box, inset to their text. */
    val separator: Color,
    /** The colour of every shadow. */
    val shadow: Color,
    /** The shade at the top of a photo. */
    val scrim: Color,
    /** Errors, and removing something for good. */
    val error: Color,
    val isDark: Boolean,
) {
    /**
     * The tonal fill of a control on a bar or a sheet. In the light the bar is the page's paper, so
     * the fill is the page's; in the dark the bar is a step lighter, so the fill is the box's.
     */
    val surfaceOnBar: Color get() = if (isDark) surfaceInBox else surface
}

private val LightInk = Color(0xFF1F1D1A)
private val DarkInk = Color(0xFFEDE9E2)

val LightColors =
    AromaColors(
        background = Color(0xFFFBF9F7),
        surface = Color(0xFFF0EDE6),
        surfaceInBox = Color(0xFFFBF9F7),
        surfacePressed = Color(0xFFE6E1D8),
        bar = Color(0xFFFBF9F7),
        ink = LightInk,
        inkSecondary = Color(0xFF6B675F),
        // A shade darker than the brief's #8F8A81, so an open tick's ring and a switch turned off stand 3:1 (section 9).
        inkTertiary = Color(0xFF837E76),
        accent = Color(0xFF536258),
        accentSoft = Color(0xFFD6E1D7),
        onInk = Color(0xFFFBF9F7),
        separator = LightInk.copy(alpha = 0.08f),
        shadow = Color(0xFF3A2E22),
        scrim = Color(0xFF000000),
        error = Color(0xFFB3261E),
        isDark = false,
    )

val DarkColors =
    AromaColors(
        background = Color(0xFF171512),
        surface = Color(0xFF221F1B),
        surfaceInBox = Color(0xFF2C2925),
        surfacePressed = Color(0xFF36322D),
        bar = Color(0xFF221F1B),
        ink = DarkInk,
        inkSecondary = Color(0xFFA8A298),
        // A shade lighter than the brief's #77716A, so a switch turned off stands 3:1 on its track (section 9).
        inkTertiary = Color(0xFF837D76),
        accent = Color(0xFFBACBBE),
        accentSoft = Color(0xFF3A4A3F),
        onInk = Color(0xFF171512),
        separator = DarkInk.copy(alpha = 0.10f),
        shadow = Color(0xFF000000),
        scrim = Color(0xFF000000),
        error = Color(0xFFF2B8B5),
        isDark = true,
    )

/** With "Colours from your wallpaper", the wallpaper colours sage alone; paper and ink stay. */
fun AromaColors.withWallpaperAccent(scheme: ColorScheme): AromaColors =
    copy(accent = scheme.primary, accentSoft = scheme.secondaryContainer)

/**
 * Material's roles, mapped onto Aroma's, for the few parts Aroma takes from Material: so none of them
 * falls back on a mint container or a tonal tint.
 */
internal fun AromaColors.toMaterial(): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = accent,
        onPrimary = onInk,
        primaryContainer = accentSoft,
        onPrimaryContainer = ink,
        inversePrimary = accentSoft,
        secondary = inkSecondary,
        onSecondary = onInk,
        secondaryContainer = accentSoft,
        onSecondaryContainer = ink,
        tertiary = accent,
        onTertiary = onInk,
        tertiaryContainer = accentSoft,
        onTertiaryContainer = ink,
        background = background,
        onBackground = ink,
        surface = background,
        onSurface = ink,
        surfaceVariant = surface,
        onSurfaceVariant = inkSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = ink,
        inverseOnSurface = onInk,
        error = error,
        onError = onInk,
        errorContainer = surface,
        onErrorContainer = error,
        outline = inkTertiary,
        outlineVariant = separator,
        scrim = scrim,
        surfaceBright = background,
        surfaceDim = surfacePressed,
        surfaceContainerLowest = background,
        surfaceContainerLow = background,
        surfaceContainer = surface,
        surfaceContainerHigh = bar,
        surfaceContainerHighest = surfacePressed,
    )
}
