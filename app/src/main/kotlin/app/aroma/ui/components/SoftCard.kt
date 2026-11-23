package app.aroma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.aroma.ui.theme.CardInset
import app.aroma.ui.theme.Space
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
 * Soft paper under rows that belong together: a day of the plan, a group of settings, a section of
 * the shopping list. It takes the page's full width but draws itself [CardInset] inside each edge,
 * so it stands half a margin outside the text column and the rows in it lay out exactly as on the
 * page. [highlight] washes it in sage, for today and for questions to the user.
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    part: CardPart = CardPart.Whole,
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = CardShape(part)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (highlight) extras.cardHighlight else extras.card)
            .padding(top = if (part.isTop) Space.xs else 0.dp, bottom = if (part.isBottom) Space.xs else 0.dp),
        content = content,
    )
}

/** A rectangle [CardInset] inside the left and right edges, rounded where [part] begins or ends the card. */
private class CardShape(private val part: CardPart) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val inset = with(density) { CardInset.toPx() }
        val radius = with(density) { CornerRadius(16.dp.toPx()) }
        val top = if (part.isTop) radius else CornerRadius.Zero
        val bottom = if (part.isBottom) radius else CornerRadius.Zero
        return Outline.Rounded(RoundRect(inset, 0f, size.width - inset, size.height, top, top, bottom, bottom))
    }

    override fun equals(other: Any?) = other is CardShape && other.part == part

    override fun hashCode() = part.hashCode()
}
