package com.wuxianggujun.tinaide.core.commands

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.i18n.Strings
import org.junit.Test

class HostCommandsTest {

    @Test
    fun `isSupported should trim command id`() {
        assertThat(HostCommands.isSupported(" ${HostCommands.EDITOR_SAVE} ")).isTrue()
        assertThat(HostCommands.isSupported(" editor.notFound ")).isFalse()
    }

    @Test
    fun `titleResOrNull should trim command id`() {
        assertThat(HostCommands.titleResOrNull(" ${HostCommands.EDITOR_SAVE} "))
            .isEqualTo(Strings.cmd_editor_save)
    }
}
