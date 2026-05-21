package com.wuxianggujun.tinaide.storage

import timber.log.Timber
import java.io.File

/**
 * 项目目录结构管理
 *
 * 统一管理 TinaIDE 在项目中创建的所有目录和文件路径。
 *
 * 目录结构：
 * ```
 * <project_root>/
 *   .tinaide/
 *     artifacts/          # 导出的构建产物（按 variant 分子目录）
 *     state/              # 状态文件
 *       bookmarks.json
 *       terminal_state.json
 *       editor_state.json
 *     cache/              # 缓存文件（可删除）
 *     config/             # 项目级配置（预留）
 *     apk-export/         # APK 打包相关
 *       permissions.json
 *       signing.properties
 *       icons/
 *       runtime-libs/
 *     keystore/           # APK 打包用 keystore
 * ```
 */
object ProjectDirStructure {
    private const val TAG = "ProjectDirStructure"

    // 目录名称常量
    private const val TINAIDE_DIR = ".tinaide"
    private const val ARTIFACTS_DIR = "artifacts"
    private const val STATE_DIR = "state"
    private const val CACHE_DIR = "cache"
    private const val CONFIG_DIR = "config"
    private const val APK_EXPORT_DIR = "apk-export"
    private const val APK_ICONS_DIR = "icons"
    private const val APK_RUNTIME_LIBS_DIR = "runtime-libs"
    private const val KEYSTORE_DIR = "keystore"

    // 状态文件名称
    private const val BOOKMARKS_FILE = "bookmarks.json"
    private const val TERMINAL_STATE_FILE = "terminal_state.json"
    private const val EDITOR_STATE_FILE = "editor_state.json"

    // apk-export 下的文件
    private const val APK_PERMISSIONS_FILE = "permissions.json"
    private const val APK_SIGNING_FILE = "signing.properties"

    // 旧版遗留路径（仅用于迁移）
    private const val LEGACY_APK_SIGNING_FILE = "apk-signing.properties"
    
    /**
     * 获取 .tinaide 根目录
     */
    fun getTinaideDir(projectPath: String): File {
        return File(projectPath, TINAIDE_DIR)
    }
    
    /**
     * 获取状态目录
     */
    fun getStateDir(projectPath: String): File {
        return File(getTinaideDir(projectPath), STATE_DIR)
    }

    /**
     * 获取构建产物导出目录。
     */
    fun getArtifactsDir(projectPath: String): File {
        return File(getTinaideDir(projectPath), ARTIFACTS_DIR)
    }
    
    /**
     * 获取缓存目录
     */
    fun getCacheDir(projectPath: String): File {
        return File(getTinaideDir(projectPath), CACHE_DIR)
    }
    
    /**
     * 获取配置目录
     */
    fun getConfigDir(projectPath: String): File {
        return File(getTinaideDir(projectPath), CONFIG_DIR)
    }

    /**
     * 获取 APK 导出配置目录（permissions.json / signing.properties / icons/）
     */
    fun getApkExportDir(projectPath: String): File {
        return File(getTinaideDir(projectPath), APK_EXPORT_DIR)
    }

    /**
     * 获取 APK 导出图标目录
     */
    fun getApkExportIconsDir(projectPath: String): File {
        return File(getApkExportDir(projectPath), APK_ICONS_DIR)
    }

    /**
     * 获取 APK 导出附加运行库目录
     */
    fun getApkExportRuntimeLibsDir(projectPath: String): File {
        return File(getApkExportDir(projectPath), APK_RUNTIME_LIBS_DIR)
    }

    /**
     * 获取 APK 权限记忆文件
     */
    fun getApkPermissionsFile(projectPath: String): File {
        return File(getApkExportDir(projectPath), APK_PERMISSIONS_FILE)
    }

    /**
     * 获取 APK 自定义签名记忆文件（含密码，勿提交）
     */
    fun getApkSigningPropertiesFile(projectPath: String): File {
        return File(getApkExportDir(projectPath), APK_SIGNING_FILE)
    }

    /**
     * 获取 APK 打包用 keystore 目录
     */
    fun getKeystoreDir(projectPath: String): File {
        return File(getTinaideDir(projectPath), KEYSTORE_DIR)
    }
    
    /**
     * 获取书签文件路径
     */
    fun getBookmarksFile(projectPath: String): File {
        return File(getStateDir(projectPath), BOOKMARKS_FILE)
    }
    
    /**
     * 获取终端状态文件路径
     */
    fun getTerminalStateFile(projectPath: String): File {
        return File(getStateDir(projectPath), TERMINAL_STATE_FILE)
    }
    
    /**
     * 获取编辑器状态文件路径
     */
    fun getEditorStateFile(projectPath: String): File {
        return File(getStateDir(projectPath), EDITOR_STATE_FILE)
    }
    
    /**
     * 确保状态目录存在
     * 
     * @return true 如果目录存在或创建成功，false 如果创建失败
     */
    fun ensureStateDir(projectPath: String): Boolean {
        return ensureDirectory(getStateDir(projectPath), "state")
    }

    /**
     * 确保构建产物目录存在
     */
    fun ensureArtifactsDir(projectPath: String): Boolean {
        return ensureDirectory(getArtifactsDir(projectPath), "artifacts")
    }
    
    /**
     * 确保缓存目录存在
     */
    fun ensureCacheDir(projectPath: String): Boolean {
        return ensureDirectory(getCacheDir(projectPath), "cache")
    }
    
    /**
     * 清理缓存目录
     */
    fun clearCache(projectPath: String): Boolean {
        val dir = getCacheDir(projectPath)
        if (!dir.exists()) return true

        return runCatching {
            dir.deleteRecursively()
            Timber.tag(TAG).i("Cleared cache directory: ${dir.absolutePath}")
            true
        }.getOrElse { e ->
            Timber.tag(TAG).e(e, "Failed to clear cache directory: ${dir.absolutePath}")
            false
        }
    }

    /**
     * 把旧版遗留的 `.tinaide/apk-signing.properties` 迁到 `.tinaide/apk-export/signing.properties`。
     *
     * 返回：
     * - true  发生过一次迁移
     * - false 新路径已存在、旧路径不存在、或迁移失败（降级保留旧文件供下次重试）
     *
     * 安全保证：copy 先于 delete；若 copy 失败则旧文件保留不变，用户下次打包仍能读到。
     */
    fun migrateLegacyApkSigningPropertiesIfNeeded(projectPath: String): Boolean {
        val newFile = getApkSigningPropertiesFile(projectPath)
        if (newFile.exists()) return false

        val legacyFile = File(getTinaideDir(projectPath), LEGACY_APK_SIGNING_FILE)
        if (!legacyFile.isFile) return false

        return runCatching {
            newFile.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
                    error("Failed to create parent directory: ${parent.absolutePath}")
                }
            }
            legacyFile.copyTo(newFile, overwrite = false)
            if (!legacyFile.delete()) {
                Timber.tag(TAG).w(
                    "Migrated apk-signing.properties but failed to delete legacy file: %s",
                    legacyFile.absolutePath
                )
            }
            Timber.tag(TAG).i(
                "Migrated legacy apk-signing.properties -> %s",
                newFile.absolutePath
            )
            true
        }.getOrElse { e ->
            Timber.tag(TAG).w(e, "Failed to migrate legacy apk-signing.properties; leaving legacy file in place")
            false
        }
    }

    private fun ensureDirectory(dir: File, kind: String): Boolean {
        if (dir.exists()) {
            if (dir.isDirectory) return true
            Timber.tag(TAG).e("%s path exists but is not a directory: %s", kind, dir.absolutePath)
            return false
        }

        val created = dir.mkdirs()
        if (created) {
            Timber.tag(TAG).d("Created %s directory: %s", kind, dir.absolutePath)
            return true
        }

        // 并发场景下，可能其它线程已成功创建目录，此时也应视为成功。
        if (dir.isDirectory) {
            return true
        }

        Timber.tag(TAG).e("Failed to create %s directory: %s", kind, dir.absolutePath)
        return false
    }
}
