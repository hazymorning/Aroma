package app.aroma.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Material's slots, for the components Aroma takes from Material: every slot is a role of
 * [AromaType]. Kept apart from Type.kt, so the roles never wait on this while they are created.
 */
val AromaTypography =
    Typography(
        displayLarge = AromaType.PageTitle,
        displayMedium = AromaType.PageTitle,
        displaySmall = AromaType.PageTitle,
        headlineLarge = AromaType.PageTitle,
        headlineMedium = AromaType.SectionTitle,
        headlineSmall = AromaType.SectionTitle,
        titleLarge = AromaType.Name,
        titleMedium = AromaType.BodyStrong,
        titleSmall = AromaType.Label,
        bodyLarge = AromaType.Body,
        bodyMedium = AromaType.Small,
        bodySmall = AromaType.Small,
        labelLarge = AromaType.Label,
        labelMedium = AromaType.Label,
        labelSmall = AromaType.Caption,
    )

val AromaShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/**
 * Colours Material's scheme has no role for. [card] is the soft fill of everything that holds or
 * takes a tap: cards, fields, chips and quiet buttons. [selected] is the sage wash of a chosen chip,
 * tab or day. [raised] is the paper of what floats above the page: the tab bar, sheets, menus.
 * [cta] is the one filled action of a screen, a deep tone-on-tone green in dark mode instead of a
 * bright mint slab.
 */
@Immutable
data class AromaExtras(
    val card: Color,
    val selected: Color,
    val onSelected: Color,
    val raised: Color,
    val cta: Color,
    val onCta: Color,
    val shadowInk: Color,
    val floatingEdge: Color,
)

private val LightExtras =
    AromaExtras(
        card = Color(0xFFEEEFE7),
        selected = Color(0xFFDCE7D4),
        onSelected = Color(0xFF1C3319),
        raised = Color(0xFFFFFFFF),
        cta = Color(0xFF486447),
        onCta = Color(0xFFFFFFFF),
        shadowInk = Color(0xFF1B2A19),
        floatingEdge = Color(0xFF1A1C18).copy(alpha = 0.05f),
    )

private val DarkExtras =
    AromaExtras(
        card = Color(0xFF1E211D),
        selected = Color(0xFF2E3D2B),
        onSelected = Color(0xFFDCEBD3),
        raised = Color(0xFF242722),
        cta = Color(0xFF3A5638),
        onCta = Color(0xFFE2F0DA),
        shadowInk = Color.Black,
        floatingEdge = Color.White.copy(alpha = 0.07f),
    )

/** With wallpaper colours, the extras follow the generated scheme instead of the sage defaults. */
private fun dynamicExtras(scheme: ColorScheme, dark: Boolean): AromaExtras =
    (if (dark) DarkExtras else LightExtras).copy(
        card = scheme.surfaceContainer,
        selected = scheme.secondaryContainer,
        onSelected = scheme.onSecondaryContainer,
        raised = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest,
        cta = if (dark) scheme.primaryContainer else scheme.primary,
        onCta = if (dark) scheme.onPrimaryContainer else scheme.onPrimary,
    )

val LocalAromaExtras = staticCompositionLocalOf { LightExtras }

/** Quiet presses: a faint ink wash instead of Material's tinted bloom. */
private val QuietRipple =
    RippleAlpha(draggedAlpha = 0.08f, focusedAlpha = 0.10f, hoveredAlpha = 0.04f, pressedAlpha = 0.08f)

@Composable
fun AromaTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme =
        when {
            dynamic -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                DarkColors
            }

            else -> {
                LightColors
            }
        }
    val extras =
        when {
            dynamic -> dynamicExtras(colorScheme, darkTheme)
            darkTheme -> DarkExtras
            else -> LightExtras
        }
    MaterialTheme(colorScheme = colorScheme, typography = AromaTypography, shapes = AromaShapes) {
        CompositionLocalProvider(
            LocalAromaExtras provides extras,
            LocalRippleConfiguration provides RippleConfiguration(color = colorScheme.onSurface, rippleAlpha = QuietRipple),
            content = content,
        )
    }
}

/** The colours beyond Material's scheme, see [AromaExtras]. */
val extras: AromaExtras
    @Composable
    @ReadOnlyComposable
    get() = LocalAromaExtras.current
