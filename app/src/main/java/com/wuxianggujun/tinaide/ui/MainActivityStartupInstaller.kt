package com.wuxianggujun.tinaide.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.wuxianggujun.tinaide.service.EditorKeepAliveService

/**
 * 收口 MainActivity 启动阶段的宿主编排，避免 onCreate 继续膨胀。
 */
internal fun installMainActivityStartup(
    activity: ComponentActivity,
    actionsDelegate: MainActivityActionsDelegate,
    compileDelegate: MainActivityCompileDelegate,
    installContent: () -> Unit,
) {
    activity.enableEdgeToEdge()

    // 启动前台服务防止应用被杀死
    EditorKeepAliveService.start(activity)

    actionsDelegate.registerObservers()
    compileDelegate.registerObservers()
    installContent()
}
