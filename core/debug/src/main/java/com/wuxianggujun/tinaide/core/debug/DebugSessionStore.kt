package com.wuxianggujun.tinaide.core.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 保存当前调试会话的描述信息，供 UI 层订阅展示。
 */
class DebugSessionStore {

    private val _descriptor = MutableStateFlow<DebugSessionScaffold.Descriptor?>(null)
    val descriptor: StateFlow<DebugSessionScaffold.Descriptor?> = _descriptor.asStateFlow()

    fun update(descriptor: DebugSessionScaffold.Descriptor) {
        _descriptor.value = descriptor
    }

    fun clear() {
        _descriptor.value = null
    }
}
