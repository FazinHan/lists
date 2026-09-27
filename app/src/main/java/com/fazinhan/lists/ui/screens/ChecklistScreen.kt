package com.fazinhan.lists.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fazinhan.lists.data.DAY_MS
import com.fazinhan.lists.data.Item
import com.fazinhan.lists.ui.AppViewModel
import com.fazinhan.lists.ui.components.DeleteConfirmDialog
import com.fazinhan.lists.ui.components.EmptyState
import com.fazinhan.lists.ui.components.SwipeToDelete
import com.fazinhan.lists.ui.components.TitleDialog
import com.fazinhan.lists.ui.components.rememberConfirmer
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val KEY_HEADER = "header"
private const val KEY_NEW_ITEM = "new-item"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChecklistScreen(
    vm: AppViewModel,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val list by vm.currentList.collectAsStateWithLifecycle()
    val loaded by vm.currentItems.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val showTitleInBar by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    var creatingList by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(showTitleInBar, enter = fadeIn(), exit = fadeOut()) {
                        Text(list?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, "Menu") }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
    ) { padding ->
        val current = list
        if (lists?.isEmpty() == true || current == null) {
            if (lists != null) {
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState("No lists yet", "Create a list to start adding items.")
                    Button(onClick = { creatingList = true }) { Text("Create a list") }
                }
            }
        } else {
            Checklist(
                vm = vm,
                listId = current.id,
                title = current.title,
                items = loaded?.takeIf { it.listId == current.id }?.items,
                now = now,
                listState = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
            )
        }
    }

    if (creatingList) {
        TitleDialog(
            title = "New list",
            confirmLabel = "Create",
            onConfirm = { vm.createList(it, open = true); creatingList = false },
            onDismiss = { creatingList = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Checklist(
    vm: AppViewModel,
    listId: Long,
    title: String,
    items: List<Item>?,
    now: Long,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier,
) {
    val all = items.orEmpty()
    val open = all.filter { !it.checked }
    val overdueIds = open.filter { it.isOverdue(now) }.map { it.id }.toSet()
    // Overdue items float to the top; the rest keep their manual order.
    val sortedOpen = open.sortedBy { if (it.id in overdueIds) 0 else 1 }
    val done = all.filter { it.checked }.sortedBy { it.checkedAt }

    // While dragging, the order lives here until the database catches up.
    var dragOrder by remember(listId) { mutableStateOf<List<Item>?>(null) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(items) { if (!dragging) dragOrder = null }
    val displayedOpen = dragOrder ?: sortedOpen

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = from.key as? Long ?: return@rememberReorderableLazyListState
        val toId = to.key as? Long ?: return@rememberReorderableLazyListState
        // Overdue items stay pinned above the rest; moves only happen within a group.
        if ((fromId in overdueIds) != (toId in overdueIds)) return@rememberReorderableLazyListState
        val current = dragOrder ?: sortedOpen
        val fromIndex = current.indexOfFirst { it.id == fromId }
        val toIndex = current.indexOfFirst { it.id == toId }
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        dragOrder = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    val confirmer = rememberConfirmer<Item>()
    suspend fun delete(item: Item): Boolean {
        if (vm.prefs.confirmItemDelete.value && !confirmer.ask(item)) return false
        vm.deleteItem(item.id)
        return true
    }

    var renaming by remember { mutableStateOf(false) }
    val newItemFocus = remember { FocusRequester() }
    var newItemFocused by remember { mutableStateOf(false) }

    // Keep the "new item" field in view while typing several items in a row.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible, newItemFocused, displayedOpen.size) {
        if (imeVisible && newItemFocused) listState.animateScrollToItem(displayedOpen.size + 1)
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = KEY_HEADER) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { renaming = true }
                    .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (items != null && all.isNotEmpty()) {
                    Text(
                        "${done.size} of ${all.size} done",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(displayedOpen, key = { it.id }) { item ->
            ReorderableItem(reorderState, key = item.id) { isDragging ->
                SwipeToDelete(onDelete = { delete(item) }) {
                    ItemRow(
                        item = item,
                        now = now,
                        overdue = item.id in overdueIds,
                        dragging = isDragging,
                        onToggle = { vm.setChecked(item, !item.checked) },
                        dragHandle = Modifier.draggableHandle(
                            onDragStarted = { dragging = true },
                            onDragStopped = {
                                dragging = false
                                dragOrder?.let { order -> vm.reorderItems(order.map { it.id }) }
                            },
                        ),
                    )
                }
            }
        }

        item(key = KEY_NEW_ITEM) {
            NewItemField(
                focusRequester = newItemFocus,
                onFocusChanged = { newItemFocused = it },
                onAdd = { vm.addItem(listId, it) },
            )
        }

        if (done.isNotEmpty()) {
            item(key = "done-divider") {
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
        }

        items(done, key = { it.id }) { item ->
            SwipeToDelete(onDelete = { delete(item) }, modifier = Modifier.animateItem()) {
                ItemRow(
                    item = item,
                    now = now,
                    overdue = false,
                    dragging = false,
                    onToggle = { vm.setChecked(item, !item.checked) },
                    dragHandle = null,
                )
            }
        }
    }

    confirmer.pending?.let { item ->
        DeleteConfirmDialog(
            title = "Delete item?",
            message = "“${item.name}” will be deleted.",
            onConfirm = { dontAsk ->
                if (dontAsk) vm.prefs.confirmItemDelete.value = false
                confirmer.respond(true)
            },
            onDismiss = { confirmer.respond(false) },
        )
    }

    if (renaming) {
        TitleDialog(
            title = "Rename list",
            confirmLabel = "Rename",
            initial = title,
            onConfirm = { vm.renameList(listId, it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
}

@Composable
private fun ItemRow(
    item: Item,
    now: Long,
    overdue: Boolean,
    dragging: Boolean,
    onToggle: () -> Unit,
    dragHandle: Modifier?,
) {
    val cs = MaterialTheme.colorScheme
    val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "drag-elevation")
    val background = when {
        overdue -> cs.error.copy(alpha = 0.16f).compositeOver(cs.surface)
        dragging -> cs.surfaceContainerHigh
        else -> cs.surface
    }
    Surface(color = background, shadowElevation = elevation, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clickable(onClick = onToggle)
                .heightIn(min = 56.dp)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = item.checked,
                onCheckedChange = { onToggle() },
                colors = if (overdue) {
                    CheckboxDefaults.colors(uncheckedColor = cs.error)
                } else {
                    CheckboxDefaults.colors(checkedColor = cs.outline, checkmarkColor = cs.surface)
                },
            )
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        item.checked -> cs.onSurface.copy(alpha = 0.38f)
                        overdue -> cs.error
                        else -> cs.onSurface
                    },
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    fontWeight = if (overdue) FontWeight.SemiBold else null,
                )
                if (overdue) {
                    Text(
                        "Unchecked for ${(now - item.createdAt) / DAY_MS} days",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.error,
                    )
                }
            }
            if (dragHandle != null) {
                Box(dragHandle.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.DragHandle, "Reorder", tint = cs.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }
    }
}

/** Notion-style trailing row: tap to type, Enter adds the item and keeps the keyboard up. */
@Composable
private fun NewItemField(
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onAdd: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                focusRequester.requestFocus()
            }
            .heightIn(min = 56.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, null, tint = if (focused) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.6f))
        Spacer(Modifier.width(16.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
            cursorBrush = SolidColor(cs.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (text.isNotBlank()) {
                    onAdd(text.trim())
                    text = ""
                } else {
                    focusManager.clearFocus()
                }
            }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChanged(it.isFocused)
                },
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text(
                            if (focused) "Item name" else "New item",
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    inner()
                }
            },
        )
    }
}
