package app.aroma.feature.shopping

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
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
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.HeaderAction
import app.aroma.ui.components.LabeledTextField
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.MessageState
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PageEndSpace
import app.aroma.ui.components.PageHeader
import app.aroma.ui.components.PillTextField
import app.aroma.ui.components.Placeholder
import app.aroma.ui.components.ScrollToTopOnReselect
import app.aroma.ui.components.StatusBarScrim
import app.aroma.ui.components.currentLocale
import app.aroma.ui.theme.IconKeyline
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.extras

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
    val onEdit: (ShoppingItem, String) -> Unit = { _, _ -> },
    val onClearChecked: () -> Unit = {},
    val onSelectList: (String) -> Unit = {},
    val onCreateList: (String) -> Unit = {},
    val onRenameList: (String) -> Unit = {},
    val onDeleteList: () -> Unit = {},
    val onRefresh: () -> Unit = {},
)

private enum class ShoppingDialog { Create, Rename, Delete }

@Composable
fun ShoppingScreen(
    state: ShoppingUiState,
    actions: ShoppingActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var dialog by rememberSaveable { mutableStateOf<ShoppingDialog?>(null) }
    // By id, so the dialog survives rotation and closes by itself once its item is gone.
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var shoppingMode by rememberSaveable { mutableStateOf(false) }
    KeepScreenOn(shoppingMode)
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(listState)
    // Grouped here, not in the list's builder, which runs again on every keystroke in the add field.
    val groups = remember(state.list) { state.list?.grouped() }
    Box(modifier.fillMaxSize()) {
        AromaPullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = LocalBottomBarInset.current + PageEndSpace),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    Header(state, actions, shoppingMode, onShoppingMode = { shoppingMode = it }) { dialog = it }
                }
                body(
                    state = state,
                    groups = groups,
                    actions = actions,
                    large = shoppingMode,
                    onEdit = { editingId = it.id },
                    onCreateList = { dialog = ShoppingDialog.Create },
                )
            }
        }
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
        AromaSnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                // 8 dp above the bar; while typing the bar is gone and the keyboard takes its place.
                .padding(bottom = if (WindowInsets.isImeVisible) 16.dp else LocalBottomBarInset.current + 8.dp),
        )
    }
    when (dialog) {
        ShoppingDialog.Create -> {
            NameDialog(
                title = stringResource(R.string.shopping_create_list),
                initial = "",
                onConfirm = {
                    actions.onCreateList(it)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }

        ShoppingDialog.Rename -> {
            NameDialog(
                title = stringResource(R.string.shopping_rename_list),
                initial = state.list?.name.orEmpty(),
                onConfirm = {
                    actions.onRenameList(it)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }

        ShoppingDialog.Delete -> {
            AromaAlertDialog(
                onDismissRequest = { dialog = null },
                title = stringResource(R.string.shopping_delete_list_title),
                text = stringResource(R.string.shopping_delete_list_message, state.list?.name.orEmpty()),
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = {
                    actions.onDeleteList()
                    dialog = null
                },
                dismissLabel = stringResource(R.string.action_cancel),
                destructive = true,
            )
        }

        null -> {}
    }
    state.list?.items?.firstOrNull { it.id == editingId }?.let { item ->
        val locale = currentLocale()
        NameDialog(
            title = stringResource(R.string.shopping_edit_item),
            initial = itemLine(item, locale).toPlainText(),
            onConfirm = {
                actions.onEdit(item, it)
                editingId = null
            },
            onDismiss = { editingId = null },
        )
    }
}

/** Shopping mode keeps the screen awake, so the list stays readable between shelves. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

private fun LazyListScope.body(
    state: ShoppingUiState,
    groups: ShoppingGroups?,
    actions: ShoppingActions,
    large: Boolean,
    onEdit: (ShoppingItem) -> Unit,
    onCreateList: () -> Unit,
) {
    when {
        state.isLoading -> {
            item(key = "loading") { Loading() }
        }

        groups == null && state.error != null -> {
            item(key = "error") {
                ErrorState(state.error, onRetry = actions.onRefresh, modifier = Modifier.fillMaxWidth())
            }
        }

        groups == null -> {
            item(key = "noLists") {
                MessageState(
                    icon = R.drawable.ic_basket,
                    title = stringResource(R.string.shopping_no_lists_title),
                    body = stringResource(R.string.shopping_no_lists_message),
                    actionLabel = stringResource(R.string.shopping_create_list),
                    onAction = onCreateList,
                    primaryAction = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        else -> {
            shoppingSections(groups, state, actions, large, onEdit)
        }
    }
}

@Composable
private fun Header(
    state: ShoppingUiState,
    actions: ShoppingActions,
    shoppingMode: Boolean,
    onShoppingMode: (Boolean) -> Unit,
    openDialog: (ShoppingDialog) -> Unit,
) {
    val list = state.list
    Column {
        PageHeader(title = stringResource(R.string.nav_shopping)) {
            if (list != null) {
                ShoppingModeToggle(shoppingMode, onShoppingMode)
                ListMenu(openDialog)
            }
        }
        when {
            shoppingMode -> {
                Text(
                    text = stringResource(R.string.shopping_mode_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = 8.dp),
                )
            }

            list != null -> {
                ListSwitcher(list, state.lists, actions.onSelectList)
                PillTextField(
                    value = state.newItem,
                    onValueChange = actions.onNewItemChange,
                    placeholder = stringResource(R.string.shopping_add_hint),
                    leadingIcon = R.drawable.ic_add,
                    imeAction = ImeAction.Done,
                    onSubmit = actions.onAddItem,
                    // With the switcher's target below the name, this sets the field about 24 dp under it.
                    modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = 8.dp),
                    trailing = { AddButton(visible = state.newItem.isNotBlank(), onClick = actions.onAddItem) },
                )
            }
        }
        SyncStatus(offline = state.isOffline, pendingChanges = state.pendingChanges)
    }
}

/** That the list is the offline copy, and how many changes wait to be sent, in one quiet block. */
@Composable
private fun SyncStatus(offline: Boolean, pendingChanges: Int) {
    if (!offline && pendingChanges == 0) return
    Column(
        Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (offline) OfflineNotice()
        if (pendingChanges > 0) {
            Text(
                text = pluralStringResource(R.plurals.shopping_pending, pendingChanges, pendingChanges),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Under the notice's text, past its 16 dp glyph and 8 dp gap.
                modifier = Modifier.padding(start = if (offline) 24.dp else 0.dp),
            )
        }
    }
}

/** The cart glyph, drawn in sage with a faint fill while shopping mode is on. */
@Composable
private fun ShoppingModeToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val label = stringResource(if (on) R.string.shopping_mode_off else R.string.shopping_mode_on)
    IconToggleButton(checked = on, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = label }) {
        Crossfade(on, animationSpec = tween(Motion.STATE), label = "shoppingMode") { active ->
            Icon(
                painterResource(if (active) R.drawable.ic_cart_selected else R.drawable.ic_cart),
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ListMenu(openDialog: (ShoppingDialog) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val choose = { dialog: ShoppingDialog ->
        open = false
        openDialog(dialog)
    }
    Box {
        HeaderAction(R.drawable.ic_more_horiz, stringResource(R.string.action_more), onClick = { open = true })
        AromaDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AromaMenuItem(stringResource(R.string.shopping_create_list), R.drawable.ic_add, onClick = { choose(ShoppingDialog.Create) })
            AromaMenuItem(stringResource(R.string.shopping_rename_list), R.drawable.ic_edit, onClick = { choose(ShoppingDialog.Rename) })
            AromaMenuItem(
                stringResource(R.string.shopping_delete_list),
                R.drawable.ic_delete,
                onClick = { choose(ShoppingDialog.Delete) },
                destructive = true,
            )
        }
    }
}

/**
 * The list's name under the page title, on the margin. With more than one list it opens a menu
 * of the others; pressing dims it instead of blooming a ripple.
 */
@Composable
private fun ListSwitcher(current: ShoppingList, lists: List<ShoppingListInfo>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val switchable = lists.size > 1
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.6f else 1f, tween(Motion.PRESS), label = "switcherAlpha")
    Box(
        Modifier
            .padding(horizontal = ScreenMargin)
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = switchable,
                onClickLabel = stringResource(R.string.shopping_switch_list),
                role = Role.Button,
                onClick = { open = true },
            )
            // The name sits 8 dp under the title, and the rest of its 48 dp target lies below it.
            .padding(top = 8.dp),
    ) {
        Row(Modifier.graphicsLayer { this.alpha = alpha }, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = current.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (switchable) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        AromaDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            lists.forEach { info ->
                ListChoice(info.name, chosen = info.id == current.id) {
                    open = false
                    onSelect(info.id)
                }
            }
        }
    }
}

/** A list in the switcher's menu; the current one carries a sage check at the end. */
@Composable
private fun ListChoice(name: String, chosen: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon =
            if (chosen) {
                {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                null
            },
        onClick = onClick,
        modifier = Modifier.semantics { selected = chosen },
    )
}

/** The add field's confirm: a small sage circle 8 dp from the field's end, there only while something is typed. */
@Composable
private fun AddButton(visible: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(48.dp)) {
        AnimatedVisibility(visible, enter = fadeIn(tween(Motion.STATE)), exit = fadeOut(tween(Motion.STATE))) {
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(
                        interactionSource = null,
                        indication = ripple(bounded = false, radius = 16.dp),
                        role = Role.Button,
                        onClick = onClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(extras.cta, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.shopping_add),
                        tint = extras.onCta,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
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

private val PlaceholderWidths = listOf(176.dp, 128.dp, 216.dp, 152.dp, 192.dp, 120.dp)

/** The outline of a category while the list loads: a kicker, then rows of checks and lines. */
@Composable
private fun Loading() {
    Column(Modifier.padding(top = 32.dp)) {
        Placeholder(
            Modifier
                .padding(horizontal = ScreenMargin)
                .size(width = 96.dp, height = 12.dp),
            shape = MaterialTheme.shapes.extraSmall,
        )
        Spacer(Modifier.height(8.dp))
        PlaceholderWidths.forEach { width ->
            Row(
                Modifier
                    .height(56.dp)
                    .padding(horizontal = ScreenMargin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Placeholder(Modifier.size(24.dp), shape = CircleShape)
                Spacer(Modifier.width(IconKeyline - ScreenMargin - 24.dp))
                Placeholder(Modifier.size(width = width, height = 12.dp), shape = MaterialTheme.shapes.extraSmall)
            }
        }
    }
}
