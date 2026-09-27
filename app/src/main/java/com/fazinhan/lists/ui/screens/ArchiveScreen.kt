package com.fazinhan.lists.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fazinhan.lists.data.Item
import com.fazinhan.lists.ui.AppViewModel
import com.fazinhan.lists.ui.Screen
import com.fazinhan.lists.ui.components.DeleteConfirmDialog
import com.fazinhan.lists.ui.components.EmptyState
import com.fazinhan.lists.ui.components.SwipeToDelete
import com.fazinhan.lists.ui.components.rememberConfirmer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(vm: AppViewModel) {
    val lists by vm.lists.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archive") },
                navigationIcon = {
                    IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (lists?.isEmpty() == true) {
                item { EmptyState("Nothing archived", "Checked items move here after 24 hours.") }
            }
            items(lists.orEmpty(), key = { it.id }) { list ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.navigate(Screen.ArchivedList(list.id)) }
                        .heightIn(min = 72.dp)
                        .padding(start = 24.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(list.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (list.archivedCount) {
                                0 -> "Nothing archived"
                                1 -> "1 archived item"
                                else -> "${list.archivedCount} archived items"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedListScreen(vm: AppViewModel, listId: Long) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val title = lists?.firstOrNull { it.id == listId }?.title.orEmpty()
    val items by remember(listId) { vm.archivedItems(listId) }.collectAsStateWithLifecycle(null)
    val confirmer = rememberConfirmer<Item>()

    suspend fun delete(item: Item): Boolean {
        if (vm.prefs.confirmArchiveDelete.value && !confirmer.ask(item)) return false
        vm.deleteItem(item.id)
        return true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (items?.isEmpty() == true) {
                item { EmptyState("Nothing archived", "Checked items move here after 24 hours.") }
            }
            items(items.orEmpty(), key = { it.id }) { item ->
                SwipeToDelete(onDelete = { delete(item) }, modifier = Modifier.animateItem()) {
                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            Modifier
                                .heightIn(min = 60.dp)
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                        ) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = TextDecoration.LineThrough,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            item.checkedAt?.let {
                                Text(
                                    "Checked ${formatDay(localDate(it)).lowercaseIfRelative()} at ${formatTime(it)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    confirmer.pending?.let { item ->
        DeleteConfirmDialog(
            title = "Delete archived item?",
            message = "“${item.name}” will be permanently deleted.",
            onConfirm = { dontAsk ->
                if (dontAsk) vm.prefs.confirmArchiveDelete.value = false
                confirmer.respond(true)
            },
            onDismiss = { confirmer.respond(false) },
        )
    }
}

private fun String.lowercaseIfRelative() =
    if (this == "Today" || this == "Yesterday") lowercase() else "on $this"
