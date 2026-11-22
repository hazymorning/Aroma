package app.kochi.feature.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kochi.core.model.ShoppingItem
import app.kochi.data.repository.ShoppingRepository
import app.kochi.data.settings.SettingsRepository
import app.kochi.data.toKochiError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

class ShoppingViewModel(private val shopping: ShoppingRepository, private val settings: SettingsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ShoppingUiState())
    val state: StateFlow<ShoppingUiState> = mutableState.asStateFlow()

    private var listJobs: List<Job> = emptyList()
    private var deleted: Pair<String, JsonObject>? = null

    init {
        loadLists()
    }

    private fun loadLists() =
        viewModelScope.launch {
            shopping
                .lists()
                .catch { error -> mutableState.update { it.copy(isLoading = false, error = error.toKochiError()) } }
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
                        if (list != null) mutableState.update { it.copy(list = list, isLoading = false) }
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
                val failure = error.toKochiError()
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

    fun toggle(item: ShoppingItem) = edit { shopping.toggle(item.listId, item.id) }

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
                .onSuccess { id -> select(id) }
                .onFailure { error -> mutableState.update { it.copy(notice = error.toKochiError()) } }
        }

    fun renameList(name: String) {
        val listId = state.value.list?.id ?: return
        viewModelScope.launch {
            runCatching { shopping.renameList(listId, name) }
                .onFailure { error -> mutableState.update { it.copy(notice = error.toKochiError()) } }
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
                }.onFailure { error -> mutableState.update { it.copy(notice = error.toKochiError()) } }
        }
    }

    fun noticeShown() = mutableState.update { it.copy(notice = null) }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { error -> mutableState.update { it.copy(notice = error.toKochiError()) } }
        }
    }
}
