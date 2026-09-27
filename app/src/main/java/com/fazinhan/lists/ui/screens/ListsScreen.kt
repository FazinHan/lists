package com.fazinhan.lists.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fazinhan.lists.data.ListSummary
import com.fazinhan.lists.ui.AppViewModel
import com.fazinhan.lists.ui.components.EmptyState
import com.fazinhan.lists.ui.components.LoudDeleteDialog
import com.fazinhan.lists.ui.components.SwipeToDelete
import com.fazinhan.lists.ui.components.TitleDialog
import com.fazinhan.lists.ui.components.rememberConfirmer
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(vm: AppViewModel) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val current by vm.currentList.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    var dragOrder by remember { mutableStateOf<List<ListSummary>?>(null) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(lists) { if (!dragging) dragOrder = null }
    val displayed = dragOrder ?: lists.orEmpty()

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val currentOrder = dragOrder ?: lists.orEmpty()
        val fromIndex = currentOrder.indexOfFirst { it.id == from.key }
        val toIndex = currentOrder.indexOfFirst { it.id == to.key }
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        dragOrder = currentOrder.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    val confirmer = rememberConfirmer<ListSummary>()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lists") },
                navigationIcon = {
                    IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "New list") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (lists?.isEmpty() == true) {
                item { EmptyState("No lists", "Tap + to create your first list.") }
            }
            items(displayed, key = { it.id }) { list ->
                ReorderableItem(reorderState, key = list.id) { isDragging ->
                    SwipeToDelete(onDelete = {
                        if (confirmer.ask(list)) {
                            vm.deleteList(list.id)
                            true
                        } else {
                            false
                        }
                    }) {
                        ListRow(
                            list = list,
                            isOpen = list.id == current?.id,
                            isDefault = list.id == displayed.firstOrNull()?.id,
                            dragging = isDragging,
                            onClick = { vm.openList(list.id) },
                            dragHandle = Modifier.draggableHandle(
                                onDragStarted = { dragging = true },
                                onDragStopped = {
                                    dragging = false
                                    dragOrder?.let { order -> vm.reorderLists(order.map { it.id }) }
                                },
                            ),
                        )
                    }
                }
            }
            if (!lists.isNullOrEmpty()) {
                item(key = "hint") {
                    Text(
                        "Drag to reorder. The top list opens when the app starts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }

    if (creating) {
        TitleDialog(
            title = "New list",
            confirmLabel = "Create",
            onConfirm = { vm.createList(it, open = false); creating = false },
            onDismiss = { creating = false },
        )
    }

    confirmer.pending?.let { list ->
        val count = list.totalCount
        LoudDeleteDialog(
            title = "Delete “${list.title}”?",
            message = buildString {
                append("Everything within this list will be lost")
                if (count > 0) append(" — all $count item${if (count == 1) "" else "s"}, including archived ones")
                append(". This can’t be undone.")
            },
            confirmLabel = "Delete everything",
            onConfirm = { confirmer.respond(true) },
            onDismiss = { confirmer.respond(false) },
        )
    }
}

@Composable
private fun ListRow(
    list: ListSummary,
    isOpen: Boolean,
    isDefault: Boolean,
    dragging: Boolean,
    onClick: () -> Unit,
    dragHandle: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "drag-elevation")
    Surface(
        color = if (dragging) cs.surfaceContainerHigh else cs.surface,
        shadowElevation = elevation,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .heightIn(min = 72.dp)
                .padding(start = 24.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    list.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isOpen) FontWeight.Bold else null,
                    color = if (isOpen) cs.primary else cs.onSurface,
                )
                Text(
                    buildString {
                        append("${list.openCount} to do")
                        if (list.checkedCount > 0) append(" · ${list.checkedCount} done")
                        if (isDefault) append(" · opens on launch")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Box(dragHandle.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.DragHandle, "Reorder", tint = cs.onSurfaceVariant)
            }
        }
    }
}
