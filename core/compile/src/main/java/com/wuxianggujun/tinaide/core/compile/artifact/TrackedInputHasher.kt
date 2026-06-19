package com.wuxianggujun.tinaide.core.compile.artifact

import java.io.File
import java.security.MessageDigest

internal object TrackedInputHasher {

    fun hashFiles(files: Collection<File>, projectRoot: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        files.asSequence()
            .map { file -> TrackedInputSignature(file = file, relativePath = normalizePath(file, projectRoot)) }
            .sorted()
            .forEach { input ->
                digest.update(input.relativePath.toByteArray(Charsets.UTF_8))
                digest.update('\u0000'.code.toByte())
                digest.update(input.size.toString().toByteArray(Charsets.UTF_8))
                digest.update('\u0000'.code.toByte())
                digest.update(input.contentHash.toByteArray(Charsets.UTF_8))
                digest.update('\n'.code.toByte())
            }
        return digest.digest()
            .take(16)
            .joinToString(separator = "") { byte ->
                (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
            }
    }

    private data class TrackedInputSignature(
        val file: File,
        val relativePath: String,
    ) : Comparable<TrackedInputSignature> {
        val size: Long = file.takeIf { it.isFile }?.length() ?: -1L
        val contentHash: String = file.takeIf { it.isFile }?.let { SourceRef.hashContent(it) } ?: "<missing>"

        override fun compareTo(other: TrackedInputSignature): Int = relativePath.compareTo(other.relativePath)
    }

    private fun normalizePath(file: File, baseDir: File): String =
        file.absoluteFile.relativeToOrSelf(baseDir.absoluteFile).path.replace(File.separatorChar, '/')
}
