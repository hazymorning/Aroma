package app.aroma.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import app.aroma.core.format.DetectedDuration
import app.aroma.core.format.DurationDetector
import app.aroma.core.format.Markdown
import app.aroma.core.format.StepAmount
import app.aroma.core.format.StepMention
import app.aroma.core.format.StyledText
import app.aroma.ui.theme.AromaTheme

/**
 * Markdown text where durations such as "10 Minuten" become tappable timer links.
 * [onDuration] is null where timers make no sense, e.g. in descriptions.
 */
@Composable
fun rememberRichText(
    markdown: String,
    onDuration: ((DetectedDuration) -> Unit)? = null,
    linkColor: Color = AromaTheme.colors.accent,
): AnnotatedString = rememberStepText(remember(markdown) { Markdown.parse(markdown) }, emptyList(), onDuration, linkColor)

/**
 * A step's [text], as Markdown reads it, with [amounts] written in and set as the ingredient list sets amounts:
 * in sage, in its weight, with tabular figures and real fractions. Durations become timer links where
 * [onDuration] is set; an amount never does. Where [onIngredient] is set, a word that names an
 * ingredient ([mentions]) ticks it off with a tap, with the amount written in before it; a word of an
 * ingredient already ticked off ([checked]) fades like its line in the list.
 */
@Composable
fun rememberStepText(
    text: StyledText,
    amounts: List<StepAmount>,
    onDuration: ((DetectedDuration) -> Unit)? = null,
    linkColor: Color = AromaTheme.colors.accent,
    mentions: List<StepMention> = emptyList(),
    checked: Set<Int> = emptySet(),
    onIngredient: ((Int) -> Unit)? = null,
): AnnotatedString {
    val colors = AromaTheme.colors
    val linkStyles =
        TextLinkStyles(
            style = SpanStyle(color = linkColor, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline),
        )
    val amountStyle = SpanStyle(color = colors.accent, fontWeight = FontWeight.Medium)
    val ingredients =
        remember(mentions, checked, onIngredient, colors) {
            onIngredient?.let { toggle ->
                val pressed = SpanStyle(background = colors.ink.copy(alpha = PRESSED_TINT))
                IngredientTaps(
                    mentions = mentions,
                    checked = checked,
                    open = TextLinkStyles(pressedStyle = pressed),
                    done = TextLinkStyles(style = SpanStyle(color = colors.inkTertiary), pressedStyle = pressed),
                    onToggle = toggle,
                )
            }
        }
    return remember(text, amounts, onDuration, linkStyles, amountStyle, ingredients) {
        richText(text, amounts, onDuration, linkStyles, amountStyle, ingredients)
    }
}

/** The words of a step that tick off ingredients, and how they look open, ticked off and pressed. */
private class IngredientTaps(
    val mentions: List<StepMention>,
    val checked: Set<Int>,
    val open: TextLinkStyles,
    val done: TextLinkStyles,
    val onToggle: (Int) -> Unit,
)

/** Ink at 6 % under a word while it is pressed, as everything else that takes a tap is tinted. */
private const val PRESSED_TINT = 0.06f

private fun richText(
    styled: StyledText,
    amounts: List<StepAmount>,
    onDuration: ((DetectedDuration) -> Unit)?,
    linkStyles: TextLinkStyles,
    amountStyle: SpanStyle,
    ingredients: IngredientTaps?,
): AnnotatedString {
    val links = styled.spans.filterIsInstance<StyledText.Span.Link>()
    // No amount goes into the text of a link.
    val edits = amounts.filter { amount -> links.none { amount.start > it.start && amount.start < it.end } }.sortedBy { it.start }
    return buildAnnotatedString {
        // Each edit with where its amount starts and where its text ends in the new text.
        val moves = mutableListOf<Triple<StepAmount, Int, Int>>()
        var position = 0
        edits.forEach { edit ->
            append(styled.text.substring(position, edit.start))
            val start = length
            append(edit.before)
            withStyle(amountStyle) { append(measureText(edit.line)) }
            append(edit.then)
            moves += Triple(edit, start, length)
            position = edit.end
        }
        append(styled.text.substring(position))

        // Where an offset of the step's text lands once the amounts are in: a style starting at an amount starts
        // after it, one ending there ends before it.
        fun moved(offset: Int, end: Boolean): Int {
            var moved = offset
            for ((edit, start, after) in moves) {
                if (offset < edit.start) break
                if (offset == edit.start && (end || edit.end > edit.start)) return start
                if (offset < edit.end) return start
                moved = offset - edit.end + after
            }
            return moved
        }
        styled.spans.forEach { span ->
            val start = moved(span.start, end = false)
            val end = moved(span.end, end = true)
            when (span) {
                is StyledText.Span.Bold -> addStyle(SpanStyle(fontWeight = FontWeight.Medium), start, end)
                is StyledText.Span.Italic -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                is StyledText.Span.Link -> addLink(LinkAnnotation.Url(span.url, linkStyles), start, end)
            }
        }
        val linked = links.map { moved(it.start, end = false) until moved(it.end, end = true) }
        val timed = mutableListOf<IntRange>()
        if (onDuration != null) {
            val taken = linked + moves.map { (_, start, after) -> start until after }
            DurationDetector
                .detect(toAnnotatedString().text)
                .filter { duration -> taken.none { duration.start <= it.last && it.first < duration.end } }
                .forEach { duration ->
                    timed += duration.start until duration.end
                    addLink(
                        LinkAnnotation.Clickable(tag = TIMER_TAG, styles = linkStyles) { onDuration(duration) },
                        duration.start,
                        duration.end,
                    )
                }
        }
        ingredients?.mentions?.forEach { mention ->
            val end = moved(mention.end, end = true)
            // The amount written in just before the word is part of the tap: "75 g Couscous".
            val start =
                moves
                    .firstOrNull { (edit, _, after) -> edit.ingredient == mention.ingredient && after == moved(mention.start, end = false) }
                    ?.second ?: moved(mention.start, end = false)
            if (end > start && (linked + timed).none { start <= it.last && it.first < end }) {
                val styles = if (mention.ingredient in ingredients.checked) ingredients.done else ingredients.open
                addLink(
                    LinkAnnotation.Clickable(tag = INGREDIENT_TAG, styles = styles) { ingredients.onToggle(mention.ingredient) },
                    start,
                    end,
                )
            }
        }
    }
}

/** What a link in a step does: start a timer, or tick off an ingredient. */
const val TIMER_TAG = "timer"
const val INGREDIENT_TAG = "ingredient"
