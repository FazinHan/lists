package com.fazinhan.lists.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fazinhan.lists.data.Prefs
import com.fazinhan.lists.data.ThemeMode
import com.fazinhan.lists.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val prefs = vm.prefs
    val theme by prefs.theme.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Appearance")
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { prefs.setTheme(mode) }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = theme == mode, onClick = { prefs.setTheme(mode) })
                    Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Confirmations")
            SwitchRow(
                pref = prefs.confirmItemDelete,
                title = "Confirm before deleting items",
                subtitle = "Ask before a swiped item is deleted",
            )
            SwitchRow(
                pref = prefs.confirmArchiveDelete,
                title = "Confirm before deleting archived items",
                subtitle = "Ask before an archived item is deleted",
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Reminders")
            SwitchRow(
                pref = prefs.overdueNotifications,
                title = "Week-old item notifications",
                subtitle = "Notify me when an item stays unchecked for a week",
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("How it works")
            Text(
                "Checked items drop to the bottom of their list and move to the archive after 24 hours. " +
                    "Items left unchecked for a week jump to the top, highlighted in red.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(pref: Prefs.BoolPref, title: String, subtitle: String) {
    val checked by pref.flow.collectAsStateWithLifecycle()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { pref.value = !checked }
            .heightIn(min = 64.dp)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = { pref.value = it })
    }
}
