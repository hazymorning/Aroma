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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
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
import kotlin.math.roundToInt

/**
 * A message for empty lists, missing results and errors, set on the page's margin: a line glyph, a
 * serif title, a quiet explanation and the action across the column. [primaryAction] makes the
 * action the filled button, for when it is the only way forward. A message that [standsAlone], with
 * nothing after it on its page, stands a third of the way down the room left below it, where the
 * eye rests, as the end of cooking does.
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
    standsAlone: Boolean = true,
) {
    val density = LocalDensity.current
    // The tab bar, the navigation bar or the keyboard: what takes the bottom of the window.
    val cover = maxOf(with(density) { LocalBottomBarInset.current.roundToPx() }, WindowInsets.safeDrawing.getBottom(density))
    Column(
        modifier =
            modifier
                .then(if (standsAlone) ThirdOfRoomElement(cover) else Modifier)
                .padding(start = ScreenMargin, end = ScreenMargin, top = Space.xl, bottom = Space.l),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .offset(x = -GlyphInk)
                    .size(Glyph.xl),
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
            val across = Modifier.fillMaxWidth()
            if (primaryAction) PrimaryButton(actionLabel, onAction, across) else SecondaryButton(actionLabel, onAction, across)
        }
    }
}

/** The longest a message's lines run, so it reads as a short note even on a tablet. */
private val MessageWidth = 320.dp

/** Aroma's line glyphs start their ink about 3.5 of their 24 units in; moved by that, the ink starts on the text's edge. */
private val GlyphInk = Glyph.xl * 3.5f / 24

/**
 * Lowers what it holds by a third of the room left below it in the window, down to [cover]. Where it
 * stands only shows as it is placed, so a new lift measures it once more, within the same frame.
 */
private data class ThirdOfRoomElement(val cover: Int) : ModifierNodeElement<ThirdOfRoomNode>() {
    override fun create() = ThirdOfRoomNode(cover)

    override fun update(node: ThirdOfRoomNode) {
        node.cover = cover
        node.invalidateMeasurement()
    }
}

private class ThirdOfRoomNode(var cover: Int) :
    Modifier.Node(),
    LayoutModifierNode {
    private var lift = 0

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val content = measurable.measure(constraints.copy(minHeight = 0))
        return layout(content.width, constraints.constrainHeight(content.height + lift)) {
            coordinates?.let { placed ->
                val top = placed.positionInRoot().y.roundToInt()
                val room = (placed.findRootCoordinates().size.height - cover - top - content.height).coerceAtLeast(0)
                if (room / 3 != lift) {
                    lift = room / 3
                    invalidateMeasurement()
                }
            }
            content.place(0, lift)
        }
    }
}

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
