package app.aroma.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.BoxPadding
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.LocalTonalFill
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Radius
import app.aroma.ui.theme.Space

/**
 * A box: a state, an action or figures, never content to read. A soft tonal fill with 20 dp
 * corners, no edge and no shadow, its content [BoxPadding] in. Controls inside it take the box's
 * own tonal fill, a step lighter, so they never sink into it. With [onClick], the whole box takes
 * the tap and gives way a little while pressed.
 */
@Composable
fun TonalBox(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(BoxPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AromaTheme.colors
    val fill = AromaTheme.tonalFill
    val interaction = remember { MutableInteractionSource() }
    val tap =
        if (onClick != null) {
            Modifier
                .pressScale(interaction)
                .clip(AromaTheme.shapes.box)
                .clickable(
                    interactionSource = interaction,
                    indication = PressTint,
                    onClickLabel = onClickLabel,
                    role = Role.Button,
                    onClick = onClick,
                )
        } else {
            Modifier.clip(AromaTheme.shapes.box)
        }
    Column(
        modifier
            .fillMaxWidth()
            .then(tap)
            .background(fill)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalTonalFill provides colors.surfaceInBox) { content() }
    }
}

/**
 * Where an item of a lazy list stands in a box that spans several items, as a group of the
 * shopping list does, one item per row: only the first and the last round their corners.
 */
enum class BoxPart(internal val isTop: Boolean, internal val isBottom: Boolean) {
    Whole(true, true),
    Top(true, false),
    Middle(false, false),
    Bottom(false, true),
    ;

    companion object {
        /** The part of item [index] in a box of [count] items. */
        fun of(index: Int, count: Int): BoxPart =
            when {
                count == 1 -> Whole
                index == 0 -> Top
                index == count - 1 -> Bottom
                else -> Middle
            }
    }
}

/**
 * One [part] of a box that spans several items of a lazy list: the box's fill and the part's
 * corners, nothing more; each row pads itself. What it holds takes the box's own tonal fill. As a
 * row next to it comes or goes, a part's corners ease into their new shape: they square at once to
 * meet a row that comes, and round only as a row that goes fades, so the box shows no notch where
 * the row still stands.
 */
@Composable
fun TonalBoxPart(part: BoxPart, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val top by animateDpAsState(if (part.isTop) Radius.box else 0.dp, cornerSpec(part.isTop), label = "topCorners")
    val bottom by animateDpAsState(if (part.isBottom) Radius.box else 0.dp, cornerSpec(part.isBottom), label = "bottomCorners")
    val shape =
        androidx.compose.foundation.shape
            .RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    val fill = AromaTheme.tonalFill
    val inner = AromaTheme.colors.surfaceInBox
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill),
    ) {
        CompositionLocalProvider(LocalTonalFill provides inner) { content() }
    }
}

/** Corners round as the row that leaves begins to fade, which a list does after an edge's time; they square at once. */
private fun cornerSpec(round: Boolean): FiniteAnimationSpec<Dp> =
    if (round) tween(Motion.STATE, delayMillis = Motion.EDGE, easing = Motion.Decelerate) else Motion.state()

/**
 * The head of a box: its title on the left, and on the right a [meta] line, a [chevron] where the
 * box leads somewhere, or both. A count in the meta line rolls as it changes ("5 von 7 geplant").
 */
@Composable
fun BoxHeader(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    chevron: Boolean = false,
    titleColor: androidx.compose.ui.graphics.Color = AromaTheme.colors.ink,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = BoxHeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = AromaTheme.type.label,
            color = titleColor,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { heading() },
        )
        if (meta != null) {
            Spacer(Modifier.width(Space.s12))
            RollingText(meta, style = AromaTheme.type.meta)
        }
        if (chevron) {
            Spacer(Modifier.width(Space.s4))
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = AromaTheme.colors.inkSecondary,
                modifier = Modifier.size(Glyph.control),
            )
        }
    }
}

/** A box's head is as tall as one line of meta beside its title, so boxes with and without a chevron keep one rhythm. */
private val BoxHeaderHeight = 24.dp

/**
 * The head of a group of rows, as settings and the shopping list group them: its name in the label
 * role and second ink, standing on the page's edge above the group's box.
 */
@Composable
fun GroupHead(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AromaTheme.type.label,
        color = AromaTheme.colors.inkSecondary,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * The one line Aroma draws: 1 px between the rows of a grouped list in a box, inset on the left to
 * the rows' text, [start] from the box's edge.
 */
@Composable
fun Separator(modifier: Modifier = Modifier, start: Dp = BoxPadding) {
    val color = AromaTheme.colors.separator
    Canvas(
        modifier
            .fillMaxWidth()
            .padding(start = start)
            .height(1.dp),
    ) {
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1f)
    }
}

/**
 * A 20 dp glyph on a circle of the box's own fill, as the shopping list's basket stands at the head
 * of its box: a sign of what the box is about, not a button.
 */
@Composable
fun IconCircleMark(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Box(
        modifier
            .size(IconCircle)
            .background(AromaTheme.tonalFill, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = AromaTheme.colors.ink, modifier = Modifier.size(Glyph.control))
    }
}
