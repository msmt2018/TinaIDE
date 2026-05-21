package com.wuxianggujun.tinaide.core.packages

import com.wuxianggujun.tinaide.core.packages.model.Platform
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

/**
 * 依赖包变更事件中心
 *
 * 用途：
 * - 包安装/卸载成功后广播事件；
 * - 编辑器侧据此触发 compile_commands 和 clangd 自动刷新。
 */
object PackageDependencyEvents {
    private const val TAG = "PkgDependencyEvents"

    enum class ChangeAction {
        INSTALLED,
        UNINSTALLED
    }

    data class DependencyChangedEvent(
        val packageId: String,
        val platform: Platform,
        val action: ChangeAction,
        val version: String? = null,
        val timestampMillis: Long = System.currentTimeMillis(),
    )

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private val _events = MutableSharedFlow<DependencyChangedEvent>(
        extraBufferCapacity = 32
    )
    val events: SharedFlow<DependencyChangedEvent> = _events.asSharedFlow()

    fun notifyChanged(event: DependencyChangedEvent) {
        _revision.update { it + 1L }
        val emitted = _events.tryEmit(event)
        Timber.tag(TAG).i(
            "Dependency changed: action=%s package=%s platform=%s version=%s revision=%d emitted=%s",
            event.action,
            event.packageId,
            event.platform,
            event.version ?: "-",
            _revision.value,
            emitted
        )
    }
}
