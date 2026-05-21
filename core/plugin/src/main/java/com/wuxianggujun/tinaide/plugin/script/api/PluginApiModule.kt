package com.wuxianggujun.tinaide.plugin.script.api

import android.content.Context
import com.wuxianggujun.tinaide.plugin.script.ScriptPluginRuntime
import party.iroiro.luajava.Lua

/**
 * A plugin API module that exposes functions under `tina.<namespace>`.
 *
 * The registry creates a Lua table for each module, passes it via [register],
 * and mounts it at `tina.<namespace>` automatically.
 * Implementations must NOT call `lua.setGlobal()`.
 */
interface PluginApiModule {
    /** Sub-key under the `tina` global table (e.g. "editor", "fs", "log"). */
    val namespace: String

    /**
     * Populate the table currently on top of the Lua stack with API functions.
     * The table is already created by the registry; just push functions and set fields.
     */
    fun register(runtime: ScriptPluginRuntime, lua: Lua)

    /** Release resources held by this module. */
    fun unregister()
}

/**
 * Central registry that collects all [PluginApiModule]s and mounts them
 * under a single `tina` global table when a script plugin is initialised.
 */
class PluginApiRegistry(private val context: Context) {
    private val modules = mutableMapOf<String, PluginApiModule>()

    fun registerModule(module: PluginApiModule) {
        modules[module.namespace] = module
    }

    fun unregisterModule(namespace: String) {
        modules.remove(namespace)
    }

    /**
     * Build the `tina` global table and populate every registered module.
     *
     * Stack layout while iterating:
     *   [-2] tina table
     *   [-1] current module sub-table
     *
     * After the call the stack is clean and `tina` is available globally.
     */
    fun initializeForRuntime(runtime: ScriptPluginRuntime) {
        val lua = runtime.getLuaState() ?: return

        // tina = {}
        lua.createTable(0, modules.size + 1)

        modules.values.forEach { module ->
            lua.createTable(0, 12)
            module.register(runtime, lua)
            lua.setField(-2, module.namespace)
        }

        // tina.pluginId = "<id>"
        lua.push(runtime.pluginId)
        lua.setField(-2, "pluginId")

        // tina.apiVersion = 1
        lua.push(runtime.apiVersion)
        lua.setField(-2, "apiVersion")

        lua.setGlobal("tina")
    }

    fun cleanup() {
        modules.values.forEach { it.unregister() }
        modules.clear()
    }

    companion object {
        fun createDefaultRegistry(
            context: Context,
            projectRootProvider: () -> String? = { null }
        ): PluginApiRegistry {
            val workspaceFileAccess = PluginWorkspaceFileAccess(projectRootProvider)
            return PluginApiRegistry(context).apply {
                registerModule(EditorApiModule())
                registerModule(UiApiModule(context))
                registerModule(StorageApiModule(context))
                registerModule(CommandsApiModule(projectRootProvider))
                registerModule(EventsApiModule())
                registerModule(DiagnosticsApiModule())
                registerModule(WorkspaceApiModule(workspaceFileAccess))
                registerModule(FileApiModule(workspaceFileAccess))
                registerModule(ClipboardApiModule(context))
                registerModule(NetworkApiModule(context))
                registerModule(DatabaseApiModule(context))
            }
        }
    }
}
