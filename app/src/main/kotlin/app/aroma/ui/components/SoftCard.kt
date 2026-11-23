package app.aroma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.CardPadding
import app.aroma.ui.theme.ContentEdge
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget
import app.aroma.ui.theme.extras

/**
 * Where an item of a lazy list sits in a card that spans several items: a day of the plan is its
 * header and one item per meal. Only the first and last part round their corners and add air.
 */
enum class CardPart(internal val isTop: Boolean, internal val isBottom: Boolean) {
    Whole(true, true),
    Top(true, false),
    Middle(false, false),
    Bottom(false, true),
    ;

    companion object {
        /** The part of item [index] in a card of [count] items. */
        fun of(index: Int, count: Int): CardPart =
            when {
                count == 1 -> Whole
                index == 0 -> Top
                index == count - 1 -> Bottom
                else -> Middle
            }
    }
}

/**
 * A soft card: the one container of the app, for rows that belong together and for content that
 * stands on its own, such as an empty state. Its edges stand on the page margins; what it holds
 * keeps [app.aroma.ui.theme.CardPadding] from them, so text inside a card always starts on
 * [ContentEdge]. Rows that take a tap span the card's full width and pad themselves.
 */
@Composable
fun SoftCard(modifier: Modifier = Modifier, part: CardPart = CardPart.Whole, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .padding(horizontal = ScreenMargin)
            .fillMaxWidth()
            .clip(part.shape())
            .background(extras.card)
            .padding(top = if (part.isTop) Space.xxs else 0.dp, bottom = if (part.isBottom) Space.xxs else 0.dp),
        content = content,
    )
}

@Composable
@ReadOnlyComposable
private fun CardPart.shape(): Shape {
    val corners = MaterialTheme.shapes.medium
    return when (this) {
        CardPart.Whole -> corners
        CardPart.Top -> corners.copy(bottomStart = ZeroCorner, bottomEnd = ZeroCorner)
        CardPart.Bottom -> corners.copy(topStart = ZeroCorner, topEnd = ZeroCorner)
        CardPart.Middle -> RectangleShape
    }
}

private val ZeroCorner = CornerSize(0.dp)

/** The padding after an icon action at the end of a card's row, so its glyph ends on the card's content edge. */
val CardActionPadding: Dp = CardPadding - (TouchTarget - Glyph.m) / 2

/**
 * The name of a group, set in sage: "For the batter", "Produce", "Appearance". Above a card it stands
 * level with the text inside it; above chips or plain rows, pass the margin as [start].
 */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier, start: Dp = ContentEdge) {
    Text(
        text = text,
        style = AromaType.Label,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .padding(start = start, end = start, bottom = Space.xs)
                .semantics { heading() },
    )
}

/** A [GroupLabel] over its [SoftCard], the way settings, ingredients and the shopping list group rows. */
@Composable
fun CardGroup(label: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) GroupLabel(label)
        SoftCard(content = content)
    }
}
