package app.aroma.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.aroma.R

/** Newsreader cut for large sizes: titles and large figures (optical size 34). */
private val SerifDisplay = FontFamily(Font(R.font.newsreader_display, FontWeight.Normal))

/** Newsreader cut for section titles and the collapsed head (optical size 22). */
private val SerifSubhead = FontFamily(Font(R.font.newsreader_subhead, FontWeight.Normal))

/** Newsreader cut for names in cards and rows, and its italic for headnotes and groups (optical size 17). */
private val SerifText =
    FontFamily(
        Font(R.font.newsreader_text, FontWeight.Normal),
        Font(R.font.newsreader_text_italic, FontWeight.Normal, FontStyle.Italic),
    )

/** Instrument Sans: everything that is read to use the app, in two weights. */
private val Sans =
    FontFamily(
        Font(R.font.instrument_sans_regular, FontWeight.Normal),
        Font(R.font.instrument_sans_medium, FontWeight.Medium),
    )

/** Lines sit centred in their line height, so text and the boxes around it share one rhythm. */
private val Centred = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** Tabular figures, so amounts, times and counts do not jitter and stand in columns. */
const val TABULAR = "tnum"

/**
 * Aroma's type roles. The serif names things: pages, recipes, sections, large figures and the
 * italic accents. Everything that serves using the app is set in the sans. Nothing is set in
 * capitals or tracked out. Every role breaks lines for the reader's language: titles balance their
 * lines, running text breaks as a paragraph, and both hyphenate.
 */
@Immutable
class AromaTypography(colors: AromaColors, private val locale: LocaleList) {
    private fun title(
        family: FontFamily,
        size: Int,
        lineHeight: Int,
        color: Color = Color.Unspecified,
        tracking: Double = 0.0,
        italic: Boolean = false,
    ) = TextStyle(
        color = color,
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.em,
        lineHeightStyle = Centred,
        lineBreak = LineBreak.Heading,
        hyphens = Hyphens.Auto,
        localeList = locale,
    )

    private fun text(
        size: Int,
        lineHeight: Int,
        color: Color = Color.Unspecified,
        weight: FontWeight = FontWeight.Normal,
        tabular: Boolean = false,
    ) = TextStyle(
        color = color,
        fontFamily = Sans,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        lineHeightStyle = Centred,
        lineBreak = LineBreak.Paragraph,
        hyphens = Hyphens.Auto,
        localeList = locale,
        fontFeatureSettings = if (tabular) TABULAR else null,
    )

    /** Page titles. */
    val display = title(SerifDisplay, 34, 40, tracking = -0.005)

    /** A recipe's title on its own page. */
    val recipeTitle = title(SerifDisplay, 30, 36, tracking = -0.005)

    /** Sections of content ("Zutaten", "Ideen für heute"), and the recipe in the Today box. */
    val section = title(SerifSubhead, 22, 28)

    /** Recipe names in cards, rows and the plan. */
    val cardTitle = title(SerifText, 17, 22)

    /** A recipe's headnote. */
    val headnote = title(SerifText, 17, 26, italic = true).copy(lineBreak = LineBreak.Paragraph)

    /** A group of ingredients, "Mürbeteig". */
    val groupLabel = title(SerifText, 17, 24, color = colors.accent, italic = true)

    /** The number of the step in cook mode. */
    val numeralLarge = title(SerifDisplay, 64, 64).copy(fontFeatureSettings = TABULAR)

    /** A day's number in the plan, a step's number on the recipe page. */
    val numeral = title(SerifDisplay, 28, 32).copy(fontFeatureSettings = TABULAR)

    /** The title of the collapsed head. */
    val barTitle = title(SerifSubhead, 20, 24)

    /** The line under a page's or a recipe's title. */
    val subtitle = text(15, 20, colors.inkSecondary)

    /** Running text: ingredients, steps, settings. */
    val body = text(16, 24)

    /** Descriptions and explanations. */
    val bodySmall = text(14, 20, colors.inkSecondary)

    /** The name in the profile. */
    val labelLarge = text(17, 22, weight = FontWeight.Medium)

    /** Buttons, the titles of boxes, the heads of groups. */
    val label = text(15, 20, weight = FontWeight.Medium)

    /** Times, counts, sources. */
    val meta = text(13, 18, colors.inkSecondary, tabular = true)

    /** The chosen destination of the navigation bar. */
    val navSelected = text(12, 16, weight = FontWeight.Medium)

    /** The other destinations of the navigation bar. */
    val nav = text(12, 16)

    /** The step on screen in cook mode, read at arm's length. */
    val cookStep = text(24, 34)

    /** The initial in the profile's 56 dp circle in the settings. */
    val initial = text(22, 28, weight = FontWeight.Medium)

    /**
     * Material's slots, for the parts Aroma takes from Material: each is one of the roles above,
     * without its colour. Material sets its words in the colour of the ground it puts them on (the
     * snackbar's paper on ink); a role's own second ink would win over it and fade them.
     */
    internal val material =
        Typography(
            displayLarge = display.uncoloured(),
            displayMedium = display.uncoloured(),
            displaySmall = recipeTitle.uncoloured(),
            headlineLarge = recipeTitle.uncoloured(),
            headlineMedium = section.uncoloured(),
            headlineSmall = section.uncoloured(),
            titleLarge = barTitle.uncoloured(),
            titleMedium = label.uncoloured(),
            titleSmall = label.uncoloured(),
            bodyLarge = body.uncoloured(),
            bodyMedium = bodySmall.uncoloured(),
            bodySmall = meta.uncoloured(),
            labelLarge = label.uncoloured(),
            labelMedium = label.uncoloured(),
            labelSmall = meta.uncoloured(),
        )

    private fun TextStyle.uncoloured() = copy(color = Color.Unspecified)
}

/** Tabular figures for a role that does not have them: amounts, counts and clocks. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR)
