package com.wuxianggujun.tinaide.treesitter

import java.nio.ByteBuffer
import java.nio.ByteOrder

class UTF16String : AutoCloseable, CharSequence {
    private var buffer: ByteBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    private var _length: Int = 0

    constructor()
    constructor(initialCapacity: Int) { buffer = ByteBuffer.allocate(initialCapacity * 2).order(ByteOrder.LITTLE_ENDIAN) }
    constructor(text: String) { append(text) }

    override val length: Int get() = _length
    val bytes: ByteArray get() { val r = ByteArray(_length * 2); buffer.position(0); buffer.get(r, 0, r.size); return r }

    fun append(text: String): UTF16String {
        val chars = text.toCharArray()
        ensureCapacity(_length + chars.size)
        buffer.position(_length * 2)
        chars.forEach { buffer.putChar(it) }
        _length += chars.size
        return this
    }

    fun insert(index: Int, text: String): UTF16String {
        require(index in 0.._length)
        val chars = text.toCharArray()
        ensureCapacity(_length + chars.size)
        val insertPos = index * 2
        val shiftSize = (_length - index) * 2
        if (shiftSize > 0) { val t = ByteArray(shiftSize); buffer.position(insertPos); buffer.get(t); buffer.position(insertPos + chars.size * 2); buffer.put(t) }
        buffer.position(insertPos)
        chars.forEach { buffer.putChar(it) }
        _length += chars.size
        return this
    }

    fun delete(start: Int, end: Int): UTF16String {
        require(start in 0.._length && end in start.._length)
        val deleteCount = end - start
        if (deleteCount == 0) return this
        val shiftSize = (_length - end) * 2
        if (shiftSize > 0) { val t = ByteArray(shiftSize); buffer.position(end * 2); buffer.get(t); buffer.position(start * 2); buffer.put(t) }
        _length -= deleteCount
        return this
    }

    fun subseqChars(start: Int, end: Int): UTF16String {
        require(start in 0.._length && end in start.._length)
        val r = UTF16String(end - start)
        buffer.position(start * 2)
        val t = ByteArray((end - start) * 2)
        buffer.get(t)
        r.buffer.position(0)
        r.buffer.put(t)
        r._length = end - start
        return r
    }

    override fun get(index: Int): Char { require(index in 0 until _length); return buffer.getChar(index * 2) }
    override fun subSequence(startIndex: Int, endIndex: Int) = subseqChars(startIndex, endIndex)
    override fun toString(): String { if (_length == 0) return ""; val c = CharArray(_length); buffer.position(0); repeat(_length) { c[it] = buffer.getChar() }; return String(c) }
    fun clear(): UTF16String { _length = 0; return this }
    private fun ensureCapacity(minCapacity: Int) { val minBytes = minCapacity * 2; if (buffer.capacity() < minBytes) { val newBuf = ByteBuffer.allocate(maxOf(buffer.capacity() * 2, minBytes)).order(ByteOrder.LITTLE_ENDIAN); buffer.position(0); buffer.limit(_length * 2); newBuf.put(buffer); buffer = newBuf } }
    override fun close() { _length = 0 }
}

object UTF16StringFactory {
    @JvmStatic fun newString() = UTF16String()
    @JvmStatic fun newString(initialCapacity: Int) = UTF16String(initialCapacity)
    @JvmStatic fun newString(text: String) = UTF16String(text)
}
