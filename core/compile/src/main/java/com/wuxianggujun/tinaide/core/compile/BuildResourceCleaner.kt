package com.wuxianggujun.tinaide.core.compile

import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns transient build resources under app cache. Persistent buildDir artifacts are not touched.
 */
internal object BuildResourceCleaner {
    private const val TMP_ROOT_DIR = "tina-build-tmp"
    private const val DEFAULT_MAX_AGE_MS = 24L * 60L * 60L * 1000L
    private const val DEFAULT_MAX_RETAINED_DIRS = 64
    private val sequence = AtomicLong(0)

    data class CleanupSummary(
        val deletedCount: Int,
        val failedCount: Int,
    )

    fun createCommandTempDir(cacheDir: File, owner: String): File {
        val root = File(cacheDir, TMP_ROOT_DIR).apply { mkdirs() }
        pruneStaleCommandTempDirs(cacheDir)
        val dirName = "${owner.sanitizeOwner()}-${System.currentTimeMillis()}-${sequence.incrementAndGet()}"
        return File(root, dirName).apply {
            mkdirs()
            BuildDiagnosticsLog.d { "build temp create path=$absolutePath" }
        }
    }

    fun cleanupCommandTempDir(tempDir: File): Boolean {
        if (!isManagedCommandTempDir(tempDir)) {
            BuildDiagnosticsLog.w { "build temp cleanup rejected path=${tempDir.absolutePath}" }
            return false
        }
        if (!tempDir.exists()) return true
        val deleted = tempDir.deleteRecursively()
        BuildDiagnosticsLog.d {
            "build temp cleanup path=${tempDir.absolutePath} deleted=$deleted"
        }
        return deleted
    }

    fun pruneStaleCommandTempDirs(
        cacheDir: File,
        nowMs: Long = System.currentTimeMillis(),
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
        maxRetainedDirs: Int = DEFAULT_MAX_RETAINED_DIRS,
    ): CleanupSummary = pruneStaleCommandTempDirsInRoot(
        root = File(cacheDir, TMP_ROOT_DIR),
        nowMs = nowMs,
        maxAgeMs = maxAgeMs,
        maxRetainedDirs = maxRetainedDirs,
    )

    internal fun pruneStaleCommandTempDirsInRoot(
        root: File,
        nowMs: Long,
        maxAgeMs: Long,
        maxRetainedDirs: Int,
    ): CleanupSummary {
        if (!root.isDirectory) return CleanupSummary(deletedCount = 0, failedCount = 0)
        val dirs = root.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .sortedByDescending { it.lastModified() }

        var deletedCount = 0
        var failedCount = 0
        dirs.forEachIndexed { index, dir ->
            val staleByAge = nowMs - dir.lastModified() > maxAgeMs
            val staleByCount = index >= maxRetainedDirs
            if (!staleByAge && !staleByCount) return@forEachIndexed
            if (cleanupCommandTempDir(dir)) {
                deletedCount++
            } else {
                failedCount++
            }
        }
        if (deletedCount > 0 || failedCount > 0) {
            BuildDiagnosticsLog.i {
                "build temp prune root=${root.absolutePath} deleted=$deletedCount failed=$failedCount"
            }
        }
        return CleanupSummary(deletedCount = deletedCount, failedCount = failedCount)
    }

    private fun isManagedCommandTempDir(tempDir: File): Boolean =
        tempDir.parentFile?.name == TMP_ROOT_DIR

    private fun String.sanitizeOwner(): String =
        lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-')
            .ifBlank { "command" }
}
