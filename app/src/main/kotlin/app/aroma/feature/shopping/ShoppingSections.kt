package app.aroma.feature.shopping

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.format.IngredientFormatter
import app.aroma.core.format.IngredientLine
import app.aroma.core.model.ShoppingItem
import app.aroma.core.model.ShoppingLabel
import app.aroma.core.model.ShoppingList
import app.aroma.ui.components.BoxPart
import app.aroma.ui.components.CheckCircle
import app.aroma.ui.components.CheckSize
import app.aroma.ui.components.EmptyState
import app.aroma.ui.components.GroupHead
import app.aroma.ui.components.RollingText
import app.aroma.ui.components.Separator
import app.aroma.ui.components.SwipeToRemove
import app.aroma.ui.components.Text
import app.aroma.ui.components.TextAction
import app.aroma.ui.components.TonalBoxPart
import app.aroma.ui.components.animateRow
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.styledMeasure
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.BoxPadding
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget
import java.util.Locale

/** Open items grouped by label, in the order chosen in Mealie; unlabeled items come last. */
data class ShoppingSection(val label: ShoppingLabel?, val items: List<ShoppingItem>)

data class ShoppingGroups(val sections: List<ShoppingSection>, val checked: List<ShoppingItem>)

fun ShoppingList.grouped(): ShoppingGroups {
    val open = items.filterNot { it.checked }
    val order = labelOrder.withIndex().associate { (index, id) -> id to index }
    val sections =
        open
            .groupBy { it.label?.id }
            .map { (_, groupItems) -> ShoppingSection(groupItems.first().label, groupItems.sortedBy { it.position }) }
            .sortedWith(
                compareBy<ShoppingSection> { it.label == null }
                    .thenBy { section -> section.label?.let { order[it.id] } ?: Int.MAX_VALUE }
                    .thenBy { it.label?.name?.lowercase() },
            )
    val checked = items.filter { it.checked }.sortedBy { it.position }
    return ShoppingGroups(sections, checked)
}

// Instrument Sans's own strikeout stroke, in ems: its top sits 0.3 above the baseline and it is 0.04 thick.
private const val STRIKE_CENTER = 0.28f
private const val STRIKE_THICKNESS = 0.04f

/** Where an item's text starts in its box: after the tick and 12 dp. Separators start there too. */
private val TextEdge: Dp = BoxPadding + CheckSize + Space.s12

/**
 * The open items, a box per aisle under the aisle's name, then what is done, folded under "Done (n)"
 * until it is opened. A list without items says so in one line, with the way to fill it.
 */
internal fun LazyListScope.shoppingSections(
    groups: ShoppingGroups,
    state: ShoppingUiState,
    actions: ShoppingActions,
    large: Boolean,
    showDone: Boolean,
    onShowDone: (Boolean) -> Unit,
    onEdit: (ShoppingItem) -> Unit,
) {
    if (groups.sections.isEmpty()) {
        item(key = "empty") {
            if (groups.checked.isEmpty()) {
                EmptyState(
                    text = stringResource(R.string.shopping_empty_title),
                    actionLabel = stringResource(R.string.shopping_add_from_plan),
                    onAction = actions.onAddFromPlan,
                    modifier = Modifier.animateRow(this),
                )
            } else {
                EmptyState(text = stringResource(R.string.shopping_done_title), modifier = Modifier.animateRow(this))
            }
        }
    }
    groups.sections.forEachIndexed { index, section ->
        item(key = "label-${section.label?.id ?: "none"}", contentType = "label") {
            GroupHead(
                text = section.label?.name ?: stringResource(R.string.shopping_other),
                modifier =
                    Modifier
                        .animateRow(this)
                        .padding(start = PageMargin, end = PageMargin, top = if (index == 0) 0.dp else Space.s24, bottom = Space.s12),
            )
        }
        itemRows(section.items, state, actions, large, onEdit)
    }
    if (groups.checked.isNotEmpty()) {
        item(key = "done", contentType = "done") {
            DoneHead(
                count = groups.checked.size,
                open = showDone,
                onToggle = { onShowDone(!showDone) },
                onClear = actions.onClearChecked,
                modifier = Modifier.animateRow(this),
            )
        }
        if (showDone) itemRows(groups.checked, state, actions, large, onEdit)
    }
}

/**
 * The rows of one box, a separator between each two, inset to their text. A row keyed by where it
 * stands leaves its group and fades in at the new one when it is ticked off or back, rather than
 * sliding across the list over the rows between.
 */
private fun LazyListScope.itemRows(
    items: List<ShoppingItem>,
    state: ShoppingUiState,
    actions: ShoppingActions,
    large: Boolean,
    onEdit: (ShoppingItem) -> Unit,
) {
    itemsIndexed(
        items,
        key = { _, item -> (if (item.checked) "done-" else "open-") + item.id },
        contentType = { _, _ -> "item" },
    ) { index, item ->
        TonalBoxPart(
            part = BoxPart.of(index, items.size),
            modifier =
                Modifier
                    .animateRow(this)
                    .padding(horizontal = PageMargin),
        ) {
            if (index > 0) Separator(start = TextEdge)
            SwipeToRemove(onRemove = { actions.onDelete(item) }) {
                ItemRow(
                    item = item,
                    checked = state.isChecked(item),
                    large = large,
                    onToggle = { actions.onToggle(item) },
                    onEdit = { onEdit(item) },
                    onDelete = { actions.onDelete(item) },
                )
            }
        }
    }
}

/**
 * "Done (3)": what is in the basket, folded away until a tap opens it; the way to clear it stands
 * at its end, fading in as it opens. The count rolls as items go in and out of the basket.
 */
@Composable
private fun DoneHead(
    count: Int,
    open: Boolean,
    onToggle: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val turn by animateFloatAsState(if (open) HALF_TURN else 0f, Motion.state(), label = "doneChevron")
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = PageMargin, end = PageMargin, top = Space.s24, bottom = if (open) Space.s12 else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = TouchTarget)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RollingText(
                stringResource(R.string.shopping_done_group, count),
                style = AromaTheme.type.label,
                color = AromaTheme.colors.inkSecondary,
            )
            Spacer(Modifier.width(Space.s4))
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = AromaTheme.colors.inkSecondary,
                modifier =
                    Modifier
                        .size(Glyph.control)
                        .rotate(turn),
            )
        }
        AnimatedVisibility(visible = open, enter = fadeIn(Motion.edge()), exit = fadeOut(Motion.edge())) {
            TextAction(stringResource(R.string.shopping_clear_checked), onClear)
        }
    }
}

private const val HALF_TURN = 180f

/**
 * One item: the tick, then the amount in sage and the item in body text, and the recipes it is for
 * under it. Ticking draws the tick, fades the line to tertiary ink and strikes it through; the row
 * leaves for "Done" a moment later. A long press edits it, a swipe removes it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemRow(
    item: ShoppingItem,
    checked: Boolean,
    large: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AromaTheme.colors
    val locale = currentLocale()
    val line = remember(item, locale) { itemLine(item, locale) }
    val amount by animateColorAsState(if (checked) colors.inkTertiary else colors.accent, Motion.state(), label = "itemAmount")
    val ink by animateColorAsState(if (checked) colors.inkTertiary else colors.ink, Motion.state(), label = "itemInk")
    val text = remember(line, amount) { itemText(line, amount) }
    val strike = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) { strike.animateTo(if (checked) 1f else 0f, Motion.state()) }
    val haptics = LocalHapticFeedback.current
    val stateLabel = stringResource(if (checked) R.string.shopping_checked else R.string.shopping_unchecked)
    val deleteLabel = stringResource(R.string.action_delete)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // Opaque, so the swipe's field only shows where the row has moved away.
                .background(AromaTheme.colors.surface)
                .combinedClickable(
                    role = Role.Checkbox,
                    onLongClickLabel = stringResource(R.string.shopping_edit_item),
                    onLongClick = onEdit,
                    onClick = {
                        haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                        onToggle()
                    },
                ).semantics(mergeDescendants = true) {
                    toggleableState = ToggleableState(checked)
                    stateDescription = stateLabel
                    customActions =
                        listOf(
                            CustomAccessibilityAction(deleteLabel) {
                                onDelete()
                                true
                            },
                        )
                }.heightIn(min = RowMin)
                .padding(horizontal = BoxPadding, vertical = Space.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(checked)
        Spacer(Modifier.width(Space.s12))
        Column(Modifier.weight(1f)) {
            StruckText(
                text = text,
                style = if (large) AromaTheme.type.cookStep else AromaTheme.type.body,
                color = ink,
                progress = { strike.value },
            )
            if (item.recipeNames.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.shopping_for_recipe, item.recipeNames.joinToString(", ")),
                    style = AromaTheme.type.bodySmall,
                    color = if (checked) colors.inkTertiary else colors.inkSecondary,
                )
            }
        }
    }
}

/** A row's least height: as tall as a target. */
private val RowMin = 56.dp

/** Text with a strikethrough drawn from the start of its first line, [progress] of the way to the end of its last. */
@Composable
private fun StruckText(text: AnnotatedString, style: TextStyle, color: Color, progress: () -> Float) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = style,
        color = color,
        onTextLayout = { layout = it },
        modifier =
            Modifier.drawWithContent {
                drawContent()
                layout?.let { drawStrike(it, progress(), color) }
            },
    )
}

private fun DrawScope.drawStrike(layout: TextLayoutResult, progress: Float, color: Color) {
    if (progress <= 0f) return
    val em =
        layout.layoutInput.style.fontSize
            .toPx()
    val lines = 0 until layout.lineCount
    var remaining = progress * lines.sumOf { (layout.getLineRight(it) - layout.getLineLeft(it)).toDouble() }.toFloat()
    for (line in lines) {
        if (remaining <= 0f) return
        val left = layout.getLineLeft(line)
        val right = layout.getLineRight(line)
        val length = minOf(remaining, right - left)
        val y = layout.getLineBaseline(line) - em * STRIKE_CENTER
        val rtl = layout.getParagraphDirection(layout.getLineStart(line)) == ResolvedTextDirection.Rtl
        val start = if (rtl) right else left
        val end = if (rtl) right - length else left + length
        drawLine(color, Offset(start, y), Offset(end, y), strokeWidth = em * STRIKE_THICKNESS)
        remaining -= length
    }
}

internal fun itemLine(item: ShoppingItem, locale: Locale): IngredientLine =
    if (item.unit != null || item.food != null) {
        IngredientFormatter.parsed(item.quantity, item.unit, item.food, item.note, 1.0, locale)
    } else {
        IngredientFormatter.freeText(item.note, if (item.quantity > 1) item.quantity else 0.0, 1.0, locale)
    }

/** "300 ml milk": the amount in sage with tabular figures, then the item. */
private fun itemText(line: IngredientLine, amountColor: Color): AnnotatedString =
    buildAnnotatedString {
        if (line.hasMeasure) {
            withStyle(SpanStyle(color = amountColor, fontFeatureSettings = "tnum")) { append(styledMeasure(line)) }
            append(' ')
        }
        append(line.text)
        line.note?.let { append(", $it") }
    }
