package com.wuxianggujun.tinaide.treesitter

class TSNode private constructor(private val context: IntArray, private val id: Long, private val treePointer: Long) {
    companion object {
        internal fun fromNativeData(data: LongArray, treePointer: Long): TSNode {
            require(data.size >= 6)
            return TSNode(intArrayOf(data[0].toInt(), data[1].toInt(), data[2].toInt(), data[3].toInt()), data[4], data[5])
        }
        @JvmStatic val NULL = TSNode(intArrayOf(0,0,0,0), 0, 0)
        @JvmStatic private external fun nativeType(context: IntArray, id: Long, tree: Long): String?
        @JvmStatic private external fun nativeSymbol(context: IntArray, id: Long, tree: Long): Int
        @JvmStatic private external fun nativeStartByte(context: IntArray, id: Long, tree: Long): Int
        @JvmStatic private external fun nativeEndByte(context: IntArray, id: Long, tree: Long): Int
        @JvmStatic private external fun nativeStartRow(context: IntArray, id: Long, tree: Long): Int
        @JvmStatic private external fun nativeStartColumn(context: IntArray, id: Long, tree: Long): Int
        @JvmStatic private external fun nativeEndRow(context: IntArray, id: Long, tree: Long): Int
        @JvmStatic private external fun nativeEndColumn(context: IntArray, id: Long, tree: Long): Int
        @JvmStatic private external fun nativeIsNull(context: IntArray, id: Long, tree: Long): Boolean
        @JvmStatic private external fun nativeIsNamed(context: IntArray, id: Long, tree: Long): Boolean
        @JvmStatic private external fun nativeIsMissing(context: IntArray, id: Long, tree: Long): Boolean
        @JvmStatic private external fun nativeHasError(context: IntArray, id: Long, tree: Long): Boolean
        @JvmStatic private external fun nativeChildCount(context: IntArray, id: Long, tree: Long): Int
        @JvmStatic private external fun nativeNamedChildCount(context: IntArray, id: Long, tree: Long): Int
        @JvmStatic private external fun nativeChild(context: IntArray, id: Long, tree: Long, index: Int): LongArray?
        @JvmStatic private external fun nativeNamedChild(context: IntArray, id: Long, tree: Long, index: Int): LongArray?
        @JvmStatic private external fun nativeParent(context: IntArray, id: Long, tree: Long): LongArray?
        @JvmStatic private external fun nativeNextSibling(context: IntArray, id: Long, tree: Long): LongArray?
        @JvmStatic private external fun nativePrevSibling(context: IntArray, id: Long, tree: Long): LongArray?
        @JvmStatic private external fun nativeNextNamedSibling(context: IntArray, id: Long, tree: Long): LongArray?
        @JvmStatic private external fun nativePrevNamedSibling(context: IntArray, id: Long, tree: Long): LongArray?
        @JvmStatic private external fun nativeString(context: IntArray, id: Long, tree: Long): String?
    }

    val type: String? get() = nativeType(context, id, treePointer)
    val symbol: Int get() = nativeSymbol(context, id, treePointer)
    val startByte: Int get() = nativeStartByte(context, id, treePointer)
    val endByte: Int get() = nativeEndByte(context, id, treePointer)
    val startPoint: TSPoint get() = TSPoint(nativeStartRow(context, id, treePointer), nativeStartColumn(context, id, treePointer))
    val endPoint: TSPoint get() = TSPoint(nativeEndRow(context, id, treePointer), nativeEndColumn(context, id, treePointer))
    val isNull: Boolean get() = nativeIsNull(context, id, treePointer)
    val isNamed: Boolean get() = nativeIsNamed(context, id, treePointer)
    val isMissing: Boolean get() = nativeIsMissing(context, id, treePointer)
    val hasError: Boolean get() = nativeHasError(context, id, treePointer)
    val childCount: Int get() = nativeChildCount(context, id, treePointer)
    
    // Compatibility methods for sora-editor
    fun canAccess(): Boolean = !isNull && treePointer != 0L
    fun hasChanges(): Boolean = false // Tree nodes don't track changes directly
    val namedChildCount: Int get() = nativeNamedChildCount(context, id, treePointer)
    fun getChild(index: Int) = nativeChild(context, id, treePointer, index)?.let { fromNativeData(it, treePointer) } ?: NULL
    fun getNamedChild(index: Int) = nativeNamedChild(context, id, treePointer, index)?.let { fromNativeData(it, treePointer) } ?: NULL
    val parent: TSNode get() = nativeParent(context, id, treePointer)?.let { fromNativeData(it, treePointer) } ?: NULL
    val nextSibling: TSNode get() = nativeNextSibling(context, id, treePointer)?.let { fromNativeData(it, treePointer) } ?: NULL
    val prevSibling: TSNode get() = nativePrevSibling(context, id, treePointer)?.let { fromNativeData(it, treePointer) } ?: NULL
    val nextNamedSibling: TSNode get() = nativeNextNamedSibling(context, id, treePointer)?.let { fromNativeData(it, treePointer) } ?: NULL
    val prevNamedSibling: TSNode get() = nativePrevNamedSibling(context, id, treePointer)?.let { fromNativeData(it, treePointer) } ?: NULL
    override fun toString() = nativeString(context, id, treePointer) ?: "(null)"
    override fun equals(other: Any?) = other is TSNode && id == other.id && treePointer == other.treePointer
    override fun hashCode() = 31 * id.hashCode() + treePointer.hashCode()
}
