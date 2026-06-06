package com.wuxianggujun.tinaide.ui

import android.view.KeyEvent
import com.wuxianggujun.tinaide.core.commands.HostCommandExecutor
import com.wuxianggujun.tinaide.core.commands.HostCommandInvocation
import com.wuxianggujun.tinaide.core.config.KeyboardShortcutManager
import com.wuxianggujun.tinaide.plugin.PluginKeyBindingResolver
import com.wuxianggujun.tinaide.plugin.ResolvedPluginKeyBinding

/**
 * MainActivity 的硬件键盘快捷键分发器。
 *
 * 负责把快捷键事件翻译成编辑器动作，并复用既有宿主委托完成分发。
 */
class MainActivityShortcutDispatcher {
    private var hostCommandExecutor: HostCommandExecutor? = null
    private var shortcutInvocationProvider: (() -> HostCommandInvocation)? = null
    private var dispatchPluginShortcut: ((KeyEvent) -> Boolean)? = null

    fun bind(
        hostCommandExecutor: HostCommandExecutor,
        invocationProvider: () -> HostCommandInvocation,
    ) {
        this.hostCommandExecutor = hostCommandExecutor
        shortcutInvocationProvider = invocationProvider
    }

    fun bindPluginKeyBindings(
        keyBindingsProvider: () -> List<ResolvedPluginKeyBinding>,
        invocationProvider: () -> HostCommandInvocation,
        editorFocusProvider: () -> Boolean,
        hostCommandExecutor: HostCommandExecutor,
    ) {
        dispatchPluginShortcut = { event ->
            val invocation = invocationProvider()
            val isDirty = invocation.isDirty ?: false
            val editorFocus = editorFocusProvider()

            keyBindingsProvider()
                .asSequence()
                .filter { binding ->
                    binding.matches(
                        event = event,
                        isDirty = isDirty,
                        editorFocus = editorFocus
                    )
                }
                .filter(PluginKeyBindingResolver::isCommandSupported)
                .any { binding ->
                    hostCommandExecutor.execute(
                        commandId = binding.commandId,
                        invocation = invocation
                    )
                }
        }
    }

    fun clearPluginKeyBindings() {
        dispatchPluginShortcut = null
    }

    fun dispatch(event: KeyEvent?): Boolean {
        if (event == null) return false
        val action = KeyboardShortcutManager.findActionForEvent(event)
        if (action != null) {
            val executor = hostCommandExecutor ?: return false
            val invocation = shortcutInvocationProvider?.invoke() ?: HostCommandInvocation()
            return executor.execute(action.commandId, invocation)
        }

        return dispatchPluginShortcut?.invoke(event) == true
    }

    fun clear() {
        hostCommandExecutor = null
        shortcutInvocationProvider = null
        clearPluginKeyBindings()
    }
}
