package com.wuxianggujun.tinaide.plugin

import android.content.Context
import android.content.SharedPreferences
import com.wuxianggujun.tinaide.core.serialization.JsonSerializer
import kotlinx.serialization.json.JsonElement

class PluginConfigurationStore private constructor(
    context: Context,
) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun getValue(
        manifest: PluginManifest,
        propertyKey: String,
    ): JsonElement? {
        val property = PluginConfigurationSchema.resolveProperty(manifest, propertyKey) ?: return null
        val storedValue = prefs.getString(buildPreferenceKey(manifest.id, propertyKey), null)
            ?.let(JsonSerializer::parseToJsonElementOrNull)
            ?.let { value -> PluginConfigurationSchema.normalizeValue(property, value) }
        return storedValue ?: property.defaultValue
    }

    fun setValue(
        manifest: PluginManifest,
        propertyKey: String,
        value: JsonElement,
    ): Boolean {
        val property = PluginConfigurationSchema.resolveProperty(manifest, propertyKey) ?: return false
        val normalizedValue = PluginConfigurationSchema.normalizeValue(property, value) ?: return false
        prefs.edit()
            .putString(buildPreferenceKey(manifest.id, propertyKey), normalizedValue.toString())
            .apply()
        return true
    }

    fun resetValue(
        manifest: PluginManifest,
        propertyKey: String,
    ): Boolean {
        if (PluginConfigurationSchema.resolveProperty(manifest, propertyKey) == null) return false
        prefs.edit()
            .remove(buildPreferenceKey(manifest.id, propertyKey))
            .apply()
        return true
    }

    fun clearPlugin(pluginId: String) {
        val prefix = "$pluginId:"
        val keys = prefs.all.keys.filter { key -> key.startsWith(prefix) }
        if (keys.isEmpty()) return
        prefs.edit().apply {
            keys.forEach(::remove)
        }.apply()
    }

    companion object {
        private const val PREFS_NAME = "tina_plugin_configuration"

        @Volatile
        private var instance: PluginConfigurationStore? = null

        fun getInstance(context: Context): PluginConfigurationStore {
            return instance ?: synchronized(this) {
                instance ?: PluginConfigurationStore(context).also { store -> instance = store }
            }
        }

        internal fun buildPreferenceKey(
            pluginId: String,
            propertyKey: String,
        ): String = "$pluginId:$propertyKey"
    }
}
