package com.wuxianggujun.tinaide.core.logging

import android.app.Application
import android.content.Context
import android.os.Process
import java.io.File

/**
 * Persists recently started TinaIDE process ids for later logcat collection.
 *
 * SDL/native runtime processes may exit before the user uploads logs from the
 * main process. Keeping a short-lived pid registry lets log export still query
 * logcat by pid without falling back to global logcat.
 */
object LogProcessRegistry {
    private const val REGISTRY_DIR_NAME = "log_processes"
    private const val RECORD_PREFIX = "process_"
    private const val RECORD_SUFFIX = ".tsv"
    internal const val DEFAULT_MAX_RECORD_AGE_MS = 2 * 60 * 60 * 1000L
    private const val MAX_RECORDS = 16

    fun recordCurrentProcess(context: Context) {
        recordProcess(
            context = context,
            pid = Process.myPid(),
            processName = Application.getProcessName().orEmpty()
        )
    }

    fun recordProcess(
        context: Context,
        pid: Int,
        processName: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (pid <= 0 || processName.isBlank()) return

        runCatching {
            val dir = registryDir(context)
            if (!dir.exists()) dir.mkdirs()
            val safeProcessName = processName
                .replace('\t', '_')
                .replace('\n', '_')
                .replace('\r', '_')
            recordFile(dir, pid).writeText(
                "$pid\t$nowMillis\t$safeProcessName\n",
                Charsets.UTF_8
            )
            pruneOldRecords(dir, nowMillis, DEFAULT_MAX_RECORD_AGE_MS)
        }
    }

    internal fun loadRecentRecords(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        maxAgeMillis: Long = DEFAULT_MAX_RECORD_AGE_MS
    ): List<LogProcessRecord> {
        val dir = registryDir(context)
        if (!dir.isDirectory) return emptyList()

        val records = dir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { file -> file.isFile && file.name.startsWith(RECORD_PREFIX) && file.name.endsWith(RECORD_SUFFIX) }
            .mapNotNull { file -> parseRecord(file) }
            .filter { record -> record.recordedAtMillis in (nowMillis - maxAgeMillis)..nowMillis }
            .sortedByDescending { it.recordedAtMillis }
            .distinctBy { it.pid }
            .take(MAX_RECORDS)
            .toList()

        pruneOldRecords(dir, nowMillis, maxAgeMillis)
        return records
    }

    internal fun clear(context: Context) {
        registryDir(context).deleteRecursively()
    }

    private fun registryDir(context: Context): File =
        File(context.applicationContext.filesDir, REGISTRY_DIR_NAME)

    private fun recordFile(dir: File, pid: Int): File =
        File(dir, "$RECORD_PREFIX$pid$RECORD_SUFFIX")

    private fun parseRecord(file: File): LogProcessRecord? {
        val parts = runCatching { file.readText(Charsets.UTF_8).trim().split('\t', limit = 3) }
            .getOrNull()
            ?: return null
        if (parts.size != 3) return null
        val pid = parts[0].toIntOrNull() ?: return null
        val recordedAtMillis = parts[1].toLongOrNull() ?: return null
        val processName = parts[2].takeIf { it.isNotBlank() } ?: return null
        return LogProcessRecord(pid, processName, recordedAtMillis)
    }

    private fun pruneOldRecords(dir: File, nowMillis: Long, maxAgeMillis: Long) {
        dir.listFiles()
            .orEmpty()
            .filter { file ->
                val record = parseRecord(file)
                record == null || record.recordedAtMillis !in (nowMillis - maxAgeMillis)..nowMillis
            }
            .forEach { file -> runCatching { file.delete() } }
    }
}

internal data class LogProcessRecord(
    val pid: Int,
    val processName: String,
    val recordedAtMillis: Long
)
