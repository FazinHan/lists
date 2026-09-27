package com.fazinhan.lists

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fazinhan.lists.ui.AppViewModel
import com.fazinhan.lists.ui.Screen
import com.fazinhan.lists.ui.screens.ArchiveScreen
import com.fazinhan.lists.ui.screens.ArchivedListScreen
import com.fazinhan.lists.ui.screens.ChecklistScreen
import com.fazinhan.lists.ui.screens.HistoryScreen
import com.fazinhan.lists.ui.screens.ListsScreen
import com.fazinhan.lists.ui.screens.SettingsScreen
import com.fazinhan.lists.ui.theme.ListsTheme
import com.fazinhan.lists.ui.theme.isDarkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            val themeMode by vm.prefs.theme.collectAsStateWithLifecycle()
            val dark = isDarkTheme(themeMode)
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LifecycleResumeEffect(vm) {
                vm.onForeground()
                onPauseOrDispose { vm.onBackground() }
            }
            ListsTheme(dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RequestNotificationPermission(vm)
                    App(vm)
                }
            }
        }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val screen = vm.backStack.last()

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen && vm.backStack.size > 1) { vm.back() }

    fun go(target: Screen) {
        scope.launch { drawerState.close() }
        vm.navigate(target)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                Row(
                    Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_notification),
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Lists", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                DrawerEntry("Lists", Icons.AutoMirrored.Filled.FormatListBulleted) { go(Screen.Lists) }
                DrawerEntry("History", Icons.Filled.History) { go(Screen.History) }
                DrawerEntry("Archive", Icons.Filled.Inventory2) { go(Screen.Archive) }
            }
        },
    ) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen",
        ) { target ->
            when (target) {
                Screen.Main -> ChecklistScreen(
                    vm = vm,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenSettings = { vm.navigate(Screen.Settings) },
                )
                Screen.Lists -> ListsScreen(vm)
                Screen.History -> HistoryScreen(vm)
                Screen.Archive -> ArchiveScreen(vm)
                is Screen.ArchivedList -> ArchivedListScreen(vm, target.listId)
                Screen.Settings -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
private fun DrawerEntry(label: String, icon: ImageVector, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, null) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

/** Asks once for notification permission so week-old reminders can be delivered. */
@Composable
private fun RequestNotificationPermission(vm: AppViewModel) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !vm.prefs.askedNotificationPermission.value) {
            vm.prefs.askedNotificationPermission.value = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
