package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.terminal.BackendMode
import com.wuxianggujun.tinaide.core.terminal.ITerminalPreferences
import com.wuxianggujun.tinaide.core.terminal.ShellType
import com.wuxianggujun.tinaide.core.terminal.TerminalLocale
import org.junit.Test

class TerminalSettingsHelperTest {

    @Test
    fun shellTypeConstantsAndLookup_shouldStayConsistent() {
        assertThat(TerminalSettingsHelper.SHELL_TYPE_AUTO).isEqualTo(ShellType.AUTO.value)
        assertThat(TerminalSettingsHelper.SHELL_TYPE_SH).isEqualTo(ShellType.SH.value)
        assertThat(TerminalSettingsHelper.SHELL_TYPE_BASH).isEqualTo(ShellType.BASH.value)
        assertThat(TerminalSettingsHelper.SHELL_TYPE_ZSH).isEqualTo(ShellType.ZSH.value)

        assertThat(
            TerminalSettingsHelper.getShellTypeFromValue(TerminalSettingsHelper.SHELL_TYPE_BASH)
        ).isEqualTo(ShellType.BASH)
        assertThat(
            TerminalSettingsHelper.getShellTypeFromValue("unknown")
        ).isEqualTo(ShellType.AUTO)
        assertThat(
            TerminalSettingsHelper.getAllShellTypes()
        ).containsExactlyElementsIn(ShellType.entries).inOrder()
    }

    @Test
    fun backendModeConstantsAndLookup_shouldStayConsistent() {
        assertThat(TerminalSettingsHelper.BACKEND_AUTO).isEqualTo(BackendMode.AUTO.value)
        assertThat(TerminalSettingsHelper.BACKEND_PROOT).isEqualTo(BackendMode.PROOT.value)
        assertThat(TerminalSettingsHelper.BACKEND_HOST).isEqualTo(BackendMode.HOST.value)

        assertThat(
            TerminalSettingsHelper.getBackendModeFromValue(TerminalSettingsHelper.BACKEND_PROOT)
        ).isEqualTo(BackendMode.PROOT)
        assertThat(
            TerminalSettingsHelper.getBackendModeFromValue("unknown")
        ).isEqualTo(BackendMode.AUTO)
        assertThat(
            TerminalSettingsHelper.getAllBackendModes()
        ).containsExactlyElementsIn(BackendMode.entries).inOrder()
    }

    @Test
    fun terminalLocaleLookup_shouldFallbackToDefaultAndExposeEntries() {
        assertThat(
            TerminalSettingsHelper.getTerminalLocaleFromValue(TerminalLocale.ZH_CN.value)
        ).isEqualTo(TerminalLocale.ZH_CN)
        assertThat(
            TerminalSettingsHelper.getTerminalLocaleFromValue("unknown")
        ).isEqualTo(TerminalLocale.C_UTF8)
        assertThat(
            TerminalSettingsHelper.getAllTerminalLocales()
        ).containsExactlyElementsIn(TerminalLocale.entries).inOrder()
    }

    @Test
    fun rangeConstants_shouldMirrorTerminalPreferencesContract() {
        assertThat(TerminalSettingsHelper.MIN_FONT_SIZE).isEqualTo(ITerminalPreferences.MIN_FONT_SIZE)
        assertThat(TerminalSettingsHelper.MAX_FONT_SIZE).isEqualTo(ITerminalPreferences.MAX_FONT_SIZE)
        assertThat(TerminalSettingsHelper.CURSOR_BLINK_RATE_MIN)
            .isEqualTo(ITerminalPreferences.CURSOR_BLINK_RATE_MIN)
        assertThat(TerminalSettingsHelper.CURSOR_BLINK_RATE_MAX)
            .isEqualTo(ITerminalPreferences.CURSOR_BLINK_RATE_MAX)
    }
}
