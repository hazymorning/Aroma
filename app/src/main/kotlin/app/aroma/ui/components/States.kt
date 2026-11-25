package app.aroma.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.data.AromaError
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.Space

/**
 * Emptiness, said quietly: one line in the description role and one text action, no picture, never
 * taller than 56 dp and never larger than the content it stands for. Where the two do not fit on
 * one line, the action moves under the words instead of squeezing them.
 */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    edge: Dp = PageMargin,
) {
    FlowRow(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = EmptyHeight)
                .padding(horizontal = edge),
        horizontalArrangement = Arrangement.spacedBy(Space.s12),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = AromaTheme.type.bodySmall)
        if (actionLabel != null && onAction != null) TextAction(actionLabel, onAction)
    }
}

private val EmptyHeight = 48.dp

/** An error, as one line with "Try again". */
@Composable
fun ErrorState(error: AromaError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        text = stringResource(error.titleRes()),
        actionLabel = stringResource(R.string.action_retry),
        onAction = onRetry,
        modifier = modifier,
    )
}

/**
 * What stands in for content while it loads: a quiet fill in the shape of what is coming, which
 * breathes softly from 60 % to full and back. It waits a moment and then fades in, so what loads
 * at once goes straight to the page without a flash of grey.
 */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: Shape = RectangleShape) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = SKELETON_LOW,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(Motion.PULSE / 2, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val appear = remember { Animatable(0f) }
    LaunchedEffect(appear) { appear.animateTo(1f, tween(Motion.EDGE, delayMillis = Motion.SKELETON_DELAY, easing = Motion.Decelerate)) }
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha * appear.value }
            .clip(shape)
            .background(AromaTheme.colors.surfacePressed),
    )
}

/**
 * A skeleton in a lazy list hands over to what it stood for: it fades out where it stands while
 * the content fades in over it, instead of vanishing and leaving the page bare for a moment. The
 * item needs a key of its own.
 */
fun Modifier.skeletonRow(scope: LazyItemScope): Modifier =
    with(scope) { animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = tween(Motion.EDGE, easing = LinearEasing)) }

/** A line of text that is still loading, as long as [modifier] makes it, as tall as an x-height and a half. */
@Composable
fun SkeletonLine(modifier: Modifier = Modifier) {
    Skeleton(modifier.height(Space.s12), shape = AromaTheme.shapes.round)
}

private const val SKELETON_LOW = 0.6f

/**
 * An error set as text where it happened, under the field or button it belongs to: a small mark and
 * the [title] in the error colour, the [message] below in second ink, and a [hint] on what to do
 * about it where the message does not say. TalkBack reads it out as it appears.
 */
@Composable
fun ErrorNote(title: String, message: String?, modifier: Modifier = Modifier, hint: String? = null) {
    val colors = AromaTheme.colors
    val type = AromaTheme.type
    // The mark stands centred on the title's first line, at any font size.
    val firstLine = with(LocalDensity.current) { type.label.lineHeight.toDp() }
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Space.s8),
    ) {
        Box(Modifier.height(firstLine), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = colors.error, modifier = Modifier.size(Glyph.meta))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            Text(title, style = type.label, color = colors.error)
            if (message != null) Text(message, style = type.bodySmall)
            if (hint != null) Text(hint, style = type.bodySmall)
        }
    }
}

/** [ErrorNote] for an [error] of Aroma's own; an import that failed says why in Mealie's words where it gave some. */
@Composable
fun ErrorNote(error: AromaError, modifier: Modifier = Modifier, hint: String? = null) {
    ErrorNote(
        title = stringResource(error.titleRes()),
        message = (error as? AromaError.ImportFailed)?.detail ?: stringResource(error.messageRes()),
        modifier = modifier,
        hint = hint,
    )
}

/** That the page shows the phone's own copy while Mealie is out of reach: one quiet line. */
@Composable
fun OfflineNotice(modifier: Modifier = Modifier) {
    MetaLine(items = listOf(stringResource(R.string.offline_notice)), icon = R.drawable.ic_cloud_off, modifier = modifier)
}
