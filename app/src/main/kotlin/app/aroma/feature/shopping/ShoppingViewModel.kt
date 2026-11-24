package app.aroma.feature.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.core.model.ShoppingItem
import app.aroma.core.model.ShoppingList
import app.aroma.core.model.ShoppingListInfo
import app.aroma.data.AromaError
import app.aroma.data.attempt
import app.aroma.data.network.ServerConnection
import app.aroma.data.repository.MealPlanRepository
import app.aroma.data.repository.ShoppingRepository
import app.aroma.data.settings.SettingsRepository
import app.aroma.data.toAromaError
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.time.Clock
import java.time.LocalDate

/** How long a checked row stays in place before it moves into or out of the basket. */
private const val SETTLE_MILLIS = 450L

/** The days ahead whose planned recipes fill an empty list. */
private const val PLAN_DAYS = 7L

sealed interface ShoppingEvent {
    /** [item] is gone from list [listId]; [ShoppingViewModel.undoDelete] puts it back. */
    data class Deleted(val listId: String, val item: JsonObject) : ShoppingEvent

    /** The ingredients of [count] planned recipes went onto list [listName]. */
    data class AddedFromPlan(val count: Int, val listName: String) : ShoppingEvent

    data class Failed(val error: AromaError) : ShoppingEvent
}

class ShoppingViewModel(
    private val shopping: ShoppingRepository,
    private val settings: SettingsRepository,
    connection: ServerConnection,
    private val mealPlans: MealPlanRepository? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(ShoppingUiState())
    val state: StateFlow<ShoppingUiState> = mutableState.asStateFlow()

    private val outbox = Channel<ShoppingEvent>(Channel.BUFFERED)
    val events: Flow<ShoppingEvent> = outbox.receiveAsFlow()

    private var listJobs: List<Job> = emptyList()

    /** The list shown or on its way, known at once, so lists that arrive meanwhile never pick another. */
    private var selectedId: String? = null

    /** Checks that are shown but not yet sent, by item id. */
    private val settling = mutableMapOf<String, Job>()

    init {
        loadLists()
        viewModelScope.launch {
            // Mealie answers again: the lists and the list shown load anew. Out of reach: what shows is the phone's copy.
            connection.isReachable.drop(1).collect { reachable ->
                if (reachable) {
                    loadLists()
                    selectedId?.let { refreshList(it) }
                } else {
                    mutableState.update { it.copy(isOffline = true) }
                }
            }
        }
    }

    private fun loadLists() =
        viewModelScope.launch {
            shopping
                .lists()
                .catch { error -> mutableState.update { it.copy(isLoading = false, error = error.toAromaError()) } }
                .collect { loadable ->
                    mutableState.update { it.copy(lists = loadable.data, isOffline = loadable.isOffline) }
                    val selected = selectedId
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
        selectedId = listId
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

    /** A pull to refresh: the lists anew, so one made elsewhere shows up, and the list shown with its items. */
    fun refresh() {
        loadLists()
        val id = state.value.list?.id ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(isRefreshing = true) }
            refreshList(id)
            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun refreshList(listId: String) {
        attempt { shopping.refresh(listId) }
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
                attempt { shopping.toggle(item.listId, item.id) }
                    .onFailure { error ->
                        mutableState.update { it.copy(settlingChecks = it.settlingChecks - item.id) }
                        outbox.send(ShoppingEvent.Failed(error.toAromaError()))
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
            shopping.delete(item.listId, item.id)?.let { removed -> outbox.send(ShoppingEvent.Deleted(item.listId, removed)) }
        }

    fun undoDelete(deleted: ShoppingEvent.Deleted) = edit { shopping.restore(deleted.listId, deleted.item) }

    fun clearChecked() {
        val listId = state.value.list?.id ?: return
        edit { shopping.clearChecked(listId) }
    }

    /** Puts the ingredients of every recipe planned for the coming week on the list shown. */
    fun addFromPlan() {
        val plans = mealPlans ?: return
        val list = state.value.list ?: return
        viewModelScope.launch {
            attempt {
                val recipes =
                    plans
                        .range(LocalDate.now(clock), PLAN_DAYS)
                        .first { !it.isRefreshing }
                        .data
                        .mapNotNull { it.recipe }
                        .distinctBy { it.id }
                recipes.forEach { shopping.addRecipe(list.id, it.id, 1.0, ingredients = null) }
                recipes.size
            }.onSuccess { count ->
                if (count > 0) outbox.send(ShoppingEvent.AddedFromPlan(count, list.name))
            }.onFailure { error -> outbox.send(ShoppingEvent.Failed(error.toAromaError())) }
        }
    }

    fun createList(name: String) =
        viewModelScope.launch {
            attempt { shopping.createList(name) }
                .onSuccess { id ->
                    // The switcher shows the new list right away, before the lists are fetched again.
                    mutableState.update { it.copy(lists = it.lists + ShoppingListInfo(id, name.trim())) }
                    select(id)
                }.onFailure { error -> outbox.send(ShoppingEvent.Failed(error.toAromaError())) }
        }

    fun renameList(name: String) {
        val listId = state.value.list?.id ?: return
        viewModelScope.launch {
            attempt { shopping.renameList(listId, name) }
                .onSuccess {
                    mutableState.update { state ->
                        state.copy(lists = state.lists.map { if (it.id == listId) it.copy(name = name.trim()) else it })
                    }
                }.onFailure { error -> outbox.send(ShoppingEvent.Failed(error.toAromaError())) }
        }
    }

    fun deleteList() {
        val listId = state.value.list?.id ?: return
        viewModelScope.launch {
            attempt { shopping.deleteList(listId) }
                .onSuccess {
                    mutableState.update { it.copy(list = null, isLoading = true) }
                    settings.setLastShoppingList(null)
                    loadLists()
                }.onFailure { error -> outbox.send(ShoppingEvent.Failed(error.toAromaError())) }
        }
    }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch {
            attempt { block() }.onFailure { error -> outbox.send(ShoppingEvent.Failed(error.toAromaError())) }
        }
    }
}
