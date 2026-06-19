package com.wuxianggujun.tinaide.core.compile.artifact

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable

/**
 * 产物生成时参与增量判定的输入文件引用。
 *
 * [relativePath] 相对项目根目录,保证跨机/跨路径可读性。
 * [mtime] 与 [size] 用于增量判定的二级校验(主校验是 BuildFingerprint)。
 */
@Serializable
data class SourceRef(
    val relativePath: String,
    val mtime: Long,
    val size: Long,
    val contentHash: String? = null,
) {
    companion object {
        fun capture(file: File, projectRoot: File): SourceRef = SourceRef(
            relativePath = file.toRelativeString(projectRoot),
            mtime = file.lastModified(),
            size = file.length(),
            contentHash = hashContent(file),
        )

        internal fun hashContent(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest()
                .take(16)
                .joinToString(separator = "") { byte ->
                    (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
                }
        }
    }
}
