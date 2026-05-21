package com.wuxianggujun.tinaide.core.config

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import timber.log.Timber
import java.io.File

/**
 * 应用偏好（SharedPreferences）统一入口。
 *
 * 说明：
 * - 默认 SharedPreferences 的文件名是 "<packageName>_preferences"（例如：com.wuxianggujun.tinaide_preferences.xml）
 * - 为避免包名耦合/便于识别，TinaIDE 使用固定文件名：tinaide_preferences.xml
 * - 首次升级会自动把默认 SharedPreferences 的数据迁移到新的文件名
 */
object AppPreferences {
    private const val TAG = "AppPreferences"

    const val PREFS_NAME = "tinaide_preferences"

    private const val KEY_MIGRATED_FROM_DEFAULT = "__migrated_from_default_preferences"

    fun get(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 将 PreferenceManager.getDefaultSharedPreferences() 的数据迁移到 [PREFS_NAME]。
     *
     * 迁移策略：
     * - 仅迁移一次（由 [KEY_MIGRATED_FROM_DEFAULT] 标记）；
     * - 新 prefs 已存在同名 key 时不覆盖（保留新值）；
     * - 迁移成功后 best-effort 清理旧 prefs 文件，避免残留 "<package>_preferences.xml"。
     */
    fun migrateFromDefaultIfNeeded(context: Context) {
        val newPrefs = get(context)
        if (newPrefs.getBoolean(KEY_MIGRATED_FROM_DEFAULT, false)) return

        val oldPrefsFile = File(
            context.applicationInfo.dataDir,
            "shared_prefs/${context.packageName}_preferences.xml"
        )
        if (!oldPrefsFile.exists()) {
            newPrefs.edit().putBoolean(KEY_MIGRATED_FROM_DEFAULT, true).apply()
            return
        }

        val oldPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val oldAll = oldPrefs.all

        val editor = newPrefs.edit()
        var migratedCount = 0

        oldAll.forEach { (key, value) ->
            if (key == KEY_MIGRATED_FROM_DEFAULT) return@forEach
            if (newPrefs.contains(key)) return@forEach

            var wrote = false
            when (value) {
                is String -> {
                    editor.putString(key, value)
                    wrote = true
                }
                is Int -> {
                    editor.putInt(key, value)
                    wrote = true
                }
                is Long -> {
                    editor.putLong(key, value)
                    wrote = true
                }
                is Float -> {
                    editor.putFloat(key, value)
                    wrote = true
                }
                is Boolean -> {
                    editor.putBoolean(key, value)
                    wrote = true
                }
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    (value as? Set<String>)?.let {
                        editor.putStringSet(key, it)
                        wrote = true
                    }
                }
                null -> Unit
                else -> Timber.tag(TAG).w("Skip unsupported type: key=%s type=%s", key, value::class.java.name)
            }
            if (wrote) migratedCount++
        }

        editor.putBoolean(KEY_MIGRATED_FROM_DEFAULT, true).apply()
        Timber.tag(TAG).i("SharedPreferences migrated: %d keys -> %s", migratedCount, PREFS_NAME)

        // 最佳实践：迁移完成后尽量移除旧文件，避免用户在 shared_prefs 里看到两份配置。
        // 这里采用 best-effort：失败也不影响启动。
        runCatching {
            oldPrefs.edit().clear().commit()
            if (!oldPrefsFile.delete()) {
                Timber.tag(TAG).i("Old default preferences file not deleted: %s", oldPrefsFile.absolutePath)
            }
        }.onFailure { t ->
            Timber.tag(TAG).w(t, "Failed to cleanup old default preferences")
        }
    }
}
