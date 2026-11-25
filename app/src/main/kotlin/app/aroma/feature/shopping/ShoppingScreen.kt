package app.aroma.feature.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.ShoppingItem
import app.aroma.core.model.ShoppingList
import app.aroma.core.model.ShoppingListInfo
import app.aroma.data.AromaError
import app.aroma.ui.components.AromaAlertDialog
import app.aroma.ui.components.AromaDropdownMenu
import app.aroma.ui.components.AromaMenuItem
import app.aroma.ui.components.AromaPullToRefreshBox
import app.aroma.ui.components.AromaSnackbarHost
import app.aroma.ui.components.CheckSize
import app.aroma.ui.components.ChoiceSheet
import app.aroma.ui.components.EmptyState
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.HeaderState
import app.aroma.ui.components.IconButton
import app.aroma.ui.components.KeepScreenOn
import app.aroma.ui.components.LabeledTextField
import app.aroma.ui.components.LargeHeader
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.MetaLine
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PillField
import app.aroma.ui.components.SEPARATOR
import app.aroma.ui.components.ScrollToTopOnReselect
import app.aroma.ui.components.Skeleton
import app.aroma.ui.components.SkeletonLine
import app.aroma.ui.components.Subtitle
import app.aroma.ui.components.TonalBox
import app.aroma.ui.components.TopBar
import app.aroma.ui.components.animateOpenRow
import app.aroma.ui.components.animateRow
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.rememberHeaderState
import app.aroma.ui.components.requestWhenAttached
import app.aroma.ui.components.scrolledBeneath
import app.aroma.ui.components.skeletonRow
import app.aroma.ui.theme.EndSpace
import app.aroma.ui.theme.HeaderGap
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.Space
import kotlinx.coroutines.flow.first

data class ShoppingUiState(
    val lists: List<ShoppingListInfo> = emptyList(),
    val list: ShoppingList? = null,
    val newItem: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val pendingChanges: Int = 0,
    val error: AromaError? = null,
    /** Checks already shown on rows that have not moved yet, by item id. */
    val settlingChecks: Map<String, Boolean> = emptyMap(),
) {
    fun isChecked(item: ShoppingItem): Boolean = settlingChecks[item.id] ?: item.checked
}

data class ShoppingActions(
    val onNewItemChange: (String) -> Unit = {},
    val onAddItem: () -> Unit = {},
    val onToggle: (ShoppingItem) -> Unit = {},
    val onDelete: (ShoppingItem) -> Unit = {},
    val onRenameItem: (ShoppingItem, String) -> Unit = { _, _ -> },
    val onClearChecked: () -> Unit = {},
    val onSelectList: (String) -> Unit = {},
    val onCreateList: (String) -> Unit = {},
    val onRenameList: (String) -> Unit = {},
    val onDeleteList: () -> Unit = {},
    val onAddFromPlan: () -> Unit = {},
    val onRefresh: () -> Unit = {},
)

private enum class ShoppingDialog { Create, Rename, Delete, Lists }

/**
 * The shopping list: its open items by aisle, a box per aisle, and what is in the basket folded
 * away under "Done". The head adds an item and opens the list's menu, where shopping mode, the
 * other lists and the list's own changes are. With [focusAdd], ready to add: the field shown, the
 * cursor in it and the keyboard up.
 */
@Composable
fun ShoppingScreen(
    state: ShoppingUiState,
    actions: ShoppingActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    focusAdd: Boolean = false,
    onFocusAddConsume: () -> Unit = {},
) {
    var dialog by rememberSaveable { mutableStateOf<ShoppingDialog?>(null) }
    // By id, so the dialog survives rotation and closes by itself once its item is gone.
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var shoppingMode by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    // Shopping mode keeps the screen awake, so the list stays readable between shelves.
    KeepScreenOn(shoppingMode)
    // Another list, or shopping mode with its larger rows, is another page rather than an edit of this one.
    val listState = key(state.list?.id, shoppingMode) { rememberLazyListState() }
    ScrollToTopOnReselect(listState)
    val header = rememberHeaderState()
    val addFocus = remember { FocusRequester() }
    val current by rememberUpdatedState(state)
    val consumeFocusAdd by rememberUpdatedState(onFocusAddConsume)
    LaunchedEffect(focusAdd) {
        if (focusAdd) {
            shoppingMode = false
            adding = true
            // The field shows once a list does, as an item of the list under its head, laid out a frame later.
            snapshotFlow { current.list != null }.first { it }
            listState.scrollToItem(0)
            addFocus.requestWhenAttached()
            // Only now: consuming changes the key, which would end this effect.
            consumeFocusAdd()
        }
    }
    LaunchedEffect(adding) {
        if (adding) addFocus.requestWhenAttached()
    }
    // Grouped here, not in the list's builder, which runs again on every keystroke in the add field.
    val groups = remember(state.list) { state.list?.grouped() }
    val title = stringResource(R.string.nav_shopping)
    val headActions: @Composable () -> Unit = {
        if (state.list != null) {
            IconButton(R.drawable.ic_add, stringResource(R.string.shopping_add_hint), onClick = { adding = !adding })
            ListMenu(
                shoppingMode = shoppingMode,
                canSwitch = state.lists.size > 1,
                canClear = groups?.checked?.isNotEmpty() == true,
                onShoppingMode = { shoppingMode = !shoppingMode },
                onClear = actions.onClearChecked,
                openDialog = { dialog = it },
            )
        }
    }
    val barInset = LocalBottomBarInset.current
    Box(modifier.fillMaxSize()) {
        AromaPullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = barInset + EndSpace),
                modifier =
                    Modifier
                        .fillMaxSize()
                        // While typing, the list ends above the keyboard, which covers the bar, so all of it
                        // can still be brought into view; its end follows the keyboard as it slides.
                        .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets(bottom = barInset))),
            ) {
                item(key = "header") {
                    Header(state = state, groups = groups, header = header, shoppingMode = shoppingMode, headActions = headActions)
                }
                // The field and the quiet lines under the head are rows of their own, so the list glides
                // aside as they come and back as they go.
                if (state.list != null && (adding || state.newItem.isNotEmpty()) && !shoppingMode) {
                    item(key = "add") { AddField(state, addFocus, actions, Modifier.animateRow(this)) }
                }
                if (state.isOffline || state.pendingChanges > 0) {
                    item(key = "sync") { SyncStatus(state.isOffline, state.pendingChanges, Modifier.animateRow(this)) }
                }
                item(key = "headGap") { Spacer(Modifier.animateRow(this).height(HeaderGap)) }
                body(
                    state = state,
                    groups = groups,
                    actions = actions,
                    large = shoppingMode,
                    showDone = showDone,
                    onShowDone = { showDone = it },
                    onEdit = { editingId = it.id },
                    onCreateList = { dialog = ShoppingDialog.Create },
                )
            }
        }
        TopBar(
            title = title,
            collapsed = header.collapsed,
            raised = listState.canScrollBackward,
            scrolled = listState::scrolledBeneath,
            actions = { headActions() },
        )
        AromaSnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                // 8 dp above the bar; while typing the bar is gone and the keyboard takes its place.
                .padding(bottom = if (WindowInsets.isImeVisible) Space.s16 else LocalBottomBarInset.current + Space.s8),
        )
    }
    ShoppingDialogs(
        dialog = dialog,
        state = state,
        actions = actions,
        onDismiss = { dialog = null },
    )
    state.list?.items?.firstOrNull { it.id == editingId }?.let { item ->
        val locale = currentLocale()
        NameDialog(
            title = stringResource(R.string.shopping_edit_item),
            initial = itemLine(item, locale).toPlainText(),
            onConfirm = {
                actions.onRenameItem(item, it)
                editingId = null
            },
            onDismiss = { editingId = null },
        )
    }
}

@Composable
private fun ShoppingDialogs(dialog: ShoppingDialog?, state: ShoppingUiState, actions: ShoppingActions, onDismiss: () -> Unit) {
    when (dialog) {
        ShoppingDialog.Create -> {
            NameDialog(
                title = stringResource(R.string.shopping_create_list),
                initial = "",
                onConfirm = {
                    actions.onCreateList(it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        ShoppingDialog.Rename -> {
            NameDialog(
                title = stringResource(R.string.shopping_rename_list),
                initial = state.list?.name.orEmpty(),
                onConfirm = {
                    actions.onRenameList(it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        ShoppingDialog.Delete -> {
            AromaAlertDialog(
                onDismissRequest = onDismiss,
                title = stringResource(R.string.shopping_delete_list_title),
                text = stringResource(R.string.shopping_delete_list_message, state.list?.name.orEmpty()),
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = {
                    actions.onDeleteList()
                    onDismiss()
                },
                dismissLabel = stringResource(R.string.action_cancel),
                destructive = true,
            )
        }

        ShoppingDialog.Lists -> {
            ChoiceSheet(
                title = stringResource(R.string.shopping_switch_list),
                options = state.lists,
                selected = state.lists.firstOrNull { it.id == state.list?.id },
                optionLabel = { it.name },
                onSelect = { actions.onSelectList(it.id) },
                onDismiss = onDismiss,
            )
        }

        null -> {}
    }
}

private fun LazyListScope.body(
    state: ShoppingUiState,
    groups: ShoppingGroups?,
    actions: ShoppingActions,
    large: Boolean,
    showDone: Boolean,
    onShowDone: (Boolean) -> Unit,
    onEdit: (ShoppingItem) -> Unit,
    onCreateList: () -> Unit,
) {
    when {
        state.isLoading -> {
            item(key = "loading") { Loading(Modifier.skeletonRow(this)) }
        }

        groups == null && state.error != null -> {
            item(key = "error") { ErrorState(state.error, onRetry = actions.onRefresh, modifier = Modifier.animateOpenRow(this)) }
        }

        groups == null -> {
            item(key = "noLists") {
                EmptyState(
                    text = stringResource(R.string.shopping_no_lists_title),
                    actionLabel = stringResource(R.string.shopping_create_list),
                    onAction = onCreateList,
                    modifier = Modifier.animateOpenRow(this),
                )
            }
        }

        else -> {
            shoppingSections(groups, state, actions, large, showDone, onShowDone, onEdit)
        }
    }
}

@Composable
private fun Header(
    state: ShoppingUiState,
    groups: ShoppingGroups?,
    header: HeaderState,
    shoppingMode: Boolean,
    headActions: @Composable () -> Unit,
) {
    val list = state.list
    val open = groups?.sections?.sumOf { it.items.size } ?: 0
    LargeHeader(
        title = stringResource(R.string.nav_shopping),
        state = header,
        subtitle =
            list?.let {
                {
                    val count = pluralStringResource(R.plurals.shopping_open, open, open)
                    Subtitle(
                        when {
                            shoppingMode -> stringResource(R.string.shopping_mode_hint)
                            state.lists.size > 1 -> "${list.name}$SEPARATOR$count"
                            else -> count
                        },
                    )
                }
            },
        actions = { headActions() },
    )
}

/** The field that adds an item, 16 dp under the head; its button adds what was typed. */
@Composable
private fun AddField(state: ShoppingUiState, addFocus: FocusRequester, actions: ShoppingActions, modifier: Modifier = Modifier) {
    PillField(
        value = state.newItem,
        onValueChange = actions.onNewItemChange,
        placeholder = stringResource(R.string.shopping_add_hint),
        leadingIcon = R.drawable.ic_add,
        imeAction = ImeAction.Done,
        onSubmit = actions.onAddItem,
        focusRequester = addFocus,
        capitalization = KeyboardCapitalization.Sentences,
        modifier = modifier.padding(start = PageMargin, end = PageMargin, top = Space.s16),
        trailing =
            if (state.newItem.isNotBlank()) {
                { IconButton(R.drawable.ic_check, stringResource(R.string.shopping_add), actions.onAddItem) }
            } else {
                null
            },
    )
}

/** That the list is the offline copy, and how many changes wait to be sent, in quiet lines. */
@Composable
private fun SyncStatus(offline: Boolean, pendingChanges: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(start = PageMargin, end = PageMargin, top = Space.s16),
        verticalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        if (offline) OfflineNotice()
        if (pendingChanges > 0) MetaLine(listOf(pluralStringResource(R.plurals.shopping_pending, pendingChanges, pendingChanges)))
    }
}

/** The list's menu: shopping mode, the other lists, the list itself, and clearing what is done. */
@Composable
private fun ListMenu(
    shoppingMode: Boolean,
    canSwitch: Boolean,
    canClear: Boolean,
    onShoppingMode: () -> Unit,
    onClear: () -> Unit,
    openDialog: (ShoppingDialog) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val choose = { action: () -> Unit ->
        open = false
        action()
    }
    Box {
        IconButton(R.drawable.ic_more_horiz, stringResource(R.string.action_more), onClick = { open = true })
        AromaDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AromaMenuItem(
                stringResource(if (shoppingMode) R.string.shopping_mode_off else R.string.shopping_mode_on),
                R.drawable.ic_cart,
                onClick = { choose(onShoppingMode) },
            )
            if (canSwitch) {
                AromaMenuItem(stringResource(R.string.shopping_switch_list), R.drawable.ic_basket, onClick = {
                    choose { openDialog(ShoppingDialog.Lists) }
                })
            }
            if (canClear) AromaMenuItem(stringResource(R.string.shopping_clear_done), R.drawable.ic_done_all, onClick = { choose(onClear) })
            AromaMenuItem(
                stringResource(R.string.shopping_create_list),
                R.drawable.ic_add,
                onClick = { choose { openDialog(ShoppingDialog.Create) } },
            )
            AromaMenuItem(
                stringResource(R.string.shopping_rename_list),
                R.drawable.ic_edit,
                onClick = { choose { openDialog(ShoppingDialog.Rename) } },
            )
            AromaMenuItem(
                stringResource(R.string.shopping_delete_list),
                R.drawable.ic_delete,
                onClick = { choose { openDialog(ShoppingDialog.Delete) } },
                destructive = true,
            )
        }
    }
}

/** A name for a list or the text of an item, typed into one field. Saving needs something to save. */
@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val confirm = { if (text.isNotBlank()) onConfirm(text.trim()) }
    AromaAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmLabel = stringResource(R.string.action_save),
        onConfirm = confirm,
        dismissLabel = stringResource(R.string.action_cancel),
        confirmEnabled = text.isNotBlank(),
        content = {
            LabeledTextField(
                value = text,
                onValueChange = { text = it },
                label = null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
            )
        },
    )
}

/** How long the skeleton's lines are: an aisle's name, then its items. */
private val HeadWidth = 96.dp
private val LineWidths = listOf(176.dp, 128.dp, 216.dp, 152.dp, 192.dp)

/** The skeleton of an aisle while the list loads: its head, then a box of checks and lines. */
@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = PageMargin)) {
        SkeletonLine(Modifier.width(HeadWidth))
        Spacer(Modifier.height(Space.s12))
        TonalBox {
            LineWidths.forEachIndexed { index, width ->
                Row(
                    Modifier.padding(top = if (index == 0) 0.dp else Space.s24),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Skeleton(Modifier.size(CheckSize), shape = CircleShape)
                    Spacer(Modifier.width(Space.s12))
                    SkeletonLine(Modifier.width(width))
                }
            }
        }
    }
}
