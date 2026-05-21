package com.wuxianggujun.tinaide.core.util

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * UTF-8 流式解码器
 *
 * 用于将字节流实时解码为字符串，处理不完整的 UTF-8 序列。
 * 适用于处理 PTY 输出、网络流等场景。
 *
 * **特点**：
 * - 支持增量解码，处理跨缓冲区的多字节字符
 * - 自动处理畸形输入（替换为 Unicode 替换字符）
 * - 动态扩展内部缓冲区
 * - 线程不安全，每个流应使用独立实例
 *
 * **使用示例**：
 * ```kotlin
 * val decoder = Utf8StreamDecoder()
 * while (true) {
 *     val n = inputStream.read(buffer)
 *     if (n < 0) break
 *     val text = decoder.append(buffer, n)
 *     if (text.isNotEmpty()) {
 *         outputConsumer(text)
 *     }
 * }
 * // 处理剩余数据
 * val remaining = decoder.flush()
 * if (remaining.isNotEmpty()) {
 *     outputConsumer(remaining)
 * }
 * ```
 *
 * @param initialCapacity 初始缓冲区容量，默认 64KB
 */
class Utf8StreamDecoder(initialCapacity: Int = 64 * 1024) {
    
    private val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    
    private var inBuf: ByteBuffer = ByteBuffer.allocate(initialCapacity)
    private val outBuf: CharBuffer = CharBuffer.allocate(initialCapacity)

    /**
     * 追加字节数据并解码
     *
     * @param bytes 字节数组
     * @param length 有效数据长度
     * @return 解码后的字符串（可能为空，如果数据不完整）
     */
    fun append(bytes: ByteArray, length: Int): String {
        ensureCapacity(length)
        inBuf.put(bytes, 0, length)
        return decode(endOfInput = false)
    }

    /**
     * 刷新解码器，处理剩余数据
     *
     * 在输入流结束时调用此方法，确保所有数据都被解码。
     *
     * @return 剩余的解码字符串
     */
    fun flush(): String = decode(endOfInput = true)

    /**
     * 重置解码器状态
     *
     * 可用于复用解码器实例处理新的流。
     */
    fun reset() {
        decoder.reset()
        inBuf.clear()
        outBuf.clear()
    }

    /**
     * 确保输入缓冲区有足够容量
     */
    private fun ensureCapacity(incoming: Int) {
        if (inBuf.remaining() >= incoming) return

        inBuf.flip()
        val needed = inBuf.remaining() + incoming
        var newCap = inBuf.capacity()
        while (newCap < needed) newCap *= 2

        val newBuf = ByteBuffer.allocate(newCap)
        newBuf.put(inBuf)
        inBuf = newBuf
    }

    /**
     * 执行解码
     */
    private fun decode(endOfInput: Boolean): String {
        inBuf.flip()
        val sb = StringBuilder()

        while (true) {
            outBuf.clear()
            val result = decoder.decode(inBuf, outBuf, endOfInput)
            outBuf.flip()
            if (outBuf.hasRemaining()) sb.append(outBuf)

            if (result.isOverflow) continue
            if (result.isUnderflow) break
            result.throwException()
        }

        inBuf.compact()

        if (endOfInput) {
            outBuf.clear()
            val flushResult = decoder.flush(outBuf)
            outBuf.flip()
            if (outBuf.hasRemaining()) sb.append(outBuf)
            if (!flushResult.isUnderflow) {
                // 忽略 flush 错误
            }
        }

        return sb.toString()
    }
}