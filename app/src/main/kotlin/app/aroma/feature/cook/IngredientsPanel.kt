package app.aroma.feature.cook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
import app.aroma.R
import app.aroma.core.format.IngredientLine
import app.aroma.data.settings.IngredientsInSteps
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.IngredientList
import app.aroma.ui.components.IngredientRow
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.CardPadding
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.RowHeight
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.extras
import app.aroma.ui.theme.tabular
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Whether the panel sums up the step's ingredients or lists them to tick off. */
internal enum class PanelValue { Collapsed, Expanded }

/** The ingredients a step shows, with their places in the recipe, by which they are ticked off. */
internal data class StepIngredients(val rows: List<IngredientRow>, val indexes: List<Int>, val all: Boolean)

/** All ingredients with their sections, those the step uses ([used]), or none, as the cook chose to see them. */
internal fun stepIngredients(rows: List<IngredientRow>, used: List<Int>, shown: IngredientsInSteps): StepIngredients =
    when (shown) {
        IngredientsInSteps.All -> {
            StepIngredients(rows, rows.indices.toList(), all = true)
        }

        IngredientsInSteps.Used -> {
            used.filter { it in rows.indices }.let {
                StepIngredients(
                    it.map { index ->
                        rows[index].copy(section = null)
                    },
                    it,
                    all = false,
                )
            }
        }

        IngredientsInSteps.None -> {
            StepIngredients(emptyList(), emptyList(), all = false)
        }
    }

@Composable
internal fun rememberPanelState(): AnchoredDraggableState<PanelValue> =
    rememberSaveable(saver = AnchoredDraggableState.Saver()) { AnchoredDraggableState(PanelValue.Collapsed) }

/**
 * The steps, with the [panel] laid over their foot. The panel is measured first, so the steps know
 * how far it is open and keep clear of it, and placed last, so it is drawn and touched above them,
 * while it still follows them for reading and the keyboard.
 */
@Composable
internal fun StepsWithPanel(panel: @Composable () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(contents = listOf(content, panel), modifier = modifier) { (steps, panels), constraints ->
        val panelPlaceables = panels.map { it.measure(constraints.copy(minHeight = 0)) }
        val stepPlaceables = steps.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            stepPlaceables.forEach { it.place(0, 0) }
            panelPlaceables.forEach { it.place(0, constraints.maxHeight - it.height) }
        }
    }
}

/**
 * The ingredients of the step on show, on a soft card above the step buttons, like a standard
 * bottom sheet: collapsed, a two-line row that sums them up; expanded by a tap or a drag, the list
 * to tick them off. Its height comes from [state] alone, never from the step, so paging leaves its
 * frame where it is and only crossfades what it holds. Away from the steps it fades out in place.
 */
@Composable
internal fun IngredientsPanel(
    ingredients: StepIngredients,
    checked: Set<Int>,
    onToggle: (Int) -> Unit,
    state: AnchoredDraggableState<PanelValue>,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val header = headerHeight()
    Box(modifier.panelFrame(state, header)) {
        AnimatedVisibility(visible, enter = fadeIn(tween(Motion.STATE)), exit = fadeOut(tween(Motion.STATE))) {
            PanelCard(ingredients, checked, onToggle, state, header)
        }
    }
}

/** Keeps a step clear of the panel below it, however far the panel is open, so the step scrolls above it. */
internal fun Modifier.abovePanel(state: AnchoredDraggableState<PanelValue>): Modifier =
    layout { measurable, constraints ->
        val panel = state.offset
        val inset = if (panel.isNaN()) 0 else panel.roundToInt() + PanelGap.roundToPx()
        val placeable = measurable.measure(constraints.offset(vertical = -inset))
        layout(placeable.width, placeable.height + inset) { placeable.place(0, 0) }
    }

/**
 * Sizes the panel by [state]: as tall as its [header] while collapsed, [EXPANDED_SHARE] of the room
 * it is given while expanded, whatever the step it shows.
 */
private fun Modifier.panelFrame(state: AnchoredDraggableState<PanelValue>, header: Dp): Modifier =
    layout { measurable, constraints ->
        val collapsed = header.roundToPx().toFloat()
        state.updateAnchors(
            DraggableAnchors {
                PanelValue.Collapsed at collapsed
                PanelValue.Expanded at maxOf(collapsed, constraints.maxHeight * EXPANDED_SHARE)
            },
            state.targetValue,
        )
        val height = state.requireOffset().roundToInt()
        val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/** How far the panel is open, from 0 for collapsed to 1 for expanded. */
private fun AnchoredDraggableState<PanelValue>.openFraction(): Float {
    val collapsed = anchors.positionOf(PanelValue.Collapsed)
    val expanded = anchors.positionOf(PanelValue.Expanded)
    return if (expanded > collapsed) ((offset - collapsed) / (expanded - collapsed)).coerceIn(0f, 1f) else 0f
}

/**
 * The collapsed panel is a two-line row, as tall on every step whatever its lines say, and as tall
 * as those lines need at the reader's text size.
 */
@Composable
private fun headerHeight(): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, density) {
        val lines = measurer.measure(LINE, AromaType.BodyStrong).size.height + measurer.measure(LINE, AromaType.Small).size.height
        maxOf(RowHeight.twoLines, with(density) { lines.toDp() } + Space.s * 2)
    }
}

@Composable
private fun PanelCard(
    ingredients: StepIngredients,
    checked: Set<Int>,
    onToggle: (Int) -> Unit,
    state: AnchoredDraggableState<PanelValue>,
    header: Dp,
) {
    val scope = rememberCoroutineScope()
    val expanded = state.targetValue == PanelValue.Expanded
    // A step without ingredients has nothing to open, though an open panel can still be closed there.
    val canToggle = expanded || ingredients.rows.isNotEmpty()
    val open by remember(state) { derivedStateOf { state.openFraction() > 0f } }
    val chevron by animateFloatAsState(if (canToggle) 1f else 0f, tween(Motion.STATE), label = "panelChevron")
    // A soft card's fill and corners, without the air SoftCard adds, so collapsed it is exactly its header.
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenMargin)
            .clip(MaterialTheme.shapes.medium)
            .background(extras.card),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(header)
                    .anchoredDraggable(
                        state = state,
                        reverseDirection = true,
                        orientation = Orientation.Vertical,
                        enabled = canToggle,
                        flingBehavior = AnchoredDraggableDefaults.flingBehavior(state, animationSpec = Motion.LetGo),
                    ).then(
                        if (canToggle) {
                            Modifier.clickable(
                                onClickLabel =
                                    stringResource(
                                        if (expanded) R.string.cook_ingredients_collapse else R.string.cook_ingredients_expand,
                                    ),
                                role = Role.Button,
                            ) {
                                scope.launch { state.animateTo(if (expanded) PanelValue.Collapsed else PanelValue.Expanded, Toggle) }
                            }
                        } else {
                            Modifier
                        },
                    ).padding(horizontal = CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Crossfade(ingredients, Modifier.weight(1f).fillMaxHeight(), tween(Motion.STATE), label = "panelSummary") { shown ->
                Summary(shown, checked, openFraction = { state.openFraction() })
            }
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .padding(start = Space.s)
                        .size(Glyph.m)
                        .graphicsLayer {
                            alpha = chevron
                            // Points up while there is more to open, down once it is open.
                            rotationZ = HALF_TURN * (1f - state.openFraction())
                        },
            )
        }
        // The list is only there while the panel is open, so a collapsed panel offers nothing it hides.
        if (open) {
            Crossfade(ingredients, Modifier.weight(1f), tween(Motion.STATE), label = "panelList") { shown ->
                PanelList(shown, checked, onToggle)
            }
        }
    }
}

/**
 * What the step uses, in two lines: how many, and the lines themselves as the recipe page lists them
 * under a step; once the panel is open, how many are ticked off instead, as the list shows them.
 */
@Composable
private fun Summary(ingredients: StepIngredients, checked: Set<Int>, openFraction: () -> Float) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        if (ingredients.rows.isEmpty()) {
            Text(
                text = stringResource(R.string.cook_step_no_ingredients),
                style = AromaType.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            val count = ingredients.rows.size
            val used = ingredients.indexes.count { it in checked }
            val ticked = MaterialTheme.colorScheme.outline
            Column {
                Text(
                    text =
                        pluralStringResource(
                            if (ingredients.all) R.plurals.cook_all_ingredient_count else R.plurals.cook_step_ingredient_count,
                            count,
                            count,
                        ),
                    style = AromaType.BodyStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box {
                    Text(
                        text = remember(ingredients, checked, ticked) { lines(ingredients, checked, ticked) },
                        style = AromaType.Small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer { alpha = 1f - openFraction() },
                    )
                    Text(
                        text = pluralStringResource(R.plurals.cook_ingredients_used, used, used, count),
                        style = AromaType.Small.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer { alpha = openFraction() },
                    )
                }
            }
        }
    }
}

/** "2 eggs · 300 ml milk · 30 g butter, melted", with those ticked off struck through. */
private fun lines(ingredients: StepIngredients, checked: Set<Int>, ticked: Color): AnnotatedString =
    buildAnnotatedString {
        ingredients.rows.forEachIndexed { position, row ->
            if (position > 0) append(" · ")
            if (ingredients.indexes[position] in checked) {
                withStyle(SpanStyle(color = ticked, textDecoration = TextDecoration.LineThrough)) { append(row.line.plain()) }
            } else {
                append(row.line.plain())
            }
        }
    }

private fun IngredientLine.plain(): String =
    listOf(measureText(), text).filter { it.isNotBlank() }.joinToString(" ") + note?.let { ", $it" }.orEmpty()

@Composable
private fun PanelList(ingredients: StepIngredients, checked: Set<Int>, onToggle: (Int) -> Unit) {
    if (ingredients.rows.isEmpty()) return
    Column(Modifier.fillMaxSize()) {
        Hairline(Modifier.padding(horizontal = CardPadding))
        IngredientList(
            rows = ingredients.rows,
            checked = ingredients.indexes.indices.filterTo(mutableSetOf()) { ingredients.indexes[it] in checked },
            onToggle = { onToggle(ingredients.indexes[it]) },
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = CardPadding, end = CardPadding, bottom = Space.xs),
        )
    }
}

/** How much of the room below the step bar the open panel takes; the step scrolls in the rest. */
private const val EXPANDED_SHARE = 0.6f

/** Between the step and the panel. */
private val PanelGap = Space.s

private const val HALF_TURN = 180f

/** One line of type, measured for its height. */
private const val LINE = "0"

/** Opened or closed with a tap, the panel glides; dragged and let go, it carries on at the finger's speed. */
private val Toggle = tween<Float>(Motion.EXPAND, easing = Motion.Glide)
