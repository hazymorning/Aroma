package app.aroma.ui.theme

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
import app.aroma.R

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

// Titles balance their lines and never hyphenate, so compound words stay whole and a two-line
// title never ends on a single word. Running text uses the paragraph strategy.
private val balancedLines =
    LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

private fun serifDisplay(size: Int, lineHeight: Int, tracking: Double, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        fontFamily = SerifDisplay,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.em,
        lineHeightStyle = balancedLines,
        lineBreak = LineBreak.Heading,
        hyphens = Hyphens.None,
    )

private fun serifText(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Medium) =
    TextStyle(
        fontFamily = SerifText,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        lineHeightStyle = balancedLines,
        lineBreak = LineBreak.Heading,
        hyphens = Hyphens.None,
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

/**
 * Small tracked capitals where a magazine would put a label: section names, meal types, step
 * counters. Set in upper case by the Kicker composable; the only place Figtree SemiBold appears.
 */
val KickerStyle = sans(11, 16, FontWeight.SemiBold, letterSpacing = 1.2)

/** The serif lead under a title: recipe and cookbook descriptions, the login tagline. */
val Standfirst = serifText(18, 27, FontWeight.Normal).copy(lineBreak = LineBreak.Paragraph, hyphens = Hyphens.Auto)

/** Recipe steps: a touch larger than body text, for reading at arm's length. */
val Reading = sans(17, 27, FontWeight.Normal)

/** The step text in cook mode. */
val CookStep = serifText(26, 36, FontWeight.Normal).copy(lineBreak = LineBreak.Paragraph, hyphens = Hyphens.Auto)

/** A step's number in the gutter of a recipe, a quiet serif numeral level with the step's first line. */
val StepNumber = serifDisplay(24, 32, -0.01).tabular()

/** The time left on a cooking timer, readable from across the kitchen. */
val TimerDigits = serifDisplay(28, 32, -0.01).tabular()

/** Items in shopping mode, large enough to read with the phone held low over the trolley. */
val ShoppingModeItem = sans(22, 28, FontWeight.Normal)

/** Lining figures of equal width, so times, amounts and counters do not jitter. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum, lnum")

val AromaTypography =
    Typography(
        displayLarge = serifDisplay(56, 60, -0.03),
        displayMedium = serifDisplay(40, 46, -0.02),
        displaySmall = serifDisplay(36, 42, -0.02),
        headlineLarge = serifDisplay(32, 38, -0.015),
        headlineMedium = serifDisplay(26, 32, -0.01),
        headlineSmall = serifText(22, 28),
        titleLarge = serifText(20, 26),
        titleMedium = sans(16, 22, FontWeight.Medium),
        titleSmall = sans(14, 20, FontWeight.Medium),
        bodyLarge = sans(16, 24, FontWeight.Normal),
        bodyMedium = sans(14, 21, FontWeight.Normal),
        bodySmall = sans(13, 18, FontWeight.Normal, letterSpacing = 0.1),
        labelLarge = sans(15, 20, FontWeight.Medium, letterSpacing = 0.1),
        labelMedium = sans(13, 18, FontWeight.Medium, letterSpacing = 0.1),
        labelSmall = sans(12, 16, FontWeight.Medium, letterSpacing = 0.1),
    )
