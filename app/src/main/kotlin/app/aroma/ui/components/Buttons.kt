package app.aroma.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget

/** The height of a button, of a compact one inside a box, and of a tall one in cook mode, hit from across the counter. */
val ButtonHeight: Dp = 48.dp
val CompactButtonHeight: Dp = 36.dp
private val TallButtonHeight: Dp = 56.dp

/** How dim a control is while it cannot be used. */
private const val DISABLED = 0.38f

/**
 * The one main action of a screen: ink, with its label in paper and an optional 20 dp glyph before
 * it. [compact] is the one inside a box. While [loading], a fine ring takes the label's place, and
 * the button keeps its width.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
    tall: Boolean = false,
    @DrawableRes icon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
) {
    val colors = AromaTheme.colors
    val usable = enabled && !loading
    ButtonFrame(
        onClick = onClick,
        enabled = usable,
        fill = if (enabled || loading) colors.ink else colors.ink.copy(alpha = 0.12f),
        content = if (enabled || loading) colors.onInk else colors.ink.copy(alpha = DISABLED),
        height =
            if (compact) {
                CompactButtonHeight
            } else if (tall) {
                TallButtonHeight
            } else {
                ButtonHeight
            },
        onInk = true,
        modifier = modifier,
    ) { content ->
        Box(contentAlignment = Alignment.Center) {
            ButtonLabel(text, icon, content, Modifier.alpha(if (loading) 0f else 1f), trailingIcon = trailingIcon)
            if (loading) ButtonProgress(content)
        }
    }
}

/**
 * An action beside or below the main one: on the tonal fill where it stands, the page's or the
 * box's, in ink. [confirmed] turns its glyph into a check for a moment once the action went through.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
    confirmed: Boolean = false,
    @DrawableRes icon: Int? = null,
) {
    val colors = AromaTheme.colors
    val content = if (enabled) colors.ink else colors.ink.copy(alpha = DISABLED)
    ButtonFrame(
        onClick = onClick,
        enabled = enabled && !loading,
        fill = AromaTheme.tonalFill,
        content = content,
        height = if (compact) CompactButtonHeight else ButtonHeight,
        onInk = false,
        modifier = modifier,
    ) { tint ->
        Box(contentAlignment = Alignment.Center) {
            val shown = if (confirmed && icon != null) R.drawable.ic_check else icon
            Crossfade(shown, animationSpec = Motion.state(), modifier = Modifier.alpha(if (loading) 0f else 1f), label = "secondaryIcon") {
                ButtonLabel(text, it, if (confirmed) colors.accent else tint, textColor = tint)
            }
            if (loading) ButtonProgress(tint)
        }
    }
}

@Composable
private fun ButtonFrame(
    onClick: () -> Unit,
    enabled: Boolean,
    fill: Color,
    content: Color,
    height: Dp,
    onInk: Boolean,
    modifier: Modifier = Modifier,
    label: @Composable (Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    // A width given to the button reaches its pill, so it can span a column; its height does not,
    // so a compact pill keeps its 36 dp inside a 48 dp target.
    Box(
        modifier =
            modifier
                .heightIn(min = maxOf(TouchTarget, height))
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        propagateMinConstraints = true,
    ) {
        Row(
            modifier =
                Modifier
                    .wrapContentHeight()
                    .heightIn(min = height)
                    .clip(CircleShape)
                    .background(fill)
                    .indication(interaction, if (onInk) PressTintOnInk else PressTint)
                    .padding(horizontal = if (height == CompactButtonHeight) Space.s16 else Space.s24, vertical = Space.s8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) { label(content) }
    }
}

@Composable
private fun ButtonLabel(
    text: String,
    @DrawableRes icon: Int?,
    iconTint: Color,
    modifier: Modifier = Modifier,
    textColor: Color = iconTint,
    @DrawableRes trailingIcon: Int? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        icon?.let {
            Icon(painterResource(it), contentDescription = null, tint = iconTint, modifier = Modifier.size(Glyph.control))
            Spacer(Modifier.width(Space.s8))
        }
        Text(text, style = AromaTheme.type.label, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailingIcon?.let {
            Spacer(Modifier.width(Space.s8))
            Icon(painterResource(it), contentDescription = null, tint = iconTint, modifier = Modifier.size(Glyph.control))
        }
    }
}

/**
 * The ring that turns while a button's action runs; the only spinner Aroma shows, in a button or in
 * the progress that takes a button's place while its work runs.
 */
@Composable
fun ButtonProgress(color: Color, modifier: Modifier = Modifier) {
    CircularProgressIndicator(strokeWidth = 1.5.dp, color = color, modifier = modifier.size(Glyph.control))
}

/**
 * A text action in sage, with no fill: "Alle", "Einplanen", "Erneut versuchen". Its text, or the
 * [icon] before it, stands on the edge; the touch area grows away from it to 48 dp.
 */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val accent = AromaTheme.colors.accent
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .heightIn(min = TouchTarget)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .alpha(if (enabled) 1f else DISABLED),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .indication(interaction, PressTint)
                // The tint reaches a little past the words; the words stay on the edge.
                .padding(horizontal = Space.s4)
                .offset(x = -Space.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(painterResource(it), contentDescription = null, tint = accent, modifier = Modifier.size(Glyph.control))
                Spacer(Modifier.width(Space.s4))
            }
            Text(text, style = AromaTheme.type.label, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Where an icon button stands, which sets its circle's size and fill. */
enum class IconButtonPlace {
    /** On the page or in a box: a 40 dp circle on the tonal fill where it stands. */
    Content,

    /** In a bar: a 48 dp circle on the tonal fill of bars. */
    Bar,

    /** On a photo: a 40 dp circle of paper, so it reads on any picture. */
    Photo,

    /** In cook mode's bar: a 56 dp circle beside the tall button, hit from across the counter. */
    TallBar,
}

/**
 * An icon that stands alone and takes a tap: always on a circle, a 20 dp glyph in ink, a 48 dp
 * target. [dot] marks it, as a filter in use. [confirmed] turns the glyph into a check for a moment.
 */
@Composable
fun IconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    place: IconButtonPlace = IconButtonPlace.Content,
    enabled: Boolean = true,
    dot: Boolean = false,
    confirmed: Boolean = false,
) {
    val colors = AromaTheme.colors
    val fill =
        when (place) {
            IconButtonPlace.Content -> AromaTheme.tonalFill
            IconButtonPlace.Bar, IconButtonPlace.TallBar -> colors.surfaceOnBar
            IconButtonPlace.Photo -> colors.background.copy(alpha = ON_PHOTO)
        }
    val circle =
        when (place) {
            IconButtonPlace.Bar -> TouchTarget
            IconButtonPlace.TallBar -> TallButtonHeight
            else -> IconCircle
        }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .size(maxOf(TouchTarget, circle))
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(circle)
                .clip(CircleShape)
                .background(fill)
                .indication(interaction, PressTint),
            contentAlignment = Alignment.Center,
        ) {
            val glyph = if (confirmed) R.drawable.ic_check else icon
            Crossfade(glyph, animationSpec = Motion.state(), label = "iconButton") {
                Icon(
                    painterResource(it),
                    contentDescription = contentDescription,
                    tint = (if (confirmed) colors.accent else colors.ink).copy(alpha = if (enabled) 1f else DISABLED),
                    modifier = Modifier.size(Glyph.control),
                )
            }
        }
        if (dot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = Space.s4, end = Space.s4)
                    .size(DotSize)
                    .background(colors.accent, CircleShape),
            )
        }
    }
}

/** The visible circle of an icon button on the page and on a photo. */
val IconCircle: Dp = 40.dp

/** How far an icon button's 48 dp target reaches past its circle: rows end this much short of the margin, so the circle ends on it. */
val IconButtonReach: Dp = (TouchTarget - IconCircle) / 2

/** The mark on an icon button, such as a filter in use. */
private val DotSize = 8.dp

/** How much of the page's paper the circle of a button on a photo keeps. */
private const val ON_PHOTO = 0.9f
