package app.aroma.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import app.aroma.ui.theme.Motion
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * A short line whose figures roll as they change, "5 von 7 geplant" to "6 von 7 geplant": what
 * stays stands still, and what changed drifts a little up as it counts up, or down as it counts
 * down, while the old figure fades and the new one comes in; what follows glides to its new place.
 * Words that change otherwise cross-fade where they changed. It keeps to one line, shortened with
 * "…" where it has to be, and reads out as the one line it is.
 */
@Composable
fun RollingText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified) {
    val roll = remember { Rolls(text) }.to(text)
    Layout(
        contents =
            listOf(
                { Part(roll.before, style, color) },
                { Rolling(roll, style, color) },
                { Part(roll.after, style, color) },
            ),
        modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
    ) { (before, middle, after), constraints ->
        // One after the other on one line: each part has the room the ones before it leave.
        val bounded = constraints.hasBoundedWidth

        fun room(taken: Int) = if (bounded) Constraints(maxWidth = (constraints.maxWidth - taken).coerceAtLeast(0)) else Constraints()
        val first = before.firstOrNull()?.measure(room(0))
        val start = first?.width ?: 0
        val second = middle.first().measure(room(start))
        val third = after.firstOrNull()?.measure(room(start + second.width))
        val width = (start + second.width + (third?.width ?: 0)).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = maxOf(first?.height ?: 0, second.height, third?.height ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            first?.placeRelative(0, 0)
            second.placeRelative(start, 0)
            third?.placeRelative(start + second.width, 0)
        }
    }
}

/**
 * The part that changed: the old one drifts away and fades within an edge's time, as the room it
 * stands in narrows or widens, and the new one comes in from the other side within a change of
 * state's. All of it moves in layout and drawing, so a roll composes nothing anew.
 */
@Composable
private fun Rolling(roll: Roll, style: TextStyle, color: Color) {
    val progress = remember(roll) { Animatable(if (roll.settled) 1f else 0f) }
    LaunchedEffect(roll) { progress.animateTo(1f, Motion.state()) }
    Layout(
        contents = listOf({ Part(roll.was, style, color) }, { Part(roll.now, style, color) }),
    ) { (was, now), constraints ->
        val old = was.firstOrNull()?.measure(constraints)
        val new = now.firstOrNull()?.measure(constraints)
        val way = roll.direction
        val gone = progress.value
        // The room changes as quickly as the old part fades, so the new one never crowds what follows.
        val width = lerp(old?.width ?: 0, new?.width ?: 0, (gone / OLD_FADES).coerceAtMost(1f))
        layout(width, maxOf(old?.height ?: 0, new?.height ?: 0)) {
            if (gone < 1f) {
                old?.placeRelativeWithLayer(0, 0) {
                    val shown = progress.value
                    alpha = (1f - shown / OLD_FADES).coerceAtLeast(0f)
                    translationY = -way * Motion.ROLL * size.height * shown
                }
            }
            new?.placeRelativeWithLayer(0, 0) {
                val shown = progress.value
                alpha = shown
                translationY = way * Motion.ROLL * size.height * (1f - shown)
            }
        }
    }
}

/** The old part is gone by the time an edge's fade would end: this share of the roll. */
private val OLD_FADES = Motion.EDGE.toFloat() / Motion.STATE

/** A part of the line; one with nothing in it is left out rather than set as an empty text. */
@Composable
private fun Part(text: String, style: TextStyle, color: Color) {
    if (text.isNotEmpty()) Text(text, style = style, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

/**
 * The last change of a [RollingText]: of [text], [before] and [after] stayed as they were, and
 * [now] took the place of [was]; [direction] is 1 where a figure counted up, -1 where it counted
 * down, and 0 for anything else. A [settled] one is the text as it first showed, with nothing to roll.
 */
private class Roll(
    val text: String,
    val before: String,
    val was: String,
    val now: String,
    val after: String,
    val direction: Int,
    val settled: Boolean = false,
)

/** Remembers the text a [RollingText] showed last, to tell what changed. */
private class Rolls(initial: String) {
    private var last = Roll(initial, before = "", was = "", now = initial, after = "", direction = 0, settled = true)

    fun to(text: String): Roll {
        if (text != last.text) last = change(last.text, text)
        return last
    }
}

/**
 * What changed from [from] to [to], in whole words and whole figures, so "Portion" never splits
 * from "Portionen" and "9" turns into "10" as one figure.
 */
private fun change(from: String, to: String): Roll {
    val old = Words.findAll(from).map { it.value }.toList()
    val new = Words.findAll(to).map { it.value }.toList()
    var start = 0
    while (start < old.size && start < new.size && old[start] == new[start]) start++
    var end = 0
    while (end < old.size - start && end < new.size - start && old[old.size - 1 - end] == new[new.size - 1 - end]) end++
    val was = old.subList(start, old.size - end).joinToString("")
    val now = new.subList(start, new.size - end).joinToString("")
    val a = was.asFigure()
    val b = now.asFigure()
    return Roll(
        text = to,
        before = new.subList(0, start).joinToString(""),
        was = was,
        now = now,
        after = new.subList(new.size - end, new.size).joinToString(""),
        direction = if (a != null && b != null) sign(b - a).roundToInt() else 0,
    )
}

/** A figure, with its decimals or thousands; a word; or any one other character. */
private val Words = Regex("""\d+(?:[.,]\d+)*|\p{L}+|.""", RegexOption.DOT_MATCHES_ALL)

private fun String.asFigure(): Double? = if (Figure.matches(this)) replace(',', '.').toDoubleOrNull() else null

private val Figure = Regex("""\d+(?:[.,]\d+)?""")
