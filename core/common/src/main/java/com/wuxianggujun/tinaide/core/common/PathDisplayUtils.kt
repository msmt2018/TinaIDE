package com.wuxianggujun.tinaide.core.common

import android.content.Context
import android.os.Environment
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr

/**
 * 简化路径显示
 * - 应用私有目录显示为 "私有目录/..."
 * - 外部存储显示为 "存储/..."
 */
fun simplifyPath(path: String, context: Context): String {
    val filesDir = context.filesDir.absolutePath
    val externalStorage = Environment.getExternalStorageDirectory().absolutePath
    val privateDir = Strings.path_private_dir.strOr(context)
    val storage = Strings.path_storage.strOr(context)

    return when {
        path.startsWith(filesDir) -> privateDir + path.removePrefix(filesDir)
        path.startsWith(externalStorage) -> storage + path.removePrefix(externalStorage)
        path.startsWith("/storage/emulated/0") -> storage + path.removePrefix("/storage/emulated/0")
        else -> path
    }
}
