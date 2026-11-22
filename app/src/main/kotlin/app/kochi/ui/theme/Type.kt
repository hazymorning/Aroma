package app.kochi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kochi.R

/** Newsreader cut for large sizes (optical size 36). */
val SerifDisplay =
    FontFamily(
        Font(R.font.newsreader_display_regular, FontWeight.Normal),
        Font(R.font.newsreader_display_medium, FontWeight.Medium),
    )

/** Newsreader cut for running titles (optical size 18). */
val SerifText =
    FontFamily(
        Font(R.font.newsreader_text_regular, FontWeight.Normal),
        Font(R.font.newsreader_text_medium, FontWeight.Medium),
    )

val Sans =
    FontFamily(
        Font(R.font.figtree_regular, FontWeight.Normal),
        Font(R.font.figtree_medium, FontWeight.Medium),
        Font(R.font.figtree_semibold, FontWeight.SemiBold),
    )

// Titles break greedily: that keeps compound words whole and still breaks after a hyphen
// ("Gnocchi-Lauch-" / "Auflauf"). Running text uses the paragraph strategy.
private val balancedLines =
    LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

private fun serifDisplay(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Medium) =
    TextStyle(
        fontFamily = SerifDisplay,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = (-0.01).em,
        lineHeightStyle = balancedLines,
        lineBreak = LineBreak.Simple,
        hyphens = Hyphens.Auto,
    )

private fun serifText(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Medium) =
    TextStyle(
        fontFamily = SerifText,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        lineHeightStyle = balancedLines,
        lineBreak = LineBreak.Simple,
        hyphens = Hyphens.Auto,
    )

private fun sans(size: Int, lineHeight: Int, weight: FontWeight, letterSpacing: Double = 0.0) =
    TextStyle(
        fontFamily = Sans,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = letterSpacing.sp,
        lineHeightStyle = balancedLines,
        lineBreak = LineBreak.Paragraph,
        hyphens = Hyphens.Auto,
    )

/** Recipe titles on cards, a step below [Typography.titleLarge]. */
val CardTitle = serifText(18, 23)

val KochiTypography =
    Typography(
        displayLarge = serifDisplay(48, 54),
        displayMedium = serifDisplay(40, 46),
        displaySmall = serifDisplay(34, 40),
        headlineLarge = serifDisplay(30, 36),
        headlineMedium = serifDisplay(26, 32),
        headlineSmall = serifText(22, 28),
        titleLarge = serifText(20, 26),
        titleMedium = sans(16, 22, FontWeight.SemiBold),
        titleSmall = sans(14, 20, FontWeight.SemiBold),
        bodyLarge = sans(16, 24, FontWeight.Normal),
        bodyMedium = sans(14, 20, FontWeight.Normal),
        bodySmall = sans(12, 16, FontWeight.Normal, letterSpacing = 0.1),
        labelLarge = sans(14, 20, FontWeight.SemiBold, letterSpacing = 0.1),
        labelMedium = sans(12, 16, FontWeight.SemiBold, letterSpacing = 0.2),
        labelSmall = sans(11, 16, FontWeight.Medium, letterSpacing = 0.3),
    )
