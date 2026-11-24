package app.aroma.ui.components

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * [breakable]). Screens use this instead of Material's `Text`, with the same parameters.
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
    androidx.compose.material3.Text(
        text = remember(text) { text.breakable() },
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style,
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
    androidx.compose.material3.Text(
        text = remember(text) { text.breakable() },
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        inlineContent = inlineContent,
        onTextLayout = onTextLayout ?: {},
        style = style,
    )
}

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
