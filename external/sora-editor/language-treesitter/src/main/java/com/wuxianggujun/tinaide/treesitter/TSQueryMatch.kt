package com.wuxianggujun.tinaide.treesitter

data class TSQueryMatch(val id: Int, val patternIndex: Int, val captures: Array<TSQueryCapture>) {
    override fun equals(other: Any?) = other is TSQueryMatch && id == other.id && patternIndex == other.patternIndex && captures.contentEquals(other.captures)
    override fun hashCode() = 31 * (31 * id + patternIndex) + captures.contentHashCode()
}
