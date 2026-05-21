package com.wuxianggujun.tinaide.core.debug

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BreakpointStoreTest {

    @Test
    fun addToggleAndRemove_shouldMaintainBreakpointState() {
        val store = BreakpointStore()

        val first = store.add(file = "main.cpp", line = 12, condition = "x > 0")
        val second = store.toggle(file = "main.cpp", line = 20)

        assertThat(first.id).isEqualTo(1)
        assertThat(second?.id).isEqualTo(2)
        assertThat(store.getForFile("main.cpp")).hasSize(2)
        assertThat(store.hasBreakpoint("main.cpp", 12)).isTrue()

        assertThat(store.toggle("main.cpp", 20)).isNull()
        assertThat(store.hasBreakpoint("main.cpp", 20)).isFalse()
        assertThat(store.removeByLocation("main.cpp", 12)).isTrue()
        assertThat(store.breakpoints.value).isEmpty()
    }

    @Test
    fun clear_shouldResetIdsAndVerificationState() {
        val store = BreakpointStore()
        val breakpoint = store.add(file = "main.cpp", line = 1)
        store.updateVerified(breakpoint.id, address = 0x1000, verified = true)
        store.incrementHitCount(breakpoint.id)

        val verified = store.getAt("main.cpp", 1)
        assertThat(verified?.verified).isTrue()
        assertThat(verified?.hitCount).isEqualTo(1)

        store.clearVerification()
        assertThat(store.getAt("main.cpp", 1)?.verified).isFalse()
        assertThat(store.getAt("main.cpp", 1)?.address).isEqualTo(0L)

        store.clear()
        assertThat(store.add(file = "next.cpp", line = 2).id).isEqualTo(1)
    }
}
