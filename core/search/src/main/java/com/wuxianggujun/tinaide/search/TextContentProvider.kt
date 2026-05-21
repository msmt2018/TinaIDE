package com.wuxianggujun.tinaide.search

/**
 * 文本内容提供者接口
 *
 * 抽象文本访问，避免 core:search 直接依赖具体的编辑器 UI 组件。
 */
interface TextContentProvider {
    /**
     * 获取文本内容
     */
    fun getText(): String

    /**
     * 根据字符索引获取行列位置
     */
    fun getPosition(charIndex: Int): Position

    /**
     * 位置信息
     */
    data class Position(
        val line: Int,
        val column: Int
    )
}
