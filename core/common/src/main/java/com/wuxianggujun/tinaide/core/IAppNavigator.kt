package com.wuxianggujun.tinaide.core

import android.content.Context

/**
 * 应用级导航接口
 *
 * 供 feature 模块调用，避免直接依赖 app 模块中的 Activity 类。
 * 实现在 app 模块的 Koin `appModule` 中注册。
 */
interface IAppNavigator {
    /** 导航到项目管理器（清空返回栈） */
    fun navigateToProjectManager(context: Context)

    /** 打开终端 */
    fun navigateToTerminal(context: Context, workDir: String)
}
