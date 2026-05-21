package com.wuxianggujun.tinaide.plugin.script.api

import com.wuxianggujun.tinaide.core.commands.HostCommandExecutor
import java.util.concurrent.atomic.AtomicReference

/**
 * 宿主命令执行器持有者。
 *
 * 由 app 层在主工作区生命周期内注入具体实现，供脚本插件通过
 * `tina.commands.execute()` 调用现有宿主命令能力。
 */
object PluginHostCommandExecutorHolder {
    private val executorRef = AtomicReference<HostCommandExecutor?>(null)

    fun set(executor: HostCommandExecutor) {
        executorRef.set(executor)
    }

    fun get(): HostCommandExecutor? = executorRef.get()

    fun clear() {
        executorRef.set(null)
    }
}
