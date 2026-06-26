package com.wuxianggujun.tinaide.ui.compose.viewer

import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class HexFileDataManager(
    private val file: File,
    private val maxCachedChunks: Int = DEFAULT_MAX_CACHED_CHUNKS
) {
    private val lock = Any()
    private val loadingChunks = mutableSetOf<Long>()
    private val chunkCache = object : LinkedHashMap<Long, ByteArray>(maxCachedChunks, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?): Boolean = size > maxCachedChunks
    }

    private var knownFileSize: Long = file.length().coerceAtLeast(0L)

    val fileSize: Long
        get() = knownFileSize

    val totalRows: Int
        get() = if (knownFileSize <= 0L) {
            0
        } else {
            ((knownFileSize + BYTES_PER_ROW - 1) / BYTES_PER_ROW)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }

    fun refreshFileSize(): Long {
        knownFileSize = file.length().coerceAtLeast(0L)
        return knownFileSize
    }

    fun getRowOffset(rowIndex: Int): Long = rowIndex.coerceAtLeast(0).toLong() * BYTES_PER_ROW

    fun getRowIndexForOffset(offset: Long): Int {
        if (knownFileSize <= 0L) return 0
        val coercedOffset = offset.coerceIn(0L, knownFileSize - 1)
        return (coercedOffset / BYTES_PER_ROW).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun coerceOffset(offset: Long): Long {
        if (knownFileSize <= 0L) return 0L
        return offset.coerceIn(0L, knownFileSize - 1)
    }

    fun canWrite(): Boolean = file.exists() && file.isFile && file.canWrite()

    fun getCachedRow(rowIndex: Int): HexByteRow? {
        if (rowIndex < 0 || rowIndex >= totalRows) return null
        val rowOffset = getRowOffset(rowIndex)
        val chunkStart = chunkStartFor(rowOffset)
        val chunk = synchronized(lock) {
            chunkCache[chunkStart]?.copyOf()
        } ?: return null

        val localOffset = (rowOffset - chunkStart).toInt()
        if (localOffset >= chunk.size) return null
        val byteCount = minOf(BYTES_PER_ROW, chunk.size - localOffset)
        if (byteCount <= 0) return null
        return HexByteRow.fromBytes(rowOffset, chunk.copyOfRange(localOffset, localOffset + byteCount))
    }

    fun getCachedByte(offset: Long): Byte? {
        if (knownFileSize <= 0L || offset !in 0 until knownFileSize) return null
        val chunkStart = chunkStartFor(offset)
        val localOffset = (offset - chunkStart).toInt()
        return synchronized(lock) {
            chunkCache[chunkStart]?.getOrNull(localOffset)
        }
    }

    suspend fun loadChunkForRow(rowIndex: Int): Boolean = loadChunkIfNeeded(getRowOffset(rowIndex))

    suspend fun preloadAroundRow(rowIndex: Int, rangeChunks: Int = DEFAULT_PRELOAD_RANGE): Boolean {
        if (knownFileSize <= 0L) return false
        val centerOffset = getRowOffset(rowIndex)
        val centerChunkStart = chunkStartFor(centerOffset)
        var loadedAny = false

        for (distance in -rangeChunks..rangeChunks) {
            val chunkStart = centerChunkStart + distance * CHUNK_SIZE
            if (chunkStart in 0 until knownFileSize) {
                loadedAny = loadChunkIfNeeded(chunkStart) || loadedAny
            }
        }

        return loadedAny
    }

    suspend fun writeByte(offset: Long, value: Byte) {
        writePatches(
            listOf(
                HexPatch(
                    offset = offset,
                    originalByte = readByte(offset),
                    newByte = value
                )
            )
        )
    }

    suspend fun readByte(offset: Long): Byte {
        val bytes = readBytes(offset = offset, byteCount = 1)
        check(bytes.isNotEmpty()) { "Cannot read byte at offset $offset." }
        return bytes[0]
    }

    suspend fun readBytes(offset: Long, byteCount: Int): ByteArray {
        if (knownFileSize <= 0L || byteCount <= 0) return ByteArray(0)
        val targetOffset = coerceOffset(offset)
        val safeByteCount = minOf(byteCount.toLong(), knownFileSize - targetOffset)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        if (safeByteCount <= 0) return ByteArray(0)

        return withContext(Dispatchers.IO) {
            RandomAccessFile(file, "r").use { randomAccessFile ->
                val buffer = ByteArray(safeByteCount)
                randomAccessFile.seek(targetOffset)
                val bytesRead = randomAccessFile.read(buffer)
                if (bytesRead <= 0) ByteArray(0) else buffer.copyOf(bytesRead)
            }
        }
    }

    suspend fun writePatches(patches: List<HexPatch>) {
        if (patches.isEmpty()) return
        check(knownFileSize > 0L) { "Cannot write an empty file." }
        check(canWrite()) { "File is read-only." }

        val sortedPatches = patches
            .filter { it.offset in 0 until knownFileSize }
            .sortedBy { it.offset }
        if (sortedPatches.isEmpty()) return

        withContext(Dispatchers.IO) {
            RandomAccessFile(file, "rw").use { randomAccessFile ->
                sortedPatches.forEach { patch ->
                    randomAccessFile.seek(patch.offset)
                    randomAccessFile.write(byteArrayOf(patch.newByte))
                }
            }
        }

        sortedPatches.forEach { patch ->
            updateCachedByte(offset = patch.offset, value = patch.newByte)
        }
    }

    private fun updateCachedByte(offset: Long, value: Byte) {
        val chunkStart = chunkStartFor(offset)
        val localOffset = (offset - chunkStart).toInt()
        synchronized(lock) {
            val cachedChunk = chunkCache[chunkStart]
            if (cachedChunk != null && localOffset in cachedChunk.indices) {
                cachedChunk[localOffset] = value
            } else {
                chunkCache.remove(chunkStart)
            }
        }
    }

    fun clearCache() {
        synchronized(lock) {
            chunkCache.clear()
            loadingChunks.clear()
        }
    }

    private suspend fun loadChunkIfNeeded(offset: Long): Boolean {
        if (knownFileSize <= 0L) return false
        val chunkStart = chunkStartFor(offset)
        if (chunkStart !in 0 until knownFileSize) return false

        synchronized(lock) {
            if (chunkCache.containsKey(chunkStart)) return false
            if (!loadingChunks.add(chunkStart)) return false
        }

        return try {
            val bytesToRead = minOf(CHUNK_SIZE.toLong(), knownFileSize - chunkStart).toInt()
            if (bytesToRead <= 0) return false

            val bytes = withContext(Dispatchers.IO) {
                RandomAccessFile(file, "r").use { randomAccessFile ->
                    val buffer = ByteArray(bytesToRead)
                    randomAccessFile.seek(chunkStart)
                    val bytesRead = randomAccessFile.read(buffer)
                    if (bytesRead <= 0) ByteArray(0) else buffer.copyOf(bytesRead)
                }
            }

            synchronized(lock) {
                chunkCache[chunkStart] = bytes
            }
            true
        } finally {
            synchronized(lock) {
                loadingChunks.remove(chunkStart)
            }
        }
    }

    private fun chunkStartFor(offset: Long): Long = (offset / CHUNK_SIZE) * CHUNK_SIZE

    companion object {
        const val BYTES_PER_ROW = 16
        const val VISUAL_BYTES_PER_ROW = 8
        const val CHUNK_SIZE = 4096
        private const val DEFAULT_MAX_CACHED_CHUNKS = 128
        private const val DEFAULT_PRELOAD_RANGE = 2
    }
}
