package com.fazinhan.lists.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fazinhan.lists.data.HistoryEntry
import com.fazinhan.lists.data.Item
import com.fazinhan.lists.data.ListItems
import com.fazinhan.lists.data.ListSummary
import com.fazinhan.lists.data.Prefs
import com.fazinhan.lists.data.Repository
import com.fazinhan.lists.work.Maintenance
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Main : Screen
    data object Lists : Screen
    data object History : Screen
    data object Archive : Screen
    data class ArchivedList(val listId: Long) : Screen
    data object Settings : Screen
}

class AppViewModel(private val app: Application) : AndroidViewModel(app) {

    val repo = Repository.get(app)
    val prefs = Prefs.get(app)

    val backStack = mutableStateListOf<Screen>(Screen.Main)

    fun navigate(screen: Screen) {
        backStack.add(screen)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Wall-clock time, refreshed periodically so overdue highlighting stays current. */
    private val nowState = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = nowState

    /** Null while the database is still loading. */
    val lists: StateFlow<List<ListSummary>?> =
        repo.lists().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val selectedListId = MutableStateFlow<Long?>(null)

    /** The open list: the one the user picked, otherwise the top list. */
    val currentList: StateFlow<ListSummary?> = combine(lists, selectedListId) { all, id ->
        all?.let { it.firstOrNull { l -> l.id == id } ?: it.firstOrNull() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentItems: StateFlow<ListItems?> = currentList
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.items(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val history: StateFlow<List<HistoryEntry>> =
        repo.history().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun archivedItems(listId: Long): Flow<List<Item>> = repo.archivedItems(listId)

    private var ticker: Job? = null

    /** Runs maintenance now and then once a minute while the app is in the foreground. */
    fun onForeground() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                nowState.value = System.currentTimeMillis()
                Maintenance.run(app)
                delay(60_000)
            }
        }
    }

    fun onBackground() {
        ticker?.cancel()
    }

    fun openList(id: Long) {
        selectedListId.value = id
        backStack.removeRange(1, backStack.size)
    }

    fun createList(title: String, open: Boolean) = viewModelScope.launch {
        val id = repo.addList(title)
        if (open) openList(id)
    }

    fun renameList(id: Long, title: String) = viewModelScope.launch { repo.renameList(id, title) }
    suspend fun deleteList(id: Long) = repo.deleteList(id)
    fun reorderLists(ids: List<Long>) = viewModelScope.launch { repo.reorderLists(ids) }

    fun addItem(listId: Long, name: String) = viewModelScope.launch { repo.addItem(listId, name) }
    fun setChecked(item: Item, checked: Boolean) = viewModelScope.launch { repo.setChecked(item.id, checked) }
    suspend fun deleteItem(id: Long) = repo.deleteItem(id)
    fun reorderItems(ids: List<Long>) = viewModelScope.launch { repo.reorderItems(ids) }
}
