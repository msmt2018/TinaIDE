package com.wuxianggujun.tinaide.treesitter

abstract class TSNativeObject : AutoCloseable {
    abstract fun getPointer(): Long
    open fun canAccess(): Boolean = getPointer() != 0L
    abstract override fun close()
}
