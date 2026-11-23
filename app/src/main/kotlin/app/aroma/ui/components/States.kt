package app.aroma.ui.components

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.data.AromaError
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Lines
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.extras

/**
 * A message for empty lists, missing results and errors, set on the page's margin: a line glyph, a
 * serif title and a quiet explanation. [primaryAction] makes the action the filled button, for when
 * it is the only way forward.
 */
@Composable
fun MessageState(
    icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    primaryAction: Boolean = false,
) {
    Column(modifier = modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.xl, bottom = Space.l)) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Glyph.xl),
        )
        Spacer(Modifier.height(Space.m))
        Text(title, style = AromaType.SectionTitle)
        if (body != null) {
            Spacer(Modifier.height(Space.xs))
            Text(
                text = body,
                style = AromaType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = MessageWidth),
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.l))
            if (primaryAction) PrimaryButton(actionLabel, onAction) else SecondaryButton(actionLabel, onAction)
        }
    }
}

/** The longest a message's lines run, so it reads as a short note even on a tablet. */
private val MessageWidth = 320.dp

@Composable
fun ErrorState(error: AromaError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    MessageState(
        icon = if (error is AromaError.Offline) R.drawable.ic_cloud_off else R.drawable.ic_error,
        title = stringResource(error.titleRes()),
        body = stringResource(error.messageRes()),
        actionLabel = stringResource(R.string.action_retry),
        onAction = onRetry,
        modifier = modifier,
    )
}

/** Softly pulsing block that stands in for content while it loads. */
@Composable
fun Placeholder(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.medium) {
    val transition = rememberInfiniteTransition(label = "placeholder")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(Motion.PULSE, easing = LinearEasing), RepeatMode.Reverse),
        label = "placeholderAlpha",
    )
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(extras.card),
    )
}

/** A line of text that is still loading, as long as [modifier] makes it. */
@Composable
fun PlaceholderLine(modifier: Modifier = Modifier) {
    Placeholder(modifier.height(Space.s), shape = MaterialTheme.shapes.extraSmall)
}

@Composable
fun PlaceholderLines(lines: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        repeat(lines) { index ->
            PlaceholderLine(
                Modifier
                    .widthIn(max = if (index == lines - 1) MessageWidth / 2 else MessageWidth)
                    .fillMaxWidth(),
            )
        }
    }
}

/** The one way Aroma shows that something is under way: a fine ring in quiet ink. */
@Composable
fun InlineProgress(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    CircularProgressIndicator(strokeWidth = Lines.focus, color = color, modifier = modifier.size(Glyph.m))
}
