package app.aroma.ui.components

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

/**
 * Text as Aroma sets it, so German compounds break as a typesetter would break them (see
 * [breakable]), and text that is shortened ends after a whole word with "…" (see [ellipsis]).
 * Screens use this instead of Material's `Text`, with the same parameters.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    Text(
        text = remember(text) { AnnotatedString(text) },
        modifier = modifier,
        color = color,
        style = style,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
    )
}

/** [Text] for styled text, and for text with [inlineContent] such as a glyph within a line. */
@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val prepared = remember(text) { text.breakable() }
    val shortens = overflow == TextOverflow.Ellipsis && maxLines != Int.MAX_VALUE
    // Where the text was cut after a whole word, and at what width; measured once it overflows.
    var cut by remember(prepared, maxLines, shortens) { mutableStateOf<Cut?>(null) }
    val shown = cut?.at?.let { prepared.ellipsis(it) } ?: prepared
    androidx.compose.material3.Text(
        text = shown,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        // Until it is measured, the text is clipped, so the cut is found in what fits.
        overflow = if (shortens && cut == null) TextOverflow.Clip else overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        inlineContent = inlineContent,
        onTextLayout = { result ->
            if (shortens) {
                val width = result.layoutInput.constraints.maxWidth
                val current = cut
                cut =
                    when {
                        current == null -> if (result.hasVisualOverflow) Cut(wordEnd(prepared.text, result), width) else null

                        // With more room than it was cut for, more of it may fit. With less, the cut
                        // stands while it fits: a menu as wide as its widest row narrows to the cut,
                        // and measuring the whole text again would widen it, back and forth for ever.
                        width > current.width -> null

                        // The "…" did not fit after the last word, or no longer fits: one word less.
                        current.at != null && result.hasVisualOverflow -> Cut(previousWordEnd(prepared.text, current.at), width)

                        else -> current
                    }
            }
            onTextLayout?.invoke(result)
        },
        style = style,
    )
}

/** A cut of a shortened text: before character [at], or nowhere where its last line holds one long word, measured at [width]. */
private data class Cut(val at: Int?, val width: Int)

/**
 * Where to end text that overflows [layout]: after the last whole word of its last line, or the
 * last whole part of a word joined by hyphens, without the space, comma or hyphen after it; null
 * where that line holds nothing to end at.
 */
private fun wordEnd(text: String, layout: TextLayoutResult): Int? {
    val line = layout.lineCount - 1
    val start = layout.getLineStart(line)
    val end = layout.getLineEnd(line, visibleEnd = true)
    // The line ends with a whole word where a break follows it; otherwise its last word is cut.
    val whole = end < text.length && (text[end].endsWord() || text[end - 1] == ZERO_WIDTH_SPACE)
    return if (whole) trimmed(text, end, start) else previousWordEnd(text, end, start)
}

/** The end of the word, or part of a word, before the one that ends at [end], not before [start]; null where there is none. */
private fun previousWordEnd(text: String, end: Int, start: Int = 0): Int? {
    val space = (end - 1 downTo start + 1).firstOrNull { text[it].endsWord() } ?: return null
    return trimmed(text, space, start)
}

/** Where a word ends: at a space, or after a hyphen, where [breakable] put a zero-width space. */
private fun Char.endsWord() = isWhitespace() || this == ZERO_WIDTH_SPACE

/** [end], moved back over spaces and over the comma or hyphen a "…" should not follow. */
private fun trimmed(text: String, end: Int, start: Int): Int? {
    var at = end
    while (at > start && (text[at - 1].endsWord() || text[at - 1] in TRAILING)) at--
    return at.takeIf { it > start }
}

/** What a shortened text drops before its "…": "Teig, …" reads as "Teig…". */
private const val TRAILING = ",;:–-·"

/** The text up to [end], and "…" after it, in the style of the last character kept. */
fun AnnotatedString.ellipsis(end: Int): AnnotatedString =
    buildAnnotatedString {
        append(this@ellipsis.subSequence(0, end))
        withStyle(this@ellipsis.spanStyleAt((end - 1).coerceAtLeast(0))) { append(ELLIPSIS) }
    }

private const val ELLIPSIS = '…'

/** A space of no width: a place a line may break that shows nothing. */
private const val ZERO_WIDTH_SPACE = '\u200B'

/** A joiner of no width: inside a word it shows nothing and keeps Android's hyphenator off the word. */
private const val WORD_JOINER = '\u2060'

private const val NON_BREAKING_HYPHEN = '‑'

/** A hyphen between two letters, as in "Zitronen-Baiser-Plätzchen", not yet followed by a zero-width space. */
private val JoiningHyphen = Regex("(?<=\\p{L})[-\u2010\u2011](?=\\p{L})")

/** The first letter of a word of four to seven letters, too short to hyphenate well, not yet joined. */
private val ShortWord = Regex("(?<![\\p{L}\u2060])\\p{L}(?=\\p{L}{3,6}(?!\\p{L}))")

/**
 * The text ready for Android's line breaker. Android never hyphenates a word that holds a hyphen,
 * so "Erdbeer-Streuselkuchenschnitten" would break anywhere in its second half, without a hyphen,
 * once it does not fit. A zero-width space after each hyphen between letters hands every part to
 * the hyphenator on its own, and a line still breaks only after a hyphen, never before one. Android
 * also leaves alone a word with a character it does not know: a word joiner after the first letter
 * of a word of four to seven letters keeps it whole, as "ei-nem", "un-ter" and "Sa-lat" read badly;
 * longer words still hyphenate. A non-breaking hyphen becomes an ordinary one. Running it again
 * changes nothing.
 */
fun String.breakable(): String = AnnotatedString(this).breakable().text

/** [String.breakable] for styled text: its styles, links and annotations stay whole, on their characters. */
fun AnnotatedString.breakable(): AnnotatedString {
    val source = text
    val inserts =
        (
            JoiningHyphen.findAll(source).map { it.range.first to ZERO_WIDTH_SPACE } +
                ShortWord.findAll(source).map { it.range.first to WORD_JOINER }
        ).groupBy({ it.first }, { it.second })
    if (inserts.isEmpty() && NON_BREAKING_HYPHEN !in source) return this
    // Where each character of the source lands in the new text, and where its end does.
    val moved = IntArray(source.length + 1)
    val built = StringBuilder(source.length + inserts.size)
    source.forEachIndexed { index, char ->
        moved[index] = built.length
        built.append(if (char == NON_BREAKING_HYPHEN) '-' else char)
        inserts[index]?.forEach { built.append(it) }
    }
    moved[source.length] = built.length
    val original = this
    return buildAnnotatedString {
        append(built.toString())
        original.spanStyles.forEach { addStyle(it.item, moved[it.start], moved[it.end]) }
        original.paragraphStyles.forEach { addStyle(it.item, moved[it.start], moved[it.end]) }
        original.getStringAnnotations(0, original.length).forEach { addStringAnnotation(it.tag, it.item, moved[it.start], moved[it.end]) }
        original.getLinkAnnotations(0, original.length).forEach { link ->
            when (val item = link.item) {
                is LinkAnnotation.Url -> addLink(item, moved[link.start], moved[link.end])
                is LinkAnnotation.Clickable -> addLink(item, moved[link.start], moved[link.end])
            }
        }
    }
}

/** The styles that apply at [index], merged in the order they were added. */
private fun AnnotatedString.spanStyleAt(index: Int): SpanStyle =
    spanStyles.filter { index >= it.start && index < it.end }.fold(SpanStyle()) { merged, range -> merged.merge(range.item) }
