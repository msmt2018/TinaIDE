package com.wuxianggujun.tinaide.ui

import java.io.File

/**
 * 提供给宿主命令执行器的能力接口（避免在 executor 内引入过多 Activity 细节）。
 */
interface MainActivityExternalFileLauncher {
    fun openWithExternalApp(file: File)
    fun shareFileOrDirectory(file: File)
}
