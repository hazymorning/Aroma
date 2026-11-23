package app.aroma.ui.theme

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
private val SerifDisplay = FontFamily(Font(R.font.newsreader_display_regular, FontWeight.Normal))

/** Newsreader cut for text sizes (optical size 18). */
private val SerifText =
    FontFamily(
        Font(R.font.newsreader_text_regular, FontWeight.Normal),
        Font(R.font.newsreader_text_medium, FontWeight.Medium),
    )

private val Sans =
    FontFamily(
        Font(R.font.figtree_regular, FontWeight.Normal),
        Font(R.font.figtree_medium, FontWeight.Medium),
    )

// Lines sit centred in their line height, so text and the boxes around it share one rhythm.
private val centred = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** Titles balance their lines and never hyphenate, so a two-line title never ends on a lone word. */
private fun title(
    family: FontFamily,
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
    lineHeightStyle = centred,
    lineBreak = LineBreak.Heading,
    hyphens = Hyphens.None,
)

/** Running text breaks like a paragraph and hyphenates long words, which German has many of. */
private fun text(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        fontFamily = Sans,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        lineHeightStyle = centred,
        lineBreak = LineBreak.Paragraph,
        hyphens = Hyphens.Auto,
    )

/**
 * Aroma's type scale: seven sizes (12, 14, 16, 18, 22, 24 and 28 sp) in two families. Newsreader
 * names things: pages, sections and recipes. Figtree carries everything that is read or tapped,
 * the step being cooked included. Text is never set in capitals and never tracked out.
 */
object AromaType {
    /** The title of a page, and a recipe's name on its own page. */
    val PageTitle = title(SerifDisplay, 28, 34, tracking = -0.01)

    /** The title of a section, a day of the plan, a sheet or a dialog. */
    val SectionTitle = title(SerifText, 22, 28, FontWeight.Medium)

    /** A recipe's name on a card or in a row. */
    val Name = title(SerifText, 18, 24, FontWeight.Medium)

    /** The step on screen in cook mode, read at arm's length; words stay whole at this size. */
    val Reading = text(24, 34).copy(hyphens = Hyphens.None)

    /** The time left on a cooking timer, read across the kitchen. */
    val Timer = text(28, 34, FontWeight.Medium).tabular()

    /** Items in shopping mode, read with the phone held low over the trolley. */
    val Large = text(22, 28).copy(hyphens = Hyphens.None)

    /** Running text: ingredients, steps, descriptions, rows. */
    val Body = text(16, 24)

    /** Body text that leads a row or holds a value: an amount, a time, a setting's name. */
    val BodyStrong = text(16, 24, FontWeight.Medium)

    /** Second lines, notes and meta such as "25 min · 4.5". */
    val Small = text(14, 20)

    /** Buttons, chips, tabs and the labels that name a group of rows. */
    val Label = text(14, 20, FontWeight.Medium)

    /** The tab bar's labels and small marks such as "Today". */
    val Caption = text(12, 16, FontWeight.Medium)
}

/** Lining figures of equal width, so times, amounts and counters do not jitter. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum, lnum")
