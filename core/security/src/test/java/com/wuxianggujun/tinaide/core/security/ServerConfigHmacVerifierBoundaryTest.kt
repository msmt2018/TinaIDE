package com.wuxianggujun.tinaide.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerConfigHmacVerifierBoundaryTest {

    @Test
    fun verify_shouldRejectSignatureCreatedWithDifferentProtocolVersion() {
        val secret = "secret".toByteArray()
        val data = "payload".toByteArray()
        val signature = ServerConfigHmacVerifier.sign(
            secret = secret,
            protocolVersion = 1,
            timestamp = 100L,
            data = data
        )

        assertThat(
            ServerConfigHmacVerifier.verify(
                secret = secret,
                protocolVersion = 2,
                timestamp = 100L,
                data = data,
                signature = signature
            )
        ).isFalse()
    }

    @Test
    fun verify_shouldRejectSignatureCreatedWithDifferentTimestampOrData() {
        val secret = "secret".toByteArray()
        val data = "payload".toByteArray()
        val signature = ServerConfigHmacVerifier.sign(
            secret = secret,
            protocolVersion = 1,
            timestamp = 100L,
            data = data
        )

        assertThat(
            ServerConfigHmacVerifier.verify(
                secret = secret,
                protocolVersion = 1,
                timestamp = 101L,
                data = data,
                signature = signature
            )
        ).isFalse()
        assertThat(
            ServerConfigHmacVerifier.verify(
                secret = secret,
                protocolVersion = 1,
                timestamp = 100L,
                data = "other".toByteArray(),
                signature = signature
            )
        ).isFalse()
    }

    @Test
    fun sign_shouldBeDeterministicForSameInputs() {
        val secret = "secret".toByteArray()
        val data = byteArrayOf(1, 2, 3)

        val first = ServerConfigHmacVerifier.sign(secret, protocolVersion = 1, timestamp = 100L, data = data)
        val second = ServerConfigHmacVerifier.sign(secret, protocolVersion = 1, timestamp = 100L, data = data)

        assertThat(first.asList()).containsExactlyElementsIn(second.asList()).inOrder()
        assertThat(ServerConfigHmacVerifier.verify(secret, 1, 100L, data, first)).isTrue()
    }
}
