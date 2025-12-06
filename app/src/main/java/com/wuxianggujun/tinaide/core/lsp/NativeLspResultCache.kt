package com.wuxianggujun.tinaide.core.lsp

import android.os.SystemClock
import com.wuxianggujun.tinaide.lsp.model.CompletionResult
import java.util.LinkedHashMap

/**
 * Native LSP 结果缓存，当前仅缓存补全结果，用于避免相同上下文反复命中 clangd。
 * 符合 Stage4 ResultCache 规划：热点上下文命中缓存能显著降低第三次补全超时。
 */
object NativeLspResultCache {

    private const val COMPLETION_TTL_MS = 60_000L
    private const val MAX_COMPLETION_ENTRIES = 128

    private data class CompletionKey(
        val filePath: String,
        val line: Int,
        val identifierStart: Int,
        val prefixHash: Int
    )

    private data class CompletionEntry(
        val prefixSnapshot: String,
        val timestampMs: Long,
        val result: CompletionResult
    )

    private val completionCache =
        object : LinkedHashMap<CompletionKey, CompletionEntry>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CompletionKey, CompletionEntry>?): Boolean {
                return size > MAX_COMPLETION_ENTRIES
            }
        }

    @Synchronized
    fun getCompletion(
        filePath: String,
        line: Int,
        identifierStart: Int,
        prefixSnapshot: String
    ): CompletionResult? {
        val key = CompletionKey(filePath, line, identifierStart, prefixSnapshot.hashCode())
        val entry = completionCache[key] ?: return null
        val now = SystemClock.elapsedRealtime()
        if (entry.prefixSnapshot != prefixSnapshot || now - entry.timestampMs > COMPLETION_TTL_MS) {
            completionCache.remove(key)
            return null
        }
        return entry.result
    }

    @Synchronized
    fun putCompletion(
        filePath: String,
        line: Int,
        identifierStart: Int,
        prefixSnapshot: String,
        result: CompletionResult
    ) {
        val key = CompletionKey(filePath, line, identifierStart, prefixSnapshot.hashCode())
        completionCache[key] = CompletionEntry(prefixSnapshot, SystemClock.elapsedRealtime(), result)
    }

    @Synchronized
    fun invalidateFile(filePath: String) {
        val iterator = completionCache.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().key.filePath == filePath) {
                iterator.remove()
            }
        }
    }
}
