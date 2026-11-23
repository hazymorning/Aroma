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
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val AromaShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )

/**
 * Colours Material's scheme has no role for. [raised] is the paper of things that float above the
 * page, [card] the soft paper under rows that belong together and [cardHighlight] its sage-washed
 * variant for today and for questions to the user, [hairlineStrong] the border of controls, [cta]
 * the one filled action per screen (a deep tone-on-tone green in dark mode instead of a bright
 * mint slab).
 */
@Immutable
data class AromaExtras(
    val raised: Color,
    val card: Color,
    val cardHighlight: Color,
    val hairlineStrong: Color,
    val cta: Color,
    val onCta: Color,
    val shadowInk: Color,
    val floatingEdge: Color,
)

private val LightExtras =
    AromaExtras(
        raised = Color(0xFFFFFFFF),
        card = Color(0xFFEFF0E9),
        cardHighlight = Color(0xFFE6EDE1),
        hairlineStrong = Color(0xFFCBCFC3),
        cta = Color(0xFF486447),
        onCta = Color(0xFFFFFFFF),
        shadowInk = Color(0xFF1B2A19),
        floatingEdge = Color(0xFF1A1C18).copy(alpha = 0.05f),
    )

private val DarkExtras =
    AromaExtras(
        raised = Color(0xFF22251F),
        card = Color(0xFF1B1D1A),
        cardHighlight = Color(0xFF1C251B),
        hairlineStrong = Color(0xFF3A3F37),
        cta = Color(0xFF3A5638),
        onCta = Color(0xFFE2F0DA),
        shadowInk = Color.Black,
        floatingEdge = Color.White.copy(alpha = 0.07f),
    )

/** With wallpaper colours, the extras follow the generated scheme instead of the sage defaults. */
private fun dynamicExtras(scheme: ColorScheme, dark: Boolean): AromaExtras =
    (if (dark) DarkExtras else LightExtras).copy(
        raised = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest,
        card = scheme.surfaceContainer,
        cardHighlight = scheme.primaryContainer.copy(alpha = 0.45f).compositeOver(scheme.surface),
        hairlineStrong = scheme.onSurface.copy(alpha = 0.18f).compositeOver(scheme.surface),
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
