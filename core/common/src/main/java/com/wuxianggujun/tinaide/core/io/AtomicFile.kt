package com.wuxianggujun.tinaide.core.io

import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

object AtomicFile {
    private const val TAG = "AtomicFile"

    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    private fun lockFor(file: File): ReentrantLock {
        val key = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        locks[key]?.let { return it }
        val created = ReentrantLock()
        val existing = locks.putIfAbsent(key, created)
        return existing ?: created
    }

    fun write(
        targetFile: File,
        tempFileName: String = targetFile.name + ".tmp",
        writeTempFile: (File) -> Unit
    ): Boolean {
        val lock = lockFor(targetFile)
        lock.lock()
        try {
            val parent = targetFile.parentFile
            if (parent != null) {
                if (parent.exists() && !parent.isDirectory) {
                    Timber.tag(TAG).e("Parent path exists but is not a directory: %s", parent.absolutePath)
                    return false
                }
                if (!parent.exists() && !parent.mkdirs()) {
                    Timber.tag(TAG).e("Failed to create parent directory: %s", parent.absolutePath)
                    return false
                }
            }

            val tempFile = if (parent == null) File(tempFileName) else File(parent, tempFileName)
            runCatching { tempFile.delete() }

            return runCatching {
                writeTempFile(tempFile)
                if (!tempFile.exists()) {
                    error("Temp file was not created: ${tempFile.absolutePath}")
                }

                if (tempFile.renameTo(targetFile)) {
                    return@runCatching true
                }

                if (targetFile.exists() && !targetFile.delete()) {
                    Timber.tag(TAG).w("Failed to delete existing target file: %s", targetFile.absolutePath)
                }

                if (tempFile.renameTo(targetFile)) {
                    return@runCatching true
                }

                tempFile.copyTo(targetFile, overwrite = true)
                runCatching { tempFile.delete() }
                true
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "Failed to write file atomically: %s", targetFile.absolutePath)
                runCatching { tempFile.delete() }
            }.getOrDefault(false)
        } finally {
            lock.unlock()
        }
    }

    fun delete(
        targetFile: File,
        tempFileName: String = targetFile.name + ".tmp"
    ): Boolean {
        val lock = lockFor(targetFile)
        lock.lock()
        try {
            val parent = targetFile.parentFile
            val tempFile = if (parent == null) File(tempFileName) else File(parent, tempFileName)

            val targetDeleted = !targetFile.exists() || runCatching { targetFile.delete() }.getOrDefault(false)
            val tempDeleted = !tempFile.exists() || runCatching { tempFile.delete() }.getOrDefault(false)
            return targetDeleted && tempDeleted
        } finally {
            lock.unlock()
        }
    }
}

