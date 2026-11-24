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

                        current.width != width -> null

                        // The "…" did not fit after the last word: one word less.
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
 * Where to end text that overflows [layout]: after the last whole word of its last line, without
 * the space or the comma after it; null where that line holds no space to end at.
 */
private fun wordEnd(text: String, layout: TextLayoutResult): Int? {
    val line = layout.lineCount - 1
    val start = layout.getLineStart(line)
    val end = layout.getLineEnd(line, visibleEnd = true)
    // The line ends with a whole word where a space follows it; otherwise its last word is cut.
    val whole = end < text.length && text[end].isWhitespace()
    return if (whole) trimmed(text, end, start) else previousWordEnd(text, end, start)
}

/** The end of the word before the one that ends at [end], not before [start]; null where there is none. */
private fun previousWordEnd(text: String, end: Int, start: Int = 0): Int? {
    val space = (end - 1 downTo start + 1).firstOrNull { text[it].isWhitespace() } ?: return null
    return trimmed(text, space, start)
}

/** [end], moved back over spaces and over the comma or dash a "…" should not follow. */
private fun trimmed(text: String, end: Int, start: Int): Int? {
    var at = end
    while (at > start && (text[at - 1].isWhitespace() || text[at - 1] in TRAILING)) at--
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

private const val NON_BREAKING_HYPHEN = '‑'

/** A hyphen between two letters, as in "Zitronen-Baiser-Plätzchen", not yet followed by a zero-width space. */
private val JoiningHyphen = Regex("(?<=\\p{L})[-\u2010\u2011](?=\\p{L})")

/**
 * The text ready for Android's line breaker. Android never hyphenates a word that holds a hyphen,
 * so "Erdbeer-Streuselkuchenschnitten" would break anywhere in its second half, without a hyphen,
 * once it does not fit. A zero-width space after each hyphen between letters hands every part to
 * the hyphenator on its own, and a line still breaks only after a hyphen, never before one. A
 * non-breaking hyphen becomes an ordinary one. Running it again changes nothing.
 */
fun String.breakable(): String {
    if (none { it == '-' || it == '\u2010' || it == NON_BREAKING_HYPHEN }) return this
    return replace(NON_BREAKING_HYPHEN, '-').replace(JoiningHyphen) { it.value + ZERO_WIDTH_SPACE }
}

/** [String.breakable] for styled text: its styles, links and annotations stay on their characters. */
fun AnnotatedString.breakable(): AnnotatedString {
    val cuts = JoiningHyphen.findAll(text).map { it.range.first }.toList()
    if (cuts.isEmpty()) return this
    val source = this
    return buildAnnotatedString {
        var start = 0
        cuts.forEach { hyphen ->
            if (source.text[hyphen] == NON_BREAKING_HYPHEN) {
                append(source.subSequence(start, hyphen))
                withStyle(source.spanStyleAt(hyphen)) { append('-') }
            } else {
                append(source.subSequence(start, hyphen + 1))
            }
            append(ZERO_WIDTH_SPACE)
            start = hyphen + 1
        }
        append(source.subSequence(start, source.length))
    }
}

/** The styles that apply at [index], merged in the order they were added. */
private fun AnnotatedString.spanStyleAt(index: Int): SpanStyle =
    spanStyles.filter { index >= it.start && index < it.end }.fold(SpanStyle()) { merged, range -> merged.merge(range.item) }
