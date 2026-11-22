package app.aroma.feature.shopping

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.format.IngredientFormatter
import app.aroma.core.format.IngredientLine
import app.aroma.core.model.ShoppingItem
import app.aroma.core.model.ShoppingList
import app.aroma.core.model.ShoppingListInfo
import app.aroma.data.AromaError
import app.aroma.ui.components.AromaSnackbarHost
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.MessageState
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PageEndSpace
import app.aroma.ui.components.PillTextField
import app.aroma.ui.components.Placeholder
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.measureText
import app.aroma.ui.components.titleRes
import app.aroma.ui.theme.Sans
import java.util.Locale

data class ShoppingUiState(
    val lists: List<ShoppingListInfo> = emptyList(),
    val list: ShoppingList? = null,
    val newItem: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val pendingChanges: Int = 0,
    val error: AromaError? = null,
    val notice: AromaError? = null,
    val lastDeleted: ShoppingItem? = null,
)

data class ShoppingActions(
    val onNewItemChange: (String) -> Unit = {},
    val onAddItem: () -> Unit = {},
    val onToggle: (ShoppingItem) -> Unit = {},
    val onDelete: (ShoppingItem) -> Unit = {},
    val onUndoDelete: () -> Unit = {},
    val onDeleteHandled: () -> Unit = {},
    val onEdit: (ShoppingItem, String) -> Unit = { _, _ -> },
    val onClearChecked: () -> Unit = {},
    val onSelectList: (String) -> Unit = {},
    val onCreateList: (String) -> Unit = {},
    val onRenameList: (String) -> Unit = {},
    val onDeleteList: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onNoticeShown: () -> Unit = {},
)

private enum class ShoppingDialog { Create, Rename, Delete }

@Composable
fun ShoppingScreen(state: ShoppingUiState, actions: ShoppingActions, modifier: Modifier = Modifier) {
    var dialog by rememberSaveable { mutableStateOf<ShoppingDialog?>(null) }
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }
    var shoppingMode by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(shoppingMode) {
        view.keepScreenOn = shoppingMode
        onDispose { view.keepScreenOn = false }
    }
    val snackbar = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.shopping_item_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val noticeText = state.notice?.let { stringResource(it.titleRes()) }
    LaunchedEffect(state.notice) {
        if (noticeText != null) {
            snackbar.showSnackbar(noticeText)
            actions.onNoticeShown()
        }
    }
    LaunchedEffect(state.lastDeleted) {
        if (state.lastDeleted != null) {
            val result = snackbar.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) actions.onUndoDelete() else actions.onDeleteHandled()
        }
    }
    Box(modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = LocalBottomBarInset.current + PageEndSpace),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    Header(state, actions, shoppingMode, onShoppingMode = { shoppingMode = it }) { dialog = it }
                }
                val list = state.list
                when {
                    state.isLoading -> {
                        item(key = "loading") { Loading() }
                    }

                    list == null && state.error != null -> {
                        item(key = "error") {
                            ErrorState(state.error, onRetry = actions.onRefresh, modifier = Modifier.fillMaxWidth())
                        }
                    }

                    list == null -> {
                        item(key = "noLists") {
                            MessageState(
                                icon = R.drawable.ic_shopping_basket,
                                title = stringResource(R.string.shopping_no_lists_title),
                                body = stringResource(R.string.shopping_no_lists_message),
                                actionLabel = stringResource(R.string.shopping_create_list),
                                onAction = { dialog = ShoppingDialog.Create },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    else -> {
                        listContent(list, actions, large = shoppingMode, onEdit = { editing = it })
                    }
                }
            }
        }
        AromaSnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = LocalBottomBarInset.current),
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
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.shopping_delete_list_title)) },
                text = { Text(stringResource(R.string.shopping_delete_list_message, state.list?.name.orEmpty())) },
                confirmButton = {
                    TextButton(onClick = {
                        actions.onDeleteList()
                        dialog = null
                    }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }

        null -> {}
    }
    editing?.let { item ->
        val locale = currentLocale()
        NameDialog(
            title = stringResource(R.string.shopping_edit_item),
            initial = itemLine(item, locale).toPlainText(),
            onConfirm = {
                actions.onEdit(item, it)
                editing = null
            },
            onDismiss = { editing = null },
        )
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
    var listMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 20.dp),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_shopping), style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
            if (state.list != null) {
                FilledTonalIconToggleButton(checked = shoppingMode, onCheckedChange = onShoppingMode) {
                    Icon(
                        painterResource(R.drawable.ic_shopping_basket),
                        contentDescription = stringResource(if (shoppingMode) R.string.shopping_mode_off else R.string.shopping_mode_on),
                    )
                }
                Box {
                    IconButton(onClick = { moreMenu = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.shopping_create_list)) }, onClick = {
                            moreMenu = false
                            openDialog(ShoppingDialog.Create)
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.shopping_rename_list)) }, onClick = {
                            moreMenu = false
                            openDialog(ShoppingDialog.Rename)
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.shopping_delete_list)) }, onClick = {
                            moreMenu = false
                            openDialog(ShoppingDialog.Delete)
                        })
                    }
                }
            }
        }
        if (shoppingMode) {
            Text(
                text = stringResource(R.string.shopping_mode_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        state.list?.takeUnless { shoppingMode }?.let { list ->
            Box(Modifier.padding(start = 8.dp)) {
                TextButton(onClick = { if (state.lists.size > 1) listMenu = true }) {
                    Text(list.name, style = MaterialTheme.typography.titleMedium)
                    if (state.lists.size > 1) {
                        Icon(painterResource(R.drawable.ic_expand_more), contentDescription = stringResource(R.string.shopping_switch_list))
                    }
                }
                DropdownMenu(expanded = listMenu, onDismissRequest = { listMenu = false }) {
                    state.lists.forEach { info ->
                        DropdownMenuItem(
                            text = { Text(info.name) },
                            trailingIcon =
                                if (info.id == list.id) {
                                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                                } else {
                                    null
                                },
                            onClick = {
                                listMenu = false
                                actions.onSelectList(info.id)
                            },
                        )
                    }
                }
            }
            PillTextField(
                value = state.newItem,
                onValueChange = actions.onNewItemChange,
                placeholder = stringResource(R.string.shopping_add_hint),
                leadingIcon = R.drawable.ic_add,
                imeAction = ImeAction.Done,
                onSubmit = actions.onAddItem,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
                trailing =
                    if (state.newItem.isNotBlank()) {
                        {
                            FilledIconButton(onClick = actions.onAddItem) {
                                Icon(painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.shopping_add))
                            }
                        }
                    } else {
                        null
                    },
            )
        }
        if (state.isOffline) {
            OfflineNotice(Modifier.padding(start = 20.dp, top = 12.dp))
        }
        if (state.pendingChanges > 0) {
            Text(
                text = pluralStringResource(R.plurals.shopping_pending, state.pendingChanges, state.pendingChanges),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.listContent(
    list: ShoppingList,
    actions: ShoppingActions,
    large: Boolean,
    onEdit: (ShoppingItem) -> Unit,
) {
    val groups = list.grouped()
    if (groups.sections.isEmpty() && groups.checked.isEmpty()) {
        item(key = "empty") {
            MessageState(
                icon = R.drawable.ic_shopping_basket,
                title = stringResource(R.string.shopping_empty_title),
                body = stringResource(R.string.shopping_empty_message),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }
    if (groups.sections.isEmpty()) {
        item(key = "allDone") {
            MessageState(
                icon = R.drawable.ic_done_all,
                title = stringResource(R.string.shopping_done_title),
                body = stringResource(R.string.shopping_done_message),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    groups.sections.forEach { section ->
        item(key = "label-${section.label?.id ?: "none"}") {
            Text(
                text = section.label?.name ?: stringResource(R.string.shopping_other),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp)
                        .semantics { heading() },
            )
        }
        items(section.items, key = { it.id }) { item ->
            SwipeableItem(item, actions, large, onEdit)
        }
    }
    if (groups.checked.isNotEmpty()) {
        item(key = "checkedHeader") {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.shopping_in_cart, groups.checked.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics { heading() },
                )
                TextButton(onClick = actions.onClearChecked) { Text(stringResource(R.string.shopping_clear_checked)) }
            }
        }
        items(groups.checked, key = { it.id }) { item ->
            SwipeableItem(item, actions, large, onEdit)
        }
    }
}

@Composable
private fun SwipeableItem(item: ShoppingItem, actions: ShoppingActions, large: Boolean, onEdit: (ShoppingItem) -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) actions.onDelete(item)
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue ==
                    SwipeToDismissBoxValue.EndToStart
                ) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    Color.Transparent
                },
                label = "swipe",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        ItemRow(item, large, onToggle = { actions.onToggle(item) }, onLongClick = { onEdit(item) })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemRow(item: ShoppingItem, large: Boolean, onToggle: () -> Unit, onLongClick: () -> Unit) {
    val locale = currentLocale()
    val line = remember(item, locale) { itemLine(item, locale) }
    val checkedLabel = stringResource(R.string.shopping_checked)
    val uncheckedLabel = stringResource(R.string.shopping_unchecked)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .combinedClickable(role = Role.Checkbox, onClick = onToggle, onLongClick = onLongClick)
                .semantics(mergeDescendants = true) {
                    toggleableState = ToggleableState(item.checked)
                    stateDescription = if (item.checked) checkedLabel else uncheckedLabel
                }.heightIn(min = if (large) 72.dp else 56.dp)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(item.checked, size = if (large) 34.dp else 26.dp)
        Spacer(Modifier.width(16.dp))
        Column(
            Modifier
                .weight(1f)
                .alpha(if (item.checked) 0.5f else 1f),
        ) {
            val textStyle =
                if (large) {
                    MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = Sans,
                    )
                } else {
                    MaterialTheme.typography.bodyLarge
                }
            Text(
                text = itemText(line),
                style = textStyle.copy(textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None),
            )
            if (item.recipeNames.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.shopping_for_recipe, item.recipeNames.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CheckCircle(checked: Boolean, size: Dp) {
    val fill by animateColorAsState(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent, label = "check")
    Surface(
        shape = CircleShape,
        color = fill,
        border = if (checked) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(size),
    ) {
        if (checked) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(size * 0.7f),
                )
            }
        }
    }
}

internal fun itemLine(item: ShoppingItem, locale: Locale): IngredientLine =
    if (item.unit != null || item.food != null) {
        IngredientFormatter.parsed(item.quantity, item.unit, item.food, item.note, 1.0, locale)
    } else {
        IngredientFormatter.freeText(item.note, if (item.quantity > 1) item.quantity else 0.0, 1.0, locale)
    }

private fun itemText(line: IngredientLine): AnnotatedString =
    buildAnnotatedString {
        if (line.hasMeasure) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(measureText(line)) }
            append(' ')
        }
        append(line.text)
        line.note?.let { append(", $it") }
    }

@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Loading() {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(6) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Placeholder(Modifier.size(26.dp), shape = CircleShape)
                Spacer(Modifier.width(16.dp))
                Placeholder(
                    Modifier
                        .height(18.dp)
                        .width(if (it % 2 == 0) 180.dp else 240.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
