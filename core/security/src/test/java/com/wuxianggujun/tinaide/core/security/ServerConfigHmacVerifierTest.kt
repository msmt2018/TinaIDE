package com.wuxianggujun.tinaide.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * ServerConfigHmacVerifier 纯 JVM 单元测试
 *
 * 验证 HMAC-SHA256 签名/验证逻辑的正确性
 */
class ServerConfigHmacVerifierTest {

    private val secret = "test-secret-key".toByteArray()
    private val protocolVersion = 1
    private val timestamp = 1700000000L
    private val data = """{"key":"value"}""".toByteArray()

    @Test
    fun `sign produces non-empty signature`() {
        val signature = ServerConfigHmacVerifier.sign(secret, protocolVersion, timestamp, data)
        assertThat(signature).isNotEmpty()
    }

    @Test
    fun `sign produces 32-byte HMAC-SHA256 output`() {
        val signature = ServerConfigHmacVerifier.sign(secret, protocolVersion, timestamp, data)
        assertThat(signature).hasLength(32) // SHA-256 = 256 bits = 32 bytes
    }

    @Test
    fun `verify returns true for matching signature`() {
        val signature = ServerConfigHmacVerifier.sign(secret, protocolVersion, timestamp, data)
        val result = ServerConfigHmacVerifier.verify(secret, protocolVersion, timestamp, data, signature)
        assertThat(result).isTrue()
    }

    @Test
    fun `verify returns false for tampered data`() {
        val signature = ServerConfigHmacVerifier.sign(secret, protocolVersion, timestamp, data)
        val tamperedData = """{"key":"tampered"}""".toByteArray()
        val result = ServerConfigHmacVerifier.verify(secret, protocolVersion, timestamp, tamperedData, signature)
        assertThat(result).isFalse()
    }

    @Test
    fun `verify returns false for wrong secret`() {
        val signature = ServerConfigHmacVerifier.sign(secret, protocolVersion, timestamp, data)
        val wrongSecret = "wrong-secret".toByteArray()
        val result = ServerConfigHmacVerifier.verify(wrongSecret, protocolVersion, timestamp, data, signature)
        assertThat(result).isFalse()
    }

    @Test
    fun `verify returns false for wrong protocol version`() {
        val signature = ServerConfigHmacVerifier.sign(secret, protocolVersion, timestamp, data)
        val result = ServerConfigHmacVerifier.verify(secret, protocolVersion + 1, timestamp, data, signature)
        assertThat(result).isFalse()
    }

    @Test
    fun `verify returns false for wrong timestamp`() {
        val signature = ServerConfigHmacVerifier.sign(secret, protocolVersion, timestamp, data)
        val result = ServerConfigHmacVerifier.verify(secret, protocolVersion, timestamp + 1, data, signature)
        assertThat(result).isFalse()
    }

    @Test
    fun `verify returns false for corrupted signature`() {
        val signature = ServerConfigHmacVerifier.sign(secret, protocolVersion, timestamp, data)
        val corrupted = signature.copyOf()
        corrupted[0] = (corrupted[0].toInt() xor 0xFF).toByte()
        val result = ServerConfigHmacVerifier.verify(secret, protocolVersion, timestamp, data, corrupted)
        assertThat(result).isFalse()
    }

    @Test
    fun `sign is deterministic for same inputs`() {
        val sig1 = ServerConfigHmacVerifier.sign(secret, protocolVersion, timestamp, data)
        val sig2 = ServerConfigHmacVerifier.sign(secret, protocolVersion, timestamp, data)
        assertThat(sig1).isEqualTo(sig2)
    }

    @Test
    fun `sign produces different output for different data`() {
        val sig1 = ServerConfigHmacVerifier.sign(secret, protocolVersion, timestamp, "data1".toByteArray())
        val sig2 = ServerConfigHmacVerifier.sign(secret, protocolVersion, timestamp, "data2".toByteArray())
        assertThat(sig1).isNotEqualTo(sig2)
    }

    @Test
    fun `sign handles empty data`() {
        val signature = ServerConfigHmacVerifier.sign(secret, protocolVersion, timestamp, ByteArray(0))
        assertThat(signature).hasLength(32)
        val result = ServerConfigHmacVerifier.verify(secret, protocolVersion, timestamp, ByteArray(0), signature)
        assertThat(result).isTrue()
    }
}
