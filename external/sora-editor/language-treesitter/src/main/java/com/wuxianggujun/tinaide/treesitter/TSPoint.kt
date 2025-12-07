package com.wuxianggujun.tinaide.treesitter

data class TSPoint(val row: Int, val column: Int) {
    companion object {
        @JvmStatic fun create(row: Int, column: Int) = TSPoint(row, column)
        @JvmStatic val ORIGIN = TSPoint(0, 0)
    }
}
