package com.wuxianggujun.tinaide.ui.compose.components.markdown

/**
 * 移除 Markdown 格式标记，返回纯文本。
 *
 * 适用于消息列表预览、通知摘要等场景。
 */
fun String.stripMarkdown(): String = this
    // 移除代码块 (```...``` 和 `...`)
    .replace(Regex("```[\\s\\S]*?```|`[^`]*?`"), "")
    // 移除图片和链接，保留文本内容
    .replace(Regex("!?\\[([^\\]]+)]\\([^)]*\\)"), "$1")
    // 移除粗体
    .replace(Regex("\\*\\*([^*]+?)\\*\\*"), "$1")
    // 移除斜体
    .replace(Regex("\\*([^*]+?)\\*"), "$1")
    // 移除下划线粗体/斜体
    .replace(Regex("__([^_]+?)__"), "$1")
    .replace(Regex("_([^_]+?)_"), "$1")
    // 移除删除线
    .replace(Regex("~~([^~]+?)~~"), "$1")
    // 移除标题标记
    .replace(Regex("(?m)^#+\\s*"), "")
    // 移除列表标记
    .replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
    .replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")
    // 移除引用标记
    .replace(Regex("(?m)^>\\s*"), "")
    // 移除水平分割线
    .replace(Regex("(?m)^(\\s*[-*_]){3,}\\s*$"), "")
    // 压缩多余换行
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

/**
 * 从思考内容中提取"标题"——最后一个 **bold** 独占行。
 *
 * 用于在思考过程折叠时展示简短的思考摘要。
 */
fun String.extractThinkingTitle(): String? {
    val lines = this.lines()
    val boldPattern = Regex("^\\*\\*(.+?)\\*\\*$")
    for (i in lines.indices.reversed()) {
        val match = boldPattern.find(lines[i].trim())
        if (match != null) {
            return match.groupValues[1].trim().takeUnless { it.isBlank() }
        }
    }
    return null
}
