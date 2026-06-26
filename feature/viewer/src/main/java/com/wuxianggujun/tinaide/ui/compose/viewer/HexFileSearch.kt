package com.wuxianggujun.tinaide.ui.compose.viewer

import java.io.File
import java.io.RandomAccessFile

internal fun searchInHexFile(file: File, query: String): List<Long> {
    if (query.isBlank() || !file.exists() || !file.isFile) return emptyList()

    val searchPattern = parseSearchPattern(query)
    if (searchPattern.isEmpty()) return emptyList()

    val offsets = mutableListOf<Long>()
    val bufferSize = 64 * 1024
    val buffer = ByteArray(bufferSize + searchPattern.size - 1)

    runCatching {
        RandomAccessFile(file, "r").use { randomAccessFile ->
            var fileOffset = 0L
            val fileSize = randomAccessFile.length()

            while (fileOffset < fileSize) {
                randomAccessFile.seek(fileOffset)
                val bytesRead = randomAccessFile.read(buffer)
                if (bytesRead <= 0) break

                var index = 0
                while (index <= bytesRead - searchPattern.size) {
                    if (buffer.matchesAt(index, searchPattern)) {
                        offsets.add(fileOffset + index)
                        if (offsets.size >= MAX_SEARCH_RESULTS) return offsets
                    }
                    index++
                }

                fileOffset += bufferSize
            }
        }
    }

    return offsets
}

private fun parseSearchPattern(query: String): HexSearchPattern {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return HexSearchPattern(emptyList())

    val compactHexPattern = trimmed.replace(" ", "").replace("-", "")
    val hexPairs = if (compactHexPattern.length % 2 == 0) {
        compactHexPattern.chunked(2)
    } else {
        emptyList()
    }
    val isHexSearch = hexPairs.isNotEmpty() && hexPairs.all { it.isHexPairOrWildcard() }

    if (isHexSearch) {
        return HexSearchPattern(
            hexPairs.map { pair ->
                if (pair == "??") null else pair.toInt(16)
            }
        )
    }

    return HexSearchPattern(trimmed.toByteArray(Charsets.UTF_8).map { it.toInt() and 0xFF })
}

private fun ByteArray.matchesAt(startIndex: Int, pattern: HexSearchPattern): Boolean {
    for (patternIndex in 0 until pattern.size) {
        val expectedByte = pattern.bytes[patternIndex] ?: continue
        if ((this[startIndex + patternIndex].toInt() and 0xFF) != expectedByte) return false
    }
    return true
}

private data class HexSearchPattern(
    val bytes: List<Int?>
) {
    val size: Int
        get() = bytes.size

    fun isEmpty(): Boolean = bytes.isEmpty()
}

private fun String.isHexPairOrWildcard(): Boolean = this == "??" || (length == 2 && all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })

private const val MAX_SEARCH_RESULTS = 1000
