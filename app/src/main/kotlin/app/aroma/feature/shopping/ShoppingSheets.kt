package app.aroma.feature.shopping

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.aroma.R
import app.aroma.core.model.ShoppingItem
import app.aroma.core.model.ShoppingLabel
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.GroupHead
import app.aroma.ui.components.LabeledTextField
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.Segment
import app.aroma.ui.components.SegmentGap
import app.aroma.ui.components.SheetFooter
import app.aroma.ui.components.Text
import app.aroma.ui.components.animateRow
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.hideThen
import app.aroma.ui.components.rememberFullSheetState
import app.aroma.ui.components.rememberListReorder
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget
import app.aroma.ui.theme.layeredShadow
import java.util.Locale

/**
 * An item to edit: its text, as it reads on the list, and the aisle it belongs in, among the list's
 * [aisles] or none. Saving sends only what changed: new text in place of the old, and the aisle, which
 * Aroma remembers for the next time the same thing is added.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemSheet(item: ShoppingItem, aisles: List<ShoppingLabel>, onSave: (String?, ShoppingLabel?) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    val initial = remember(item, locale) { itemLine(item, locale).toPlainText() }
    var text by rememberSaveable { mutableStateOf(initial) }
    var aisleId by rememberSaveable { mutableStateOf(item.label?.id) }
    val save = {
        if (text.isNotBlank()) {
            onSave(text.trim().takeIf { it != initial.trim() }, aisles.firstOrNull { it.id == aisleId })
            scope.hideThen(sheetState, onDismiss)
        }
    }
    AromaBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.shopping_edit_item), sheetState = sheetState) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.s8),
        ) {
            LabeledTextField(
                value = text,
                onValueChange = { text = it },
                label = null,
                modifier = Modifier.padding(horizontal = PageMargin),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
            if (aisles.isNotEmpty()) {
                GroupHead(stringResource(R.string.shopping_aisle), Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s24))
                FlowRow(
                    Modifier
                        .padding(horizontal = PageMargin)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(SegmentGap),
                ) {
                    aisles.forEach { aisle -> Segment(aisle.name, aisleId == aisle.id, { aisleId = aisle.id }) }
                    Segment(stringResource(R.string.shopping_other), aisleId == null, { aisleId = null })
                }
                Text(
                    text = stringResource(R.string.shopping_aisle_hint),
                    style = AromaTheme.type.bodySmall,
                    color = AromaTheme.colors.inkSecondary,
                    modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s8),
                )
            }
        }
        SheetFooter {
            PrimaryButton(
                stringResource(R.string.action_save),
                onClick = save,
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The list's aisles in the order of the shop, to drag into a new one by their handles, or by the
 * whole row once held a moment. The new order holds as the sheet closes, however it closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AislesSheet(aisles: List<ShoppingLabel>, onOrder: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    val order = remember(aisles) { aisles.toMutableStateList() }
    val list = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val reorder =
        rememberListReorder(
            list = list,
            onMove = { from, to ->
                val fromIndex = order.indexOfFirst { it.id == from }
                val toIndex = order.indexOfFirst { it.id == to }
                if (fromIndex >= 0 && toIndex >= 0) {
                    order.add(toIndex, order.removeAt(fromIndex))
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
            },
            canMove = { _, to -> order.any { it.id == to } },
        )
    val close = {
        val ids = order.map { it.id }
        if (ids != aisles.map { it.id }) onOrder(ids)
        onDismiss()
    }
    val up = stringResource(R.string.shopping_move_up)
    val down = stringResource(R.string.shopping_move_down)
    AromaBottomSheet(
        onDismissRequest = close,
        title = stringResource(R.string.shopping_order_aisles),
        sheetState = sheetState,
        meta = {
            Text(
                stringResource(R.string.shopping_order_aisles_hint),
                style = AromaTheme.type.bodySmall,
                color = AromaTheme.colors.inkSecondary,
            )
        },
    ) {
        LazyColumn(state = list, modifier = Modifier.weight(1f, fill = false)) {
            items(order, key = { it.id }) { aisle ->
                val lifted = reorder.isLifted(aisle.id)
                val lift by animateFloatAsState(if (reorder.held == aisle.id) 1f else 0f, Motion.state(), label = "aisleLift")
                val moves = order.movesOf(aisle, up, down)
                AisleRow(
                    name = aisle.name,
                    lift = { lift },
                    modifier =
                        Modifier
                            .animateRow(this, glides = !lifted)
                            .zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer { translationY = reorder.shift(aisle.id) }
                            .semantics { customActions = moves },
                    onHold = {
                        if (reorder.lift(aisle.id)) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onMove = reorder::drag,
                    onLetGo = reorder::drop,
                )
            }
        }
        SheetFooter {
            PrimaryButton(
                stringResource(R.string.action_done),
                onClick = { scope.hideThen(sheetState, close) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** For TalkBack, moving [aisle] up or down by one place, as dragging it would; [up] and [down] name the moves. */
private fun MutableList<ShoppingLabel>.movesOf(aisle: ShoppingLabel, up: String, down: String): List<CustomAccessibilityAction> {
    val index = indexOf(aisle)
    return buildList {
        if (index > 0) add(CustomAccessibilityAction(up) { add(index - 1, removeAt(index)).let { true } })
        if (index < lastIndex) add(CustomAccessibilityAction(down) { add(index + 1, removeAt(index)).let { true } })
    }
}

/**
 * An aisle on the sheet that orders them: its name, and the handle to drag it by at the end. Taken
 * hold of, it rises off the sheet with the soft shadow of what floats, [lift] of the way.
 */
@Composable
private fun AisleRow(
    name: String,
    lift: () -> Float,
    onHold: () -> Unit,
    onMove: (Float) -> Unit,
    onLetGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hold by rememberUpdatedState(onHold)
    val move by rememberUpdatedState(onMove)
    val letGo by rememberUpdatedState(onLetGo)
    val shape = AromaTheme.shapes.box
    val handle = stringResource(R.string.shopping_drag_hint)
    Row(
        modifier =
            modifier
                .padding(horizontal = PageMargin - Space.s12)
                .layeredShadow(AromaTheme.shadows.menu, shape) { lift() }
                .clip(shape)
                .background(AromaTheme.colors.bar)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { hold() },
                        onDrag = { change, amount ->
                            change.consume()
                            move(amount.y)
                        },
                        onDragEnd = { letGo() },
                        onDragCancel = { letGo() },
                    )
                }.fillMaxWidth()
                .heightIn(min = AisleHeight)
                .padding(start = Space.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = AromaTheme.type.body, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Space.s16))
        // The handle takes hold at once, without the wait a row asks for.
        Box(
            Modifier
                .size(TouchTarget)
                .semantics { contentDescription = handle }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { hold() },
                        onDrag = { change, amount ->
                            change.consume()
                            move(amount.y)
                        },
                        onDragEnd = { letGo() },
                        onDragCancel = { letGo() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_drag_handle),
                contentDescription = null,
                tint = AromaTheme.colors.inkSecondary,
                modifier = Modifier.size(Glyph.control),
            )
        }
    }
}

/** An aisle's row on its sheet, as tall as a sheet's choices. */
private val AisleHeight = 56.dp

/**
 * The list as text to send: its name, then each aisle with what is still open in it, an item a line.
 * What is done stays out, since it is bought.
 */
internal fun shareText(name: String, groups: ShoppingGroups, other: String, locale: Locale): String =
    buildString {
        append(name)
        groups.sections.forEach { section ->
            append("\n\n").append(section.label?.name ?: other)
            section.items.forEach { item -> append("\n• ").append(itemLine(item, locale).toPlainText()) }
        }
    }
