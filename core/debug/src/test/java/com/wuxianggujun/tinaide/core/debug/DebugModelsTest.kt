package com.wuxianggujun.tinaide.core.debug

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DebugModelsTest {

    @Test
    fun memoryData_shouldCompareByContent() {
        val left = DebugResponse.MemoryData(byteArrayOf(0x01, 0x02))
        val right = DebugResponse.MemoryData(byteArrayOf(0x01, 0x02))
        val different = DebugResponse.MemoryData(byteArrayOf(0x01, 0x03))

        assertThat(left).isEqualTo(right)
        assertThat(left.hashCode()).isEqualTo(right.hashCode())
        assertThat(left).isNotEqualTo(different)
    }

    @Test
    fun debugSessionStore_shouldPublishAndClearDescriptor() {
        val store = DebugSessionStore()
        val descriptor = DebugSessionScaffold.Descriptor(
            sessionId = "dbg-1",
            descriptorPath = "/tmp/dbg-1.txt",
            instructions = listOf("created")
        )

        store.update(descriptor)
        assertThat(store.descriptor.value).isEqualTo(descriptor)

        store.clear()
        assertThat(store.descriptor.value).isNull()
    }
}
