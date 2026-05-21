package com.wuxianggujun.tinaide.plugin.script.api

import com.wuxianggujun.tinaide.plugin.PluginLogManager
import com.wuxianggujun.tinaide.plugin.script.ScriptPluginRuntime
import party.iroiro.luajava.Lua

/**
 * Exposes `tina.log.debug/info/warn/error` to Lua plugins.
 * Also redirects the global `print()` to `tina.log.info`.
 */
class LogApiModule(private val logManager: PluginLogManager) : PluginApiModule {
    override val namespace = "log"

    private var runtime: ScriptPluginRuntime? = null

    override fun register(runtime: ScriptPluginRuntime, lua: Lua) {
        this.runtime = runtime
        val pluginId = runtime.pluginId
        val pluginName = runtime.pluginName

        lua.push { L: Lua ->
            val msg = L.getStringArg(1) ?: ""
            logManager.debug(pluginId, pluginName, msg)
            0
        }
        lua.setField(-2, "debug")

        lua.push { L: Lua ->
            val msg = L.getStringArg(1) ?: ""
            logManager.info(pluginId, pluginName, msg)
            0
        }
        lua.setField(-2, "info")

        lua.push { L: Lua ->
            val msg = L.getStringArg(1) ?: ""
            logManager.warn(pluginId, pluginName, msg)
            0
        }
        lua.setField(-2, "warn")

        lua.push { L: Lua ->
            val msg = L.getStringArg(1) ?: ""
            logManager.error(pluginId, pluginName, msg)
            0
        }
        lua.setField(-2, "error")
    }

    override fun unregister() {
        runtime = null
    }
}
