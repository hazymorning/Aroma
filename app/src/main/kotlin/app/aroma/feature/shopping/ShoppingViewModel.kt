package app.aroma.feature.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.core.model.ShoppingItem
import app.aroma.core.model.ShoppingList
import app.aroma.core.model.ShoppingListInfo
import app.aroma.data.repository.ShoppingRepository
import app.aroma.data.settings.SettingsRepository
import app.aroma.data.toAromaError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** How long a checked row stays in place before it moves into or out of the basket. */
private const val SETTLE_MILLIS = 450L

class ShoppingViewModel(private val shopping: ShoppingRepository, private val settings: SettingsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ShoppingUiState())
    val state: StateFlow<ShoppingUiState> = mutableState.asStateFlow()

    private var listJobs: List<Job> = emptyList()
    private var deleted: Pair<String, JsonObject>? = null

    /** Checks that are shown but not yet sent, by item id. */
    private val settling = mutableMapOf<String, Job>()

    init {
        loadLists()
    }

    private fun loadLists() =
        viewModelScope.launch {
            shopping
                .lists()
                .catch { error -> mutableState.update { it.copy(isLoading = false, error = error.toAromaError()) } }
                .collect { loadable ->
                    mutableState.update { it.copy(lists = loadable.data, isOffline = loadable.isOffline) }
                    val selected = mutableState.value.list?.id
                    if (selected == null || loadable.data.none { it.id == selected }) {
                        val remembered = settings.settings.first().lastShoppingList
                        val target = loadable.data.firstOrNull { it.id == remembered } ?: loadable.data.firstOrNull()
                        if (target !=
                            null
                        ) {
                            select(target.id)
                        } else if (!loadable.isRefreshing) {
                            mutableState.update { it.copy(isLoading = false, list = null) }
                        }
                    }
                }
        }

    fun select(listId: String) {
        listJobs.forEach { it.cancel() }
        viewModelScope.launch { settings.setLastShoppingList(listId) }
        listJobs =
            listOf(
                viewModelScope.launch {
                    shopping.observe(listId).collect { list ->
                        if (list != null) {
                            val settlingChecks = settle(list)
                            mutableState.update { it.copy(list = list, isLoading = false, settlingChecks = settlingChecks) }
                        }
                    }
                },
                viewModelScope.launch {
                    shopping.pendingCount(listId).collect { count -> mutableState.update { it.copy(pendingChanges = count) } }
                },
                viewModelScope.launch { refreshList(listId) },
            )
    }

    fun refresh() {
        val id = state.value.list?.id
        if (id == null) {
            loadLists()
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(isRefreshing = true) }
            refreshList(id)
            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun refreshList(listId: String) {
        runCatching { shopping.refresh(listId) }
            .onSuccess { mutableState.update { it.copy(isOffline = false, error = null) } }
            .onFailure { error ->
                val failure = error.toAromaError()
                mutableState.update { it.copy(isLoading = false, isOffline = true, error = if (it.list == null) failure else null) }
            }
    }

    fun onNewItemChange(text: String) = mutableState.update { it.copy(newItem = text) }

    fun addItem() {
        val text = state.value.newItem.trim()
        val listId = state.value.list?.id ?: return
        if (text.isEmpty()) return
        mutableState.update { it.copy(newItem = "") }
        edit { shopping.add(listId, text) }
    }

    /**
     * Shows the new check at once but changes the list [SETTLE_MILLIS] later, so the row stays in
     * place until its check is drawn and only then moves. A second tap in the meantime takes it back.
     */
    fun toggle(item: ShoppingItem) {
        val waiting = settling.remove(item.id)
        if (waiting != null) {
            waiting.cancel()
            mutableState.update { it.copy(settlingChecks = it.settlingChecks - item.id) }
            return
        }
        mutableState.update { it.copy(settlingChecks = it.settlingChecks + (item.id to !item.checked)) }
        settling[item.id] =
            viewModelScope.launch {
                delay(SETTLE_MILLIS)
                settling.remove(item.id)
                runCatching { shopping.toggle(item.listId, item.id) }
                    .onFailure { error ->
                        mutableState.update { it.copy(settlingChecks = it.settlingChecks - item.id, notice = error.toAromaError()) }
                    }
            }
    }

    /**
     * The checks [list] does not show yet. A check the list already shows, because the change
     * arrived from elsewhere first, is no longer sent.
     */
    private fun settle(list: ShoppingList): Map<String, Boolean> {
        val checked = list.items.associate { it.id to it.checked }
        val (arrived, pending) =
            state.value.settlingChecks.entries
                .partition { (id, shown) -> checked[id] == shown }
        arrived.forEach { settling.remove(it.key)?.cancel() }
        return pending.filter { it.key in checked }.associate { it.key to it.value }
    }

    fun rename(item: ShoppingItem, text: String) = edit { shopping.rename(item.listId, item.id, text) }

    fun delete(item: ShoppingItem) =
        edit {
            shopping.delete(item.listId, item.id)?.let { removed ->
                deleted = item.listId to removed
                mutableState.update { it.copy(lastDeleted = item) }
            }
        }

    fun undoDelete() {
        val (listId, item) = deleted ?: return
        deleted = null
        mutableState.update { it.copy(lastDeleted = null) }
        edit { shopping.restore(listId, item) }
    }

    fun deleteHandled() {
        deleted = null
        mutableState.update { it.copy(lastDeleted = null) }
    }

    fun clearChecked() {
        val listId = state.value.list?.id ?: return
        edit { shopping.clearChecked(listId) }
    }

    fun createList(name: String) =
        viewModelScope.launch {
            runCatching { shopping.createList(name) }
                .onSuccess { id ->
                    // The switcher shows the new list right away, before the lists are fetched again.
                    mutableState.update { it.copy(lists = it.lists + ShoppingListInfo(id, name.trim())) }
                    select(id)
                }.onFailure { error -> mutableState.update { it.copy(notice = error.toAromaError()) } }
        }

    fun renameList(name: String) {
        val listId = state.value.list?.id ?: return
        viewModelScope.launch {
            runCatching { shopping.renameList(listId, name) }
                .onSuccess {
                    mutableState.update { state ->
                        state.copy(lists = state.lists.map { if (it.id == listId) it.copy(name = name.trim()) else it })
                    }
                }.onFailure { error -> mutableState.update { it.copy(notice = error.toAromaError()) } }
        }
    }

    fun deleteList() {
        val listId = state.value.list?.id ?: return
        viewModelScope.launch {
            runCatching { shopping.deleteList(listId) }
                .onSuccess {
                    mutableState.update { it.copy(list = null, isLoading = true) }
                    settings.setLastShoppingList(null)
                    loadLists()
                }.onFailure { error -> mutableState.update { it.copy(notice = error.toAromaError()) } }
        }
    }

    fun noticeShown() = mutableState.update { it.copy(notice = null) }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { error -> mutableState.update { it.copy(notice = error.toAromaError()) } }
        }
    }
}
