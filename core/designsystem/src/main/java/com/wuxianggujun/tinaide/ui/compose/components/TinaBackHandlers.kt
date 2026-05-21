package com.wuxianggujun.tinaide.ui.compose.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

/**
 * 单个屏内返回动作。
 *
 * 适用于列表/详情、普通态/搜索态这类页面内部状态切换。
 */
class TinaBackAction internal constructor(
    internal val enabled: Boolean,
    internal val onBack: () -> Unit,
)

fun tinaBackAction(
    enabled: Boolean,
    onBack: () -> Unit,
): TinaBackAction = TinaBackAction(
    enabled = enabled,
    onBack = onBack,
)

/**
 * 按传入顺序处理系统返回，越靠前优先级越高。
 */
@Composable
fun TinaBackHandlers(vararg actions: TinaBackAction) {
    val activeAction = actions.firstOrNull { it.enabled }
    val currentOnBack = rememberUpdatedState(activeAction?.onBack)

    BackHandler(enabled = activeAction != null) {
        currentOnBack.value?.invoke()
    }
}
