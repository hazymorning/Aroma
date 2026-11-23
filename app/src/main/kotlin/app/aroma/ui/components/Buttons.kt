package app.aroma.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.extras
import app.aroma.ui.theme.photoButtonShadow

/** The one filled action of a screen. While [loading], a small spinner takes the label's place. */
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
        modifier = modifier.height(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // The label keeps its place while loading, so the button never changes width.
            Row(Modifier.alpha(if (loading) 0f else 1f), verticalAlignment = Alignment.CenterVertically) {
                leadingIcon?.let {
                    Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                trailingIcon?.let {
                    Spacer(Modifier.width(8.dp))
                    Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            if (loading) CircularProgressIndicator(strokeWidth = 1.5.dp, color = extras.onCta, modifier = Modifier.size(18.dp))
        }
    }
}

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
                    modifier = Modifier.size(if (compact) 16.dp else 20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** How a [QuietLink] reads: [Soft] for navigation, [Ink] for actions in place, [Destructive] for removal. */
enum class LinkTone { Soft, Ink, Destructive }

/**
 * A text link with no container: the text sits exactly on the keyline and the touch area grows
 * away from it. Pressing dims it instead of blooming a ripple.
 */
@Composable
fun QuietLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: LinkTone = LinkTone.Soft,
    chevron: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        when {
            !enabled -> 0.38f
            pressed -> 0.6f
            else -> 1f
        },
        tween(Motion.PRESS),
        label = "linkAlpha",
    )
    val color =
        when (tone) {
            LinkTone.Soft -> MaterialTheme.colorScheme.onSurfaceVariant
            LinkTone.Ink -> MaterialTheme.colorScheme.onSurface
            LinkTone.Destructive -> MaterialTheme.colorScheme.error
        }
    Row(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        }
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
                .size(maxOf(size, 48.dp))
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
                modifier = Modifier.size(20.dp),
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
                .size(48.dp)
                // Unbounded, so the press does not cut off the button's shadow.
                .clickable(
                    interactionSource = null,
                    indication = ripple(bounded = false, radius = 20.dp),
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .photoButtonShadow(CircleShape)
                .background(extras.raised.copy(alpha = 0.92f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}
