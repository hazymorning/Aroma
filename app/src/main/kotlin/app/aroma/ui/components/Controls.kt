package app.aroma.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.BoxPadding
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget
import app.aroma.ui.theme.tabular
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Minus, the value and plus on one 40 dp pill of the tonal fill where it stands; each sign takes a
 * 48 dp target. For servings. The value's figure rolls up as it grows and down as it shrinks, and a
 * sign that can go no further fades back.
 */
@Composable
fun Stepper(
    label: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
) {
    val fill = AromaTheme.tonalFill
    Row(
        modifier =
            modifier
                .height(TouchTarget)
                .drawBehind {
                    val height = StepperHeight.toPx()
                    drawRoundRect(fill, Offset(0f, (size.height - height) / 2), Size(size.width, height), CornerRadius(height / 2))
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperSign(R.drawable.ic_remove, stringResource(R.string.action_decrease), onDecrease, canDecrease)
        Box(Modifier.widthIn(min = StepperValueWidth), contentAlignment = Alignment.Center) {
            RollingText(label, style = AromaTheme.type.label.tabular())
        }
        StepperSign(R.drawable.ic_add, stringResource(R.string.action_increase), onIncrease, canIncrease)
    }
}

@Composable
private fun StepperSign(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val shown by animateFloatAsState(if (enabled) 1f else DISABLED_CONTROL, Motion.state(), label = "stepperSign")
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(TouchTarget)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(StepperHeight)
                .clip(CircleShape)
                .indication(interaction, PressTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = description,
                tint = AromaTheme.colors.ink,
                modifier =
                    Modifier
                        .size(Glyph.control)
                        .graphicsLayer { alpha = shown },
            )
        }
    }
}

/** How dim a sign is while it can go no further. */
private const val DISABLED_CONTROL = 0.38f

private val StepperHeight = 40.dp

/** Room for "12 Portionen" between the signs, so the pill keeps its width as the value changes. */
private val StepperValueWidth = 64.dp

/**
 * A row of choices set as words, of which one is chosen: the chosen one on a soft sage pill, which
 * glides to the next choice, the others in second ink. It scrolls sideways, its first pill starting
 * on the page's edge, flush with the search field and the boxes above and below it.
 */
@Composable
fun SelectionRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: Dp = PageMargin,
) {
    val haptics = LocalHapticFeedback.current
    val positions = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val chosen = positions[selectedIndex]
    val left = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    LaunchedEffect(chosen) {
        val (x, w) = chosen ?: return@LaunchedEffect
        if (width.value == 0f) {
            left.snapTo(x)
            width.snapTo(w)
        } else {
            launch { left.animateTo(x, Motion.state()) }
            width.animateTo(w, Motion.state())
        }
    }
    val pill = AromaTheme.colors.accentSoft
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = contentPadding)
                .drawBehind {
                    if (chosen != null && width.value > 0f) {
                        val height = SegmentHeight.toPx()
                        drawRoundRect(
                            pill,
                            Offset(left.value, (size.height - height) / 2),
                            Size(width.value, height),
                            CornerRadius(height / 2),
                        )
                    }
                }.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        options.forEachIndexed { index, option ->
            SegmentWord(
                text = option,
                selected = index == selectedIndex,
                onClick = {
                    if (index != selectedIndex) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(index)
                    }
                },
                modifier = Modifier.onPlaced { positions[index] = it.positionInParent().x to it.size.width.toFloat() },
            )
        }
    }
}

/**
 * One choice among others that may be chosen together, on its own soft sage pill while chosen:
 * kinds of dish, categories, tags, days.
 */
@Composable
fun Segment(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pill by animateColorAsState(if (selected) AromaTheme.colors.accentSoft else Color.Transparent, Motion.state(), label = "segment")
    SegmentWord(text, selected, onClick, modifier, pill)
}

@Composable
private fun SegmentWord(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pill: Color = Color.Transparent,
) {
    val colors = AromaTheme.colors
    val color by animateColorAsState(if (selected) colors.ink else colors.inkSecondary, Motion.state(), label = "segmentText")
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .height(TouchTarget)
                .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(SegmentHeight)
                .clip(CircleShape)
                .background(pill)
                .indication(interaction, PressTint)
                .padding(horizontal = SegmentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = AromaTheme.type.label, color = color, maxLines = 1)
        }
    }
}

/** The pill of a chosen word, and the room inside it on either side of the word. */
private val SegmentHeight = 36.dp
private val SegmentPadding = Space.s12

/** The air between two segments, so two chosen pills side by side never touch. */
val SegmentGap: Dp = Space.s4

/**
 * The tick of a list to tick off: a ring on the tonal fill that fills with sage and draws its check
 * in paper. Taken back, the check fades with the sage instead of vanishing, and draws itself anew
 * the next time. The row it sits in is the target, and gives the haptic tick.
 */
@Composable
fun CheckCircle(checked: Boolean, modifier: Modifier = Modifier, size: Dp = CheckSize) {
    val fill = remember { Animatable(if (checked) 1f else 0f) }
    val draw = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) {
        if (checked) {
            launch { fill.animateTo(1f, Motion.edge()) }
            // A check still standing, taken back and ticked again before it had faded, stays drawn.
            if (draw.value < 1f) draw.animateTo(1f, tween(Motion.STATE, delayMillis = Motion.EDGE, easing = Motion.Decelerate))
        } else {
            fill.animateTo(0f, Motion.edge())
            draw.snapTo(0f)
        }
    }
    val colors = AromaTheme.colors
    val ground = AromaTheme.tonalFill
    Spacer(
        modifier
            .size(size)
            .drawWithCache {
                val radius = this.size.minDimension / 2
                val ring = CheckRing.toPx()
                val ringStroke = Stroke(ring)
                val checkStroke = Stroke(width = CheckStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val w = this.size.width
                val h = this.size.height
                val check =
                    PathMeasure().apply {
                        setPath(
                            Path().apply {
                                moveTo(0.29f * w, 0.52f * h)
                                lineTo(0.44f * w, 0.66f * h)
                                lineTo(0.71f * w, 0.37f * h)
                            },
                            false,
                        )
                    }
                val drawn = Path()
                onDrawBehind {
                    val filled = fill.value
                    if (filled < 1f) {
                        drawCircle(ground, radius = radius)
                        drawCircle(colors.inkTertiary.copy(alpha = 1f - filled), radius = radius - ring / 2, style = ringStroke)
                    }
                    if (filled > 0f) drawCircle(colors.accent.copy(alpha = filled), radius = radius)
                    val stroke = draw.value
                    if (stroke > 0f && filled > 0f) {
                        drawn.rewind()
                        check.getSegment(0f, check.length * stroke, drawn, true)
                        drawPath(drawn, colors.onInk.copy(alpha = filled), style = checkStroke)
                    }
                }
            },
    )
}

/** The tick's size, its ring and the stroke of its check. */
val CheckSize: Dp = 24.dp
private val CheckRing = 1.5.dp
private val CheckStroke = 1.75.dp

/**
 * A switch: on, a sage track with a paper knob; off, a pressed-paper track with a tertiary knob; no
 * edge. The knob glides across in a change of state's time and the track takes on its colour as it
 * goes; the knob keeps its size, pressed or not. The row it stands in takes the tap.
 */
@Composable
private fun AromaSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = AromaTheme.colors
    val on = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) { on.animateTo(if (checked) 1f else 0f, Motion.state()) }
    Spacer(
        modifier
            .size(SwitchWidth, SwitchHeight)
            .drawBehind {
                val position = on.value
                val height = size.height
                drawRoundRect(lerp(colors.surfacePressed, colors.accent, position), cornerRadius = CornerRadius(height / 2))
                val knob = SwitchKnob.toPx() / 2
                val inset = height / 2
                drawCircle(
                    color = lerp(colors.inkTertiary, colors.onInk, position),
                    radius = knob,
                    center = Offset(inset + (size.width - 2 * inset) * position, height / 2),
                )
            },
    )
}

/** The switch's track, and its knob, which stands as large off as on. */
private val SwitchWidth = 52.dp
private val SwitchHeight = 32.dp
private val SwitchKnob = 24.dp

/**
 * A row of settings in a box: a 20 dp glyph in second ink, the title, and a [description] under
 * it; on the right of the title the current [value] with a chevron, or a switch where [checked] is
 * set. The description runs on under the value or the switch, so a long one keeps the box's width
 * instead of a narrow column. A [trailing] control stands beside the whole row. The whole row takes
 * the tap. Rows of one group stand in one box, a separator between each two.
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    description: String? = null,
    value: String? = null,
    checked: Boolean? = null,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = onClick != null && checked == null,
    titleColor: Color = AromaTheme.colors.ink,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AromaTheme.colors
    val haptics = LocalHapticFeedback.current
    val tap =
        when {
            checked != null && onClick != null -> {
                Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = {
                    // The phone confirms the switch turning, on or off, as Android's own switches do.
                    haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onClick()
                })
            }

            onClick != null -> {
                Modifier.clickable(role = Role.Button, onClick = onClick)
            }

            else -> {
                Modifier
            }
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = SettingsRowHeight)
                .then(tap)
                .padding(horizontal = BoxPadding, vertical = Space.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(Modifier.heightIn(min = TitleLine), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = if (titleColor == colors.error) colors.error else colors.inkSecondary,
                        modifier = Modifier.size(Glyph.control),
                    )
                    Spacer(Modifier.width(Space.s16))
                }
                Text(title, style = AromaTheme.type.body, color = titleColor, modifier = Modifier.weight(1f))
                if (value != null) {
                    Spacer(Modifier.width(Space.s16))
                    Text(value, style = AromaTheme.type.meta, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                }
                when {
                    checked != null -> {
                        Spacer(Modifier.width(Space.s16))
                        AromaSwitch(checked = checked)
                    }

                    chevron && trailing == null -> {
                        Spacer(Modifier.width(Space.s4))
                        Icon(
                            painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = colors.inkSecondary,
                            modifier = Modifier.size(Glyph.control),
                        )
                    }
                }
            }
            if (description != null) {
                Text(
                    description,
                    style = AromaTheme.type.bodySmall,
                    modifier = Modifier.padding(start = if (icon != null) Glyph.control + Space.s16 else 0.dp),
                )
            }
        }
        if (trailing != null && checked == null) {
            Spacer(Modifier.width(Space.s16))
            trailing()
        }
    }
}

/** Where a settings row's text starts, after its glyph: the separators between rows start there. */
val SettingsTextEdge: Dp = BoxPadding + Glyph.control + Space.s16

private val SettingsRowHeight = 56.dp

/** The title's line in a settings row, as tall as a switch, so every row's description starts alike. */
private val TitleLine = 32.dp

/**
 * Five stars. Without [onRate] they show a rating in sage, small, where there is one. With it they
 * are 48 dp targets that fill in sage up to the tapped star, one after another; where [clearable],
 * a tap on the star already chosen takes the rating back, and [onRate] hears 0.
 */
@Composable
fun RatingStars(
    rating: Double?,
    modifier: Modifier = Modifier,
    starSize: Dp = StarSize,
    onRate: ((Int) -> Unit)? = null,
    clearable: Boolean = false,
) {
    if (onRate == null) {
        Stars(rating, starSize, onRate = null, target = 0.dp, clearable = false, modifier)
        return
    }
    BoxWithConstraints(modifier) {
        val target = if (constraints.hasBoundedWidth) minOf(TouchTarget, maxWidth / 5).coerceAtLeast(starSize) else TouchTarget
        Stars(rating, starSize, onRate, target, clearable)
    }
}

@Composable
private fun Stars(
    rating: Double?,
    starSize: Dp,
    onRate: ((Int) -> Unit)?,
    target: Dp,
    clearable: Boolean,
    modifier: Modifier = Modifier,
) {
    val value = rating ?: 0.0
    val description = ratingDescription(rating)
    // Screen readers hear the stars as one choice among five: only the rated star is selected.
    val chosen = rating?.roundToInt()
    val haptics = LocalHapticFeedback.current
    val colors = AromaTheme.colors
    Row(
        modifier =
            modifier
                .semantics(mergeDescendants = onRate == null) { contentDescription = description }
                .then(if (onRate != null) Modifier.selectableGroup() else Modifier),
        horizontalArrangement = Arrangement.spacedBy(if (onRate != null) 0.dp else StarGap),
    ) {
        for (star in 1..5) {
            val icon =
                when {
                    value >= star -> R.drawable.ic_star_filled
                    value >= star - 0.5 && floor(value) == (star - 1).toDouble() -> R.drawable.ic_star_half_filled
                    else -> R.drawable.ic_star
                }
            val lit = value >= star - 0.5
            val tint = if (lit) colors.accent else colors.inkTertiary
            if (onRate != null) {
                val clears = clearable && star == chosen
                // Stars up to the tapped one fill in turn. The wait belongs to the animation, so with animations off there is none.
                val fill = remember(star) { Animatable(1f) }
                LaunchedEffect(lit) {
                    if (lit) {
                        fill.snapTo(FILL_FROM)
                        fill.animateTo(1f, tween(Motion.EDGE, delayMillis = STAR_STAGGER_MILLIS * (star - 1)))
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .size(target)
                            .selectable(selected = star == chosen, role = Role.RadioButton, onClick = {
                                haptics.performHapticFeedback(if (clears) HapticFeedbackType.ToggleOff else HapticFeedbackType.Confirm)
                                onRate(if (clears) 0 else star)
                            }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription =
                            if (clears) stringResource(R.string.rating_remove) else pluralStringResource(R.plurals.rating_set, star, star),
                        tint = tint,
                        modifier =
                            Modifier
                                .size(starSize)
                                .graphicsLayer { alpha = fill.value },
                    )
                }
            } else {
                Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(starSize))
            }
        }
    }
}

/** What a screen reader says of a rating: the stars out of five, or that there is none yet. */
@Composable
@ReadOnlyComposable
fun ratingDescription(rating: Double?): String =
    if (rating == null) stringResource(R.string.rating_none) else stringResource(R.string.rating_description, rating)

/** A shown rating's stars, and the room between them. */
val StarSize: Dp = 14.dp
private val StarGap = 2.dp

/** How faint a star starts as it fills in, and how long each waits for the one before it. */
private const val FILL_FROM = 0.4f
private const val STAR_STAGGER_MILLIS = 50

/** Interactive stars stand centred in 48 dp targets; offset the row by this to set the first glyph on an edge. */
fun ratingTouchInset(starSize: Dp): Dp = (TouchTarget - starSize) / 2
