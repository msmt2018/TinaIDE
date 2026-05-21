package com.wuxianggujun.tinaide.core.compile.artifact

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
)
