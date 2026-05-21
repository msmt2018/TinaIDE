package com.wuxianggujun.tinaide.core.packages.store

import android.content.Context
import android.content.SharedPreferences
import com.wuxianggujun.tinaide.core.packages.model.InstallType
import com.wuxianggujun.tinaide.core.packages.model.Platform
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import com.wuxianggujun.tinaide.core.serialization.JsonSerializer

class InstallHistoryStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = JsonSerializer.default

    companion object {
        private const val PREFS_NAME = "package_install_history"
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY_SIZE = 100
    }

    fun recordInstall(
        packageId: String,
        packageName: String,
        platform: Platform,
        version: String,
        installType: InstallType,
        success: Boolean,
        errorMessage: String? = null
    ) {
        val history = getHistory().toMutableList()
        history.add(0, HistoryEntry(
            packageId = packageId,
            packageName = packageName,
            platform = platform.name,
            version = version,
            installType = installType.name,
            action = "install",
            success = success,
            errorMessage = errorMessage,
            timestamp = System.currentTimeMillis()
        ))
        
        if (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.lastIndex)
        }
        
        saveHistory(history)
    }

    fun recordUninstall(
        packageId: String,
        packageName: String,
        platform: Platform,
        success: Boolean,
        errorMessage: String? = null
    ) {
        val history = getHistory().toMutableList()
        history.add(0, HistoryEntry(
            packageId = packageId,
            packageName = packageName,
            platform = platform.name,
            version = null,
            installType = null,
            action = "uninstall",
            success = success,
            errorMessage = errorMessage,
            timestamp = System.currentTimeMillis()
        ))
        
        if (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.lastIndex)
        }
        
        saveHistory(history)
    }

    fun recordUpdate(
        packageId: String,
        packageName: String,
        platform: Platform,
        fromVersion: String,
        toVersion: String,
        success: Boolean,
        errorMessage: String? = null
    ) {
        val history = getHistory().toMutableList()
        history.add(0, HistoryEntry(
            packageId = packageId,
            packageName = packageName,
            platform = platform.name,
            version = toVersion,
            fromVersion = fromVersion,
            installType = null,
            action = "update",
            success = success,
            errorMessage = errorMessage,
            timestamp = System.currentTimeMillis()
        ))
        
        if (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.lastIndex)
        }
        
        saveHistory(history)
    }

    fun getHistory(): List<HistoryEntry> {
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<HistoryEntry>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveHistory(history: List<HistoryEntry>) {
        val jsonString = json.encodeToString(history)
        prefs.edit().putString(KEY_HISTORY, jsonString).apply()
    }
}

@Serializable
data class HistoryEntry(
    val packageId: String,
    val packageName: String,
    val platform: String,
    val version: String? = null,
    val fromVersion: String? = null,
    val installType: String? = null,
    val action: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long
) {
    val actionDisplayName: String
        get() = when (action) {
            "install" -> "Installed"
            "uninstall" -> "Uninstalled"
            "update" -> "Updated"
            else -> action
        }
}
