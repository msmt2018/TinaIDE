package com.wuxianggujun.tinaide.plugin.script.api

import com.wuxianggujun.tinaide.plugin.script.PluginPermission
import com.wuxianggujun.tinaide.plugin.script.ScriptPluginRuntime
import party.iroiro.luajava.Lua

class DiagnosticsApiModule : PluginApiModule {
    override val namespace = "diagnostics"

    private var runtime: ScriptPluginRuntime? = null

    override fun register(runtime: ScriptPluginRuntime, lua: Lua) {
        this.runtime = runtime

        lua.push { L: Lua ->
            withPermission(this.runtime, L, namespace, "get", PluginPermission.DIAGNOSTICS_READ) {
                val requestedFilePath = L.getStringArg(1)
                val snapshot = PluginDiagnosticsProviderHolder.get()
                    ?.getDiagnostics(requestedFilePath)
                    ?: PluginDiagnosticsSnapshot.unavailable(requestedFilePath)
                L.pushDiagnosticsSnapshot(snapshot)
            }
        }
        lua.setField(-2, "get")
    }

    override fun unregister() {
        runtime = null
    }
}

private fun Lua.pushDiagnosticsSnapshot(snapshot: PluginDiagnosticsSnapshot): Int {
    createTable(0, 9)
    push(snapshot.available)
    setField(-2, "available")
    push(snapshot.totalCount)
    setField(-2, "totalCount")
    push(snapshot.errorCount)
    setField(-2, "errorCount")
    push(snapshot.warningCount)
    setField(-2, "warningCount")
    push(snapshot.infoCount)
    setField(-2, "infoCount")
    push(snapshot.hintCount)
    setField(-2, "hintCount")
    pushNullableString(snapshot.requestedFilePath)
    setField(-2, "requestedFilePath")
    pushNullableString(snapshot.error)
    setField(-2, "error")
    pushDiagnosticItems(snapshot.diagnostics)
    setField(-2, "diagnostics")
    return 1
}

private fun Lua.pushDiagnosticItems(diagnostics: List<PluginDiagnosticItem>) {
    createTable(diagnostics.size, 0)
    diagnostics.forEachIndexed { index, diagnostic ->
        createTable(0, 10)
        push(diagnostic.fileUri)
        setField(-2, "fileUri")
        push(diagnostic.filePath)
        setField(-2, "filePath")
        push(diagnostic.fileName)
        setField(-2, "fileName")
        push(diagnostic.line)
        setField(-2, "line")
        push(diagnostic.column)
        setField(-2, "column")
        push(diagnostic.endLine)
        setField(-2, "endLine")
        push(diagnostic.endColumn)
        setField(-2, "endColumn")
        push(diagnostic.message)
        setField(-2, "message")
        push(diagnostic.severity)
        setField(-2, "severity")
        pushNullableString(diagnostic.source)
        setField(-2, "source")
        pushNullableString(diagnostic.code)
        setField(-2, "code")
        rawSetI(-2, index + 1)
    }
}

private fun Lua.pushNullableString(value: String?) {
    if (value == null) {
        pushNil()
    } else {
        push(value)
    }
}
