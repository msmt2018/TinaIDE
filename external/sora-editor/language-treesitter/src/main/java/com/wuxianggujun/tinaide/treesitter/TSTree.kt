package com.wuxianggujun.tinaide.treesitter

class TSTree internal constructor(private var pointer: Long) : TSNativeObject() {
    val rootNode: TSNode get() = TSNode.fromNativeData(nativeRootNode(pointer), pointer)
    val language: TSLanguage get() = TSLanguage.create("unknown", nativeLanguage(pointer))
    val closed: Boolean get() = pointer == 0L

    fun copy(): TSTree {
        val ptr = nativeCopy(pointer)
        require(ptr != 0L) { "Failed to copy tree" }
        return TSTree(ptr)
    }
    fun edit(edit: TSInputEdit) {
        nativeEdit(pointer, edit.startByte, edit.oldEndByte, edit.newEndByte,
            edit.startPoint.row, edit.startPoint.column,
            edit.oldEndPoint.row, edit.oldEndPoint.column,
            edit.newEndPoint.row, edit.newEndPoint.column)
    }
    override fun getPointer() = pointer
    override fun close() { if (pointer != 0L) { nativeDelete(pointer); pointer = 0 } }

    private external fun nativeDelete(pointer: Long)
    private external fun nativeCopy(pointer: Long): Long
    private external fun nativeRootNode(pointer: Long): LongArray
    private external fun nativeLanguage(pointer: Long): Long
    private external fun nativeEdit(pointer: Long, startByte: Int, oldEndByte: Int, newEndByte: Int,
        startRow: Int, startCol: Int, oldEndRow: Int, oldEndCol: Int, newEndRow: Int, newEndCol: Int)
}
