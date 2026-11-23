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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.CardPadding
import app.aroma.ui.theme.ChipHeight
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Lines
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Motion.Settle
import app.aroma.ui.theme.RowHeight
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.SectionSpace
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget
import app.aroma.ui.theme.extras
import app.aroma.ui.theme.isDarkScheme
import app.aroma.ui.theme.tabular
import kotlin.math.floor
import kotlin.math.roundToInt

/** A 0.5 dp line between rows of a card, inset to their text. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = Lines.hairline, color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * A section's serif title on the margin, [SectionSpace] below what comes before, with an optional
 * link to more at its end. Its content follows [Space.xs] below.
 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = SectionSpace)
                // The same height with or without a link, so sections keep one rhythm on every screen.
                .heightIn(min = TouchTarget)
                .padding(horizontal = ScreenMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = AromaType.SectionTitle,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { heading() },
        )
        if (actionLabel != null && onAction != null) QuietLink(actionLabel, onAction, chevron = true)
    }
}

/** Minus, the value and plus on one soft pill; used for servings. */
@Composable
fun Stepper(
    label: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .height(TouchTarget)
                .clip(CircleShape)
                .background(extras.card),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(
            R.drawable.ic_remove,
            stringResource(R.string.action_decrease),
            onDecrease,
            tint = MaterialTheme.colorScheme.onSurface,
            enabled = canDecrease,
        )
        AnimatedContent(
            targetState = label,
            transitionSpec = { fadeIn(tween(Motion.STATE)) togetherWith fadeOut(tween(Motion.STATE)) },
            label = "stepperValue",
        ) { value ->
            Text(
                text = value,
                style = AromaType.Label.tabular(),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = StepperValueWidth),
            )
        }
        IconAction(R.drawable.ic_add, stringResource(R.string.action_increase), onIncrease, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** Room for "12 servings" between the stepper's buttons, so the pill keeps its width as the value changes. */
private val StepperValueWidth = 80.dp

/**
 * Five stars. Without [onRate] they show a rating in ink; with it they are 48 dp targets that
 * fill in sage up to the tapped star, one after another. Where five targets do not fit, they
 * share the width, so the fifth star never falls off the edge.
 */
@Composable
fun RatingStars(rating: Double?, modifier: Modifier = Modifier, starSize: Int = 14, onRate: ((Int) -> Unit)? = null) {
    if (onRate == null) {
        Stars(rating, starSize, onRate = null, target = 0.dp, modifier)
        return
    }
    BoxWithConstraints(modifier) {
        val target = if (constraints.hasBoundedWidth) minOf(TouchTarget, maxWidth / 5).coerceAtLeast(starSize.dp) else TouchTarget
        Stars(rating, starSize, onRate, target)
    }
}

@Composable
private fun Stars(
    rating: Double?,
    starSize: Int,
    onRate: ((Int) -> Unit)?,
    target: Dp,
    modifier: Modifier = Modifier,
) {
    val value = rating ?: 0.0
    val description = ratingDescription(rating)
    // Screen readers hear the stars as one choice among five: only the rated star is selected.
    val chosen = rating?.roundToInt()
    val haptics = LocalHapticFeedback.current
    Row(
        modifier =
            modifier
                .semantics(mergeDescendants = onRate == null) { contentDescription = description }
                .then(if (onRate != null) Modifier.selectableGroup() else Modifier),
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
                // Stars up to the tapped one fill in turn.
                val fill = remember(star) { Animatable(1f) }
                LaunchedEffect(lit) {
                    if (lit) {
                        fill.snapTo(0.4f)
                        kotlinx.coroutines.delay(STAR_STAGGER_MILLIS * (star - 1))
                        fill.animateTo(1f, tween(Motion.STATE))
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .size(target)
                            .selectable(selected = star == chosen, role = Role.RadioButton, onClick = {
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

/** What a screen reader says of a rating: the stars out of five, or that there is none yet. */
@Composable
@ReadOnlyComposable
fun ratingDescription(rating: Double?): String =
    if (rating == null) stringResource(R.string.rating_none) else stringResource(R.string.rating_description, rating)

/** How long each star waits for the one before it as the stars fill in. */
private const val STAR_STAGGER_MILLIS = 50L

/** Interactive stars stand centred in 48 dp targets; offset the row by this to set the first glyph on a keyline. */
fun ratingTouchInset(starSize: Int): Dp = (TouchTarget - starSize.dp) / 2

/** A soft stadium chip; a chosen chip takes the sage wash. */
@Composable
fun AromaFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes trailingIcon: Int? = null,
) {
    val container by animateColorAsState(if (selected) extras.selected else extras.card, tween(Motion.STATE), label = "chip")
    val content by animateColorAsState(
        if (selected) extras.onSelected else MaterialTheme.colorScheme.onSurface,
        tween(Motion.STATE),
        label = "chipContent",
    )
    Surface(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = modifier.height(ChipHeight),
    ) {
        Row(
            Modifier.padding(start = Space.m, end = if (trailingIcon != null) Space.s else Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = AromaType.Label, maxLines = 1)
            if (trailingIcon != null) {
                Spacer(Modifier.width(Space.xxs))
                Icon(
                    painterResource(trailingIcon),
                    contentDescription = null,
                    tint = if (selected) content else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Glyph.s),
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
        // Thumb content with a real size keeps the unchecked thumb as large as the checked one.
        thumbContent = { Spacer(Modifier.size(SwitchDefaults.IconSize)) },
        colors =
            SwitchDefaults.colors(
                checkedTrackColor = extras.cta,
                checkedThumbColor = extras.onCta,
                checkedBorderColor = Color.Transparent,
                // A step darker than the card it sits on, so an open switch still reads as one.
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceDim,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedBorderColor = Color.Transparent,
            ),
        modifier = modifier,
    )
}

/** A whole row of a card that toggles, with the switch at its end, as Android settings screens do it. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = if (subtitle == null) RowHeight.oneLine else RowHeight.twoLines)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = CardPadding, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AromaType.Body)
            if (subtitle != null) {
                Text(subtitle, style = AromaType.Small, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AromaSwitch(checked = checked, onCheckedChange = null)
    }
}

/** Equal segments on a soft pill; the chosen one takes the sage wash, which slides to it. */
@Composable
fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(TouchTarget)
            .background(extras.card, CircleShape)
            .padding(SegmentInset),
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val offset by animateDpAsState(segment * selectedIndex.coerceAtLeast(0), tween(Motion.EXPAND, easing = Settle), label = "thumb")
        if (selectedIndex >= 0) {
            Box(
                Modifier
                    .offset { IntOffset(offset.roundToPx(), 0) }
                    .width(segment)
                    .fillMaxHeight()
                    .background(extras.selected, CircleShape),
            )
        }
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                val color by animateColorAsState(
                    if (selected) extras.onSelected else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Text(option, style = AromaType.Label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** How far the chosen segment stands inside the pill. */
private val SegmentInset = 4.dp

/**
 * The views of a page, as words on the margin: the current one in ink over a short sage rule, the
 * others in soft ink. Unlike chips, which filter what a view shows, tabs change the view.
 */
@Composable
fun PageTabs(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                tween(Motion.STATE),
                label = "tab",
            )
            val rule by animateFloatAsState(if (selected) 1f else 0f, tween(Motion.STATE, easing = Settle), label = "tabRule")
            Column(
                Modifier
                    .height(TouchTarget)
                    .width(IntrinsicSize.Max)
                    .selectable(selected = selected, role = Role.Tab, interactionSource = null, indication = QuietPress) {
                        if (!selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(index)
                        }
                    },
                verticalArrangement = Arrangement.Center,
            ) {
                Text(option, style = AromaType.BodyStrong, color = color, maxLines = 1)
                Spacer(Modifier.height(TabRuleGap))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(TabRuleHeight)
                        .graphicsLayer { alpha = rule }
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
    }
}

private val TabRuleGap = 6.dp
private val TabRuleHeight = 2.dp

/**
 * The shopping check: a fine ring that fills with sage, then draws its check stroke. The row it
 * sits in is the touch target.
 */
@Composable
fun CheckCircle(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val fill by animateFloatAsState(if (checked) 1f else 0f, tween(Motion.PRESS), label = "checkFill")
    val draw = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) {
        if (checked) {
            draw.animateTo(1f, tween(Motion.STATE, delayMillis = Motion.PRESS, easing = Settle))
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
