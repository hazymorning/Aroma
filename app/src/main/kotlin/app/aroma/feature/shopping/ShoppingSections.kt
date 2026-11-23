package app.aroma.feature.shopping

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.text.font.FontWeight
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
import app.aroma.ui.components.CardPart
import app.aroma.ui.components.CheckCircle
import app.aroma.ui.components.GroupLabel
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.MessageState
import app.aroma.ui.components.QuietLink
import app.aroma.ui.components.SoftCard
import app.aroma.ui.components.SwipeToRemove
import app.aroma.ui.components.animateRow
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.measureText
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.CardPadding
import app.aroma.ui.theme.ContentEdge
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Motion.Glide
import app.aroma.ui.theme.RowHeight
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.extras
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

// Figtree's own strikeout stroke, in ems: its top sits 0.3 above the baseline and it is 0.05 thick.
private const val STRIKE_CENTER = 0.275f
private const val STRIKE_THICKNESS = 0.05f

/** The check's size in the list, and in shopping mode. */
internal val CheckSize = 24.dp
private val LargeCheckSize = 30.dp

private fun checkSize(large: Boolean): Dp = if (large) LargeCheckSize else CheckSize

/** Where an item's text starts in its card: 16 dp after the check, a little further in shopping mode. */
private fun textKeyline(large: Boolean): Dp = CardPadding + checkSize(large) + if (large) Space.l else Space.m

/** The open items on a card per category, then what is already in the basket. */
internal fun LazyListScope.shoppingSections(
    groups: ShoppingGroups,
    state: ShoppingUiState,
    actions: ShoppingActions,
    large: Boolean,
    onEdit: (ShoppingItem) -> Unit,
) {
    when {
        groups.sections.isEmpty() && groups.checked.isEmpty() -> {
            item(key = "empty") {
                MessageState(
                    icon = R.drawable.ic_basket,
                    title = stringResource(R.string.shopping_empty_title),
                    body = stringResource(R.string.shopping_empty_message),
                    modifier = Modifier.animateRow(this),
                )
            }
            return
        }

        groups.sections.isEmpty() -> {
            item(key = "allDone") {
                // The basket follows, so the message keeps its place under the head.
                MessageState(
                    icon = R.drawable.ic_done_all,
                    title = stringResource(R.string.shopping_done_title),
                    body = stringResource(R.string.shopping_done_message),
                    modifier = Modifier.animateRow(this),
                    standsAlone = false,
                )
            }
        }
    }
    groups.sections.forEach { section ->
        item(key = "label-${section.label?.id ?: "none"}", contentType = "label") {
            GroupLabel(
                text = section.label?.name ?: stringResource(R.string.shopping_other),
                modifier =
                    Modifier
                        .animateRow(this)
                        .padding(top = Space.l),
            )
        }
        itemRows(section.items, state, actions, large, onEdit)
    }
    if (groups.checked.isNotEmpty()) {
        item(key = "basket", contentType = "basket") {
            BasketHeader(count = groups.checked.size, onClear = actions.onClearChecked, modifier = Modifier.animateRow(this))
        }
        itemRows(groups.checked, state, actions, large, onEdit)
    }
}

/**
 * A group of rows on one card, with hairlines between them inset to the text. A row keyed by where it
 * stands leaves its group and fades in at the new one when it is checked off or back, rather than
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
        key = { _, item -> (if (item.checked) "basket-" else "open-") + item.id },
        contentType = { _, _ -> "item" },
    ) { index, item ->
        SoftCard(Modifier.animateRow(this), part = CardPart.of(index, items.size)) {
            if (index > 0) Hairline(Modifier.padding(start = textKeyline(large), end = CardPadding))
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

/** "In the basket · 3" over its card, with the link that clears the basket on the right margin. */
@Composable
private fun BasketHeader(count: Int, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = ContentEdge, end = ScreenMargin, top = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.shopping_in_cart, count),
            style = AromaType.Label,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { heading() },
        )
        QuietLink(stringResource(R.string.shopping_clear_checked), onClear)
    }
}

/**
 * One item: the check on the card's content edge, the text on the keyline after it. Checking draws the check,
 * fades the text and strikes it through; the row leaves for the basket a moment later.
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
    val locale = currentLocale()
    val line = remember(item, locale) { itemLine(item, locale) }
    // The amount in sage, as in a recipe's ingredients; in the basket it is muted with the rest of the line.
    val amount by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        tween(Motion.STATE),
        label = "itemAmount",
    )
    val text = remember(line, amount) { itemText(line, amount) }
    val fade = animateFloatAsState(if (checked) 0.45f else 1f, tween(Motion.STATE), label = "itemFade")
    val strike = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) { strike.animateTo(if (checked) 1f else 0f, tween(Motion.STATE, easing = Glide)) }
    val haptics = LocalHapticFeedback.current
    val stateLabel = stringResource(if (checked) R.string.shopping_checked else R.string.shopping_unchecked)
    val deleteLabel = stringResource(R.string.action_delete)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(extras.card)
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
                }.heightIn(min = if (large || item.recipeNames.isNotEmpty()) RowHeight.twoLines else RowHeight.oneLine)
                .padding(horizontal = CardPadding, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(checked, size = checkSize(large))
        Spacer(Modifier.width(textKeyline(large) - CardPadding - checkSize(large)))
        Column(
            Modifier
                .weight(1f)
                .graphicsLayer { alpha = fade.value },
        ) {
            StruckText(
                text = text,
                style = if (large) AromaType.Large else AromaType.Body,
                progress = { strike.value },
            )
            if (item.recipeNames.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.shopping_for_recipe, item.recipeNames.joinToString(", ")),
                    style = AromaType.Small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Text with a strikethrough drawn from the start of its first line, [progress] of the way to the end of its last. */
@Composable
private fun StruckText(text: AnnotatedString, style: TextStyle, progress: () -> Float) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val ink = MaterialTheme.colorScheme.onSurface
    Text(
        text = text,
        style = style,
        color = ink,
        onTextLayout = { layout = it },
        modifier =
            Modifier.drawWithContent {
                drawContent()
                layout?.let { drawStrike(it, progress(), ink) }
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

private fun itemText(line: IngredientLine, amountColor: Color): AnnotatedString =
    buildAnnotatedString {
        if (line.hasMeasure) {
            withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = amountColor)) { append(measureText(line)) }
            append(' ')
        }
        append(line.text)
        line.note?.let { append(", $it") }
    }
