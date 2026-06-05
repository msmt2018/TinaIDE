package com.wuxianggujun.tinaide.ui.commands

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.commands.HostCommands
import org.junit.Test

class MainActivityHostCommandExecutorTest {

    @Test
    fun `normalizeHostCommandId should trim command id`() {
        assertThat(normalizeHostCommandId(" ${HostCommands.VIEW_SETTINGS} "))
            .isEqualTo(HostCommands.VIEW_SETTINGS)
    }
}
