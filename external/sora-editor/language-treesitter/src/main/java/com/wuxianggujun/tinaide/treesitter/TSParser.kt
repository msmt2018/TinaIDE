package com.wuxianggujun.tinaide.treesitter

class TSParser private constructor(private var pointer: Long) : TSNativeObject() {
    companion object {
        @JvmStatic fun create(): TSParser {
            val ptr = nativeCreate()
            require(ptr != 0L) { "Failed to create parser" }
            return TSParser(ptr)
        }
        @JvmStatic private external fun nativeCreate(): Long
    }

    var language: TSLanguage? = null
        set(value) { field = value; value?.let { nativeSetLanguage(pointer, it.getPointer()) } }

    fun parseString(source: String) = parseString(null, source)
    fun parseString(oldTree: TSTree?, source: String): TSTree? {
        val ptr = nativeParseString(pointer, source, oldTree?.getPointer() ?: 0)
        return if (ptr != 0L) TSTree(ptr) else null
    }
    fun parseString(source: UTF16String) = parseString(null, source)
    fun parseString(oldTree: TSTree?, source: UTF16String): TSTree? {
        val bytes = source.bytes
        val ptr = nativeParseBytes(pointer, bytes, bytes.size, oldTree?.getPointer() ?: 0)
        return if (ptr != 0L) TSTree(ptr) else null
    }
    fun reset() = nativeReset(pointer)
    override fun getPointer() = pointer
    override fun close() { if (pointer != 0L) { nativeDelete(pointer); pointer = 0 } }

    private external fun nativeDelete(pointer: Long)
    private external fun nativeSetLanguage(parser: Long, language: Long): Boolean
    private external fun nativeParseString(parser: Long, source: String, oldTree: Long): Long
    private external fun nativeParseBytes(parser: Long, source: ByteArray, length: Int, oldTree: Long): Long
    private external fun nativeReset(pointer: Long)
}
