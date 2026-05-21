package com.wuxianggujun.tinaide.search

data class SearchPosition(
    val line: Int,
    val column: Int,
    val index: Int
)

data class SearchRange(
    val start: SearchPosition,
    val end: SearchPosition
) {
    val startIndex: Int get() = start.index
    val endIndex: Int get() = end.index
}
