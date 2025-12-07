package com.wuxianggujun.tinaide.treesitter

data class TSInputEdit(
    val startByte: Int, val oldEndByte: Int, val newEndByte: Int,
    val startPoint: TSPoint, val oldEndPoint: TSPoint, val newEndPoint: TSPoint
) {
    companion object {
        @JvmStatic
        fun create(startByte: Int, oldEndByte: Int, newEndByte: Int,
                   startPoint: TSPoint, oldEndPoint: TSPoint, newEndPoint: TSPoint) =
            TSInputEdit(startByte, oldEndByte, newEndByte, startPoint, oldEndPoint, newEndPoint)
    }
}
