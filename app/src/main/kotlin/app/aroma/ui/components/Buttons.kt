package app.aroma.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Lines
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget
import app.aroma.ui.theme.extras
import app.aroma.ui.theme.isDarkScheme
import app.aroma.ui.theme.photoButtonShadow
import app.aroma.ui.theme.tabular

/** The one filled action of a screen. While [loading], [InlineProgress] takes the label's place. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
) {
    Button(
        onClick = { if (!loading) onClick() },
        enabled = enabled || loading,
        shape = CircleShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = extras.cta,
                contentColor = extras.onCta,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 28.dp),
        modifier = modifier.height(PrimaryButtonHeight),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // The label keeps its place while loading, so the button never changes width.
            Row(Modifier.alpha(if (loading) 0f else 1f), verticalAlignment = Alignment.CenterVertically) {
                leadingIcon?.let {
                    Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(Glyph.m))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                trailingIcon?.let {
                    Spacer(Modifier.width(8.dp))
                    Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(Glyph.m))
                }
            }
            if (loading) InlineProgress(color = extras.onCta)
        }
    }
}

/** The height of [PrimaryButton], which a round button beside it takes as its size. */
val PrimaryButtonHeight: Dp = 52.dp

/**
 * A quiet action: a hairline stadium with an ink icon and label. [confirmed] turns the icon into
 * a check for a moment once the action went through.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    confirmed: Boolean = false,
    compact: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (enabled) extras.hairlineStrong else MaterialTheme.colorScheme.outlineVariant),
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            ),
        contentPadding = PaddingValues(horizontal = if (compact) 14.dp else 16.dp),
        modifier = modifier.height(if (compact) 40.dp else 48.dp),
    ) {
        if (icon != null) {
            Crossfade(confirmed, animationSpec = tween(Motion.STATE), label = "secondaryIcon") { done ->
                Icon(
                    painter = painterResource(if (done) R.drawable.ic_check else icon),
                    contentDescription = null,
                    tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(if (compact) Glyph.s else Glyph.m),
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge.tabular(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** How a [QuietLink] reads: [Soft] for navigation, [Ink] for actions in place, [Destructive] for removal. */
enum class LinkTone { Soft, Ink, Destructive }

/**
 * A text link with no container: the text, or the [leadingIcon] before it, sits exactly on the
 * keyline and the touch area grows away from it. Pressing dims it instead of blooming a ripple.
 */
@Composable
fun QuietLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: LinkTone = LinkTone.Soft,
    @DrawableRes leadingIcon: Int? = null,
    chevron: Boolean = false,
    enabled: Boolean = true,
) {
    val color =
        when (tone) {
            LinkTone.Soft -> MaterialTheme.colorScheme.onSurfaceVariant
            LinkTone.Ink -> MaterialTheme.colorScheme.onSurface
            LinkTone.Destructive -> MaterialTheme.colorScheme.error
        }
    Row(
        modifier =
            modifier
                .heightIn(min = TouchTarget)
                .clickable(interactionSource = null, indication = QuietPress, enabled = enabled, role = Role.Button, onClick = onClick)
                .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let {
            Icon(painterResource(it), contentDescription = null, tint = color, modifier = Modifier.size(Glyph.s))
            Spacer(Modifier.width(Space.xs))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            // The chevron is drawn left of its box's centre; shifted so its tip ends on the margin.
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = color,
                modifier =
                    Modifier
                        .size(Glyph.s)
                        .offset(x = 5.dp),
            )
        }
    }
}

/**
 * A line glyph in a 48 dp target, with a round ripple for its only container: the quiet action of
 * a row or a field. [HeaderAction] is the larger one in ink, for bars.
 */
@Composable
fun IconAction(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    glyphSize: Dp = Glyph.m,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors =
            IconButtonDefaults.iconButtonColors(
                contentColor = tint,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            ),
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(glyphSize))
    }
}

/** A round hairline button for steppers, timers and the cook mode's back step. */
@Composable
fun HairlineIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier =
            modifier
                .size(maxOf(size, TouchTarget))
                .clip(CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .border(1.dp, if (enabled) extras.hairlineStrong else MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = contentDescription,
                tint = if (enabled) tint else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(Glyph.m),
            )
        }
    }
}

/** A round button resting on a photo: paper with a soft shadow, so it reads on any picture. */
@Composable
fun OnPhotoButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier =
            modifier
                .size(TouchTarget)
                // Unbounded, so the press does not cut off the button's shadow.
                .clickable(
                    interactionSource = null,
                    indication = ripple(bounded = false, radius = 20.dp),
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        // In dark mode a step lighter than the bar, with its fine edge, so it does not sink into dark photos.
        val dark = isDarkScheme
        Box(
            Modifier
                .size(40.dp)
                .photoButtonShadow(CircleShape)
                .background(
                    if (dark) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f) else extras.raised.copy(alpha = 0.92f),
                    CircleShape,
                ).then(if (dark) Modifier.border(Lines.hairline, extras.floatingEdge, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = contentDescription, tint = tint, modifier = Modifier.size(Glyph.m))
        }
    }
}
