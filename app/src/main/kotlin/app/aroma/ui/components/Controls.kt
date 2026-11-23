package app.aroma.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.ui.navigation.Transitions.Settle
import app.aroma.ui.theme.KickerStyle
import app.aroma.ui.theme.Lines
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.contactShadow
import app.aroma.ui.theme.extras
import app.aroma.ui.theme.isDarkScheme
import app.aroma.ui.theme.tabular
import kotlin.math.floor

/** A small tracked label in capitals, where a cookbook would print a section name. */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text.uppercase(currentLocale()),
        style = KickerStyle.tabular(),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** A 0.5 dp line between rows, inset to the text column. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = Lines.hairline, color = MaterialTheme.colorScheme.outlineVariant)
}

/** A section's serif title on the 24 dp margin, with an optional quiet link to more at the end. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                // The same height with or without a link, so sections keep one rhythm on every screen.
                .heightIn(min = 48.dp)
                .padding(horizontal = ScreenMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { heading() },
        )
        if (actionLabel != null && onAction != null) QuietLink(actionLabel, onAction, chevron = true)
    }
}

/** Minus, the value and plus, as hairline circles; used for servings. */
@Composable
fun Stepper(
    label: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        HairlineIconButton(R.drawable.ic_remove, stringResource(R.string.action_decrease), onDecrease, enabled = canDecrease)
        AnimatedContent(
            targetState = label,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
            label = "stepperValue",
        ) { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.tabular(),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 96.dp),
            )
        }
        HairlineIconButton(R.drawable.ic_add, stringResource(R.string.action_increase), onIncrease)
    }
}

/**
 * Five stars. Without [onRate] they show a rating in ink; with it they are 48 dp targets that
 * fill in sage up to the tapped star, one after another.
 */
@Composable
fun RatingStars(rating: Double?, modifier: Modifier = Modifier, starSize: Int = 14, onRate: ((Int) -> Unit)? = null) {
    val value = rating ?: 0.0
    val description = stringResource(R.string.rating_description, value)
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier.semantics(mergeDescendants = onRate == null) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(if (onRate != null) 0.dp else 2.dp),
    ) {
        for (star in 1..5) {
            val icon =
                when {
                    value >= star -> R.drawable.ic_star_filled
                    value >= star - 0.5 && floor(value) == (star - 1).toDouble() -> R.drawable.ic_star_half_filled
                    else -> R.drawable.ic_star
                }
            val lit = value >= star - 0.5
            val tint =
                when {
                    onRate != null && lit -> MaterialTheme.colorScheme.primary
                    lit -> MaterialTheme.colorScheme.onSurface
                    onRate != null -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                }
            if (onRate != null) {
                // Stars up to the tapped one fill in turn, 50 ms apart.
                val fill = remember(star) { Animatable(1f) }
                LaunchedEffect(lit) {
                    if (lit) {
                        fill.snapTo(0.4f)
                        kotlinx.coroutines.delay(50L * (star - 1))
                        fill.animateTo(1f, tween(160))
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .selectable(selected = lit, role = Role.RadioButton, onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                onRate(star)
                            }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = pluralStringResource(R.plurals.rating_set, star, star),
                        tint = tint,
                        modifier =
                            Modifier
                                .size(starSize.dp)
                                .alpha(fill.value),
                    )
                }
            } else {
                Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(starSize.dp))
            }
        }
    }
}

/** Interactive stars leave this much room before the first glyph; offset the row by it to keep the keyline. */
fun ratingTouchInset(starSize: Int): Dp = (48.dp - starSize.dp) / 2

/** A recipe's category or tag as a quiet hairline chip. */
@Composable
fun TagChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AromaFilterChip(selected = false, label = label, onClick = onClick, modifier = modifier)
}

/** A hairline stadium chip; selected chips take the action colour. */
@Composable
fun AromaFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes trailingIcon: Int? = null,
) {
    val container by animateColorAsState(if (selected) extras.cta else Color.Transparent, tween(Motion.STATE), label = "chip")
    val content by animateColorAsState(
        if (selected) extras.onCta else MaterialTheme.colorScheme.onSurface,
        tween(Motion.STATE),
        label = "chipContent",
    )
    Surface(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = if (selected) null else BorderStroke(Lines.control, extras.hairlineStrong),
        modifier = modifier.height(32.dp),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = if (trailingIcon != null) 12.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            if (trailingIcon != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    painterResource(trailingIcon),
                    contentDescription = null,
                    tint = if (selected) content else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Material's switch with a full-size thumb in both states and the action colour when on. */
@Composable
fun AromaSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        // A non-null thumb content keeps the unchecked thumb at its full size.
        thumbContent = {},
        colors =
            SwitchDefaults.colors(
                checkedTrackColor = extras.cta,
                checkedThumbColor = extras.onCta,
                checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedBorderColor = Color.Transparent,
            ),
        modifier = modifier,
    )
}

/** A whole row that toggles, with the switch at its end, as Android settings screens do it. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = ScreenMargin, vertical = 12.dp),
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = if (subtitle == null) 56.dp else 72.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AromaSwitch(checked = checked, onCheckedChange = null)
    }
}

/** Equal segments on a pale track, with one paper thumb that slides to the selection. */
@Composable
fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val offset by animateDpAsState(segment * selectedIndex.coerceAtLeast(0), tween(Motion.EXPAND, easing = Settle), label = "thumb")
        if (selectedIndex >= 0) {
            Box(
                Modifier
                    .offset { IntOffset(offset.roundToPx(), 0) }
                    .width(segment)
                    .fillMaxHeight()
                    .contactShadow(CircleShape)
                    .background(if (isDarkScheme) MaterialTheme.colorScheme.surfaceContainerHighest else extras.raised, CircleShape),
            )
        }
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(Motion.STATE),
                    label = "segment",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = selected, role = Role.RadioButton, interactionSource = null, indication = null) {
                            if (!selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(option, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * The shopping check: a fine ring that fills with sage, then draws its check stroke. The row it
 * sits in is the touch target.
 */
@Composable
fun CheckCircle(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val fill by animateFloatAsState(if (checked) 1f else 0f, tween(140), label = "checkFill")
    val draw = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) {
        if (checked) {
            draw.animateTo(1f, tween(200, delayMillis = 80, easing = Settle))
        } else {
            draw.snapTo(0f)
        }
    }
    val ring = MaterialTheme.colorScheme.outline
    val filled = MaterialTheme.colorScheme.primary
    val mark = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier.size(size)) {
        val radius = this.size.minDimension / 2
        if (fill < 1f) {
            drawCircle(ring.copy(alpha = 1f - fill), radius = radius - Lines.checkRing.toPx() / 2, style = Stroke(Lines.checkRing.toPx()))
        }
        if (fill > 0f) drawCircle(filled.copy(alpha = fill), radius = radius)
        if (draw.value > 0f) {
            val w = this.size.width
            val h = this.size.height
            val path =
                Path().apply {
                    moveTo(0.28f * w, 0.52f * h)
                    lineTo(0.44f * w, 0.67f * h)
                    lineTo(0.72f * w, 0.36f * h)
                }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * draw.value, partial, true)
            drawPath(partial, mark, style = Stroke(width = 1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
