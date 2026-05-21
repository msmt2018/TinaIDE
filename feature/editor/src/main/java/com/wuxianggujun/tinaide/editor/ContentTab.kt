package com.wuxianggujun.tinaide.editor

import java.io.File

/**
 * 内容标签页密封类
 * 统一管理不同类型的内容展示
 */
sealed class ContentTab {
    abstract val id: String
    abstract val file: File
    abstract val displayName: String

    /**
     * 代码编辑器标签页
     */
    data class Editor(
        override val id: String,
        override val file: File
    ) : ContentTab() {
        override val displayName: String get() = file.name
    }

    /**
     * 图片预览标签页
     */
    data class ImagePreview(
        override val id: String,
        override val file: File
    ) : ContentTab() {
        override val displayName: String get() = file.name
    }

    /**
     * 十六进制查看器标签页
     */
    data class HexViewer(
        override val id: String,
        override val file: File
    ) : ContentTab() {
        override val displayName: String get() = "${file.name} [Hex]"
    }

    /**
     * JSON 树形查看器标签页
     */
    data class JsonViewer(
        override val id: String,
        override val file: File
    ) : ContentTab() {
        override val displayName: String get() = file.name
    }
}
