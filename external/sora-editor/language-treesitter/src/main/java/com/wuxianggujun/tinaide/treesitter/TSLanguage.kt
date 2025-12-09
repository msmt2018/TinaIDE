package com.wuxianggujun.tinaide.treesitter

class TSLanguage private constructor(private val name: String, private var pointer: Long) : TSNativeObject() {
    companion object {
        @JvmStatic fun create(name: String, pointer: Long): TSLanguage {
            require(pointer != 0L) { "Invalid language pointer" }
            return TSLanguage(name, pointer)
        }
    }
    fun getName() = name
    override fun getPointer() = pointer
    val version: Int get() = if (pointer != 0L) nativeVersion(pointer) else 0
    val fieldCount: Int get() = if (pointer != 0L) nativeFieldCount(pointer) else 0
    val symbolCount: Int get() = if (pointer != 0L) nativeSymbolCount(pointer) else 0
    override fun close() { pointer = 0 }

    private external fun nativeVersion(pointer: Long): Int
    private external fun nativeFieldCount(pointer: Long): Int
    private external fun nativeSymbolCount(pointer: Long): Int
}
