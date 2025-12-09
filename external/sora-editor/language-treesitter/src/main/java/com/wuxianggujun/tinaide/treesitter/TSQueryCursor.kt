package com.wuxianggujun.tinaide.treesitter

class TSQueryCursor private constructor(private var pointer: Long) : TSNativeObject() {
    companion object {
        @JvmStatic fun create(): TSQueryCursor {
            val ptr = nativeCreate()
            require(ptr != 0L) { "Failed to create query cursor" }
            return TSQueryCursor(ptr)
        }
        @JvmStatic private external fun nativeCreate(): Long
    }

    fun exec(query: TSQuery, node: TSNode) = nativeExec(pointer, query.getPointer(), node)
    fun setByteRange(startByte: Int, endByte: Int) = nativeSetByteRange(pointer, startByte, endByte)
    fun setPointRange(startPoint: TSPoint, endPoint: TSPoint) = nativeSetPointRange(pointer, startPoint.row, startPoint.column, endPoint.row, endPoint.column)
    fun setAllowChangedNodes(allow: Boolean) { /* no-op */ }
    fun nextMatch(): TSQueryMatch? {
        val data = nativeNextMatch(pointer) ?: return null
        val captures = Array(data[2].toInt()) { i ->
            val off = 3 + i * 7
            TSQueryCapture(TSNode.fromNativeData(longArrayOf(data[off], data[off+1], data[off+2], data[off+3], data[off+4], data[off+5]), data[off+5]), data[off+6].toInt())
        }
        return TSQueryMatch(data[0].toInt(), data[1].toInt(), captures)
    }
    fun removeMatch(matchId: Int) = nativeRemoveMatch(pointer, matchId)
    fun setMatchLimit(limit: Int) = nativeSetMatchLimit(pointer, limit)
    fun getMatchLimit() = nativeGetMatchLimit(pointer)
    fun didExceedMatchLimit() = nativeDidExceedMatchLimit(pointer)
    override fun getPointer() = pointer
    override fun close() { if (pointer != 0L) { nativeDelete(pointer); pointer = 0 } }

    private external fun nativeDelete(pointer: Long)
    private external fun nativeExec(cursor: Long, query: Long, node: TSNode)
    private external fun nativeSetByteRange(pointer: Long, startByte: Int, endByte: Int)
    private external fun nativeSetPointRange(pointer: Long, startRow: Int, startCol: Int, endRow: Int, endCol: Int)
    private external fun nativeNextMatch(pointer: Long): LongArray?
    private external fun nativeRemoveMatch(pointer: Long, matchId: Int)
    private external fun nativeSetMatchLimit(pointer: Long, limit: Int)
    private external fun nativeGetMatchLimit(pointer: Long): Int
    private external fun nativeDidExceedMatchLimit(pointer: Long): Boolean
}
