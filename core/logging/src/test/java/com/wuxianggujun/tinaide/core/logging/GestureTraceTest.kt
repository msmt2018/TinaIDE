package com.wuxianggujun.tinaide.core.logging

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class GestureTraceTest {

    @After
    fun tearDown() {
        GestureTrace.enabledProvider = { false }
    }

    @Test
    fun isEnabled_shouldReadInjectedProvider() {
        GestureTrace.enabledProvider = { true }
        assertThat(GestureTrace.isEnabled()).isTrue()

        GestureTrace.enabledProvider = { false }
        assertThat(GestureTrace.isEnabled()).isFalse()
    }

    @Test
    fun loggingCalls_shouldReturnImmediatelyWhenDisabled() {
        GestureTrace.enabledProvider = { false }

        GestureTrace.d("Editor", "down")
        GestureTrace.w("Editor", "cancel")

        assertThat(GestureTrace.isEnabled()).isFalse()
    }
}
