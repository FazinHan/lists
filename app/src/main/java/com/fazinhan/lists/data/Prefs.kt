package com.fazinhan.lists.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("Follow system") }

class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    inner class BoolPref(private val key: String, default: Boolean) {
        private val state = MutableStateFlow(sp.getBoolean(key, default))
        val flow: StateFlow<Boolean> = state
        var value: Boolean
            get() = state.value
            set(v) {
                sp.edit { putBoolean(key, v) }
                state.value = v
            }
    }

    private val themeState = MutableStateFlow(
        runCatching { ThemeMode.valueOf(sp.getString("theme", null)!!) }.getOrDefault(ThemeMode.DARK)
    )
    val theme: StateFlow<ThemeMode> = themeState
    fun setTheme(mode: ThemeMode) {
        sp.edit { putString("theme", mode.name) }
        themeState.value = mode
    }

    val confirmItemDelete = BoolPref("confirm_item_delete", true)
    val confirmArchiveDelete = BoolPref("confirm_archive_delete", true)
    val overdueNotifications = BoolPref("overdue_notifications", true)
    val askedNotificationPermission = BoolPref("asked_notification_permission", false)

    companion object {
        @Volatile private var instance: Prefs? = null

        fun get(context: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(context.applicationContext).also { instance = it }
        }
    }
}
