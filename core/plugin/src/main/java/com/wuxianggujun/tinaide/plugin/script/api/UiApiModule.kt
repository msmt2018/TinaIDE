package com.wuxianggujun.tinaide.plugin.script.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import com.wuxianggujun.tinaide.plugin.script.PluginPermission
import com.wuxianggujun.tinaide.plugin.script.ScriptPluginRuntime
import party.iroiro.luajava.Lua

class UiApiModule(private val context: Context) : PluginApiModule {
    override val namespace = "ui"

    private var runtime: ScriptPluginRuntime? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun register(runtime: ScriptPluginRuntime, lua: Lua) {
        this.runtime = runtime

        lua.push { L: Lua ->
            withPermission(this.runtime, L, namespace, "showMessage", PluginPermission.UI_NOTIFICATION) {
                val message = L.getStringArg(1) ?: ""
                mainHandler.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
                0
            }
        }
        lua.setField(-2, "showMessage")

        lua.push { L: Lua ->
            withPermission(this.runtime, L, namespace, "showWarning", PluginPermission.UI_NOTIFICATION) {
                val message = L.getStringArg(1) ?: ""
                mainHandler.post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
                0
            }
        }
        lua.setField(-2, "showWarning")

        lua.push { L: Lua ->
            withPermission(this.runtime, L, namespace, "showError", PluginPermission.UI_NOTIFICATION) {
                val message = L.getStringArg(1) ?: ""
                mainHandler.post {
                    Toast.makeText(
                        context,
                        Strings.plugin_ui_error_prefix.strOr(context, message),
                        Toast.LENGTH_LONG
                    ).show()
                }
                0
            }
        }
        lua.setField(-2, "showError")
    }

    override fun unregister() {
        runtime = null
    }
}
