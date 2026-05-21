package com.wuxianggujun.tinaide.ui

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.compile.CompileProjectUseCase
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    manifest = Config.NONE,
    application = Application::class
)
class CompileActionUiTextTest {

    @Test
    fun `resolveUiText returns combined action messages`() {
        val context = RuntimeEnvironment.getApplication()

        val text = CompileProjectUseCase.Action.CMAKE_CLEAR_AND_RECONFIGURE
            .resolveUiText(context)

        assertThat(text.menuLabel)
            .isEqualTo(Strings.menu_cmake_clean_and_reconfigure.strOr(context))
        assertThat(text.progressMessage)
            .isEqualTo(Strings.toast_cmake_clean_and_reconfiguring.strOr(context))
        assertThat(text.successMessage)
            .isEqualTo(Strings.compile_cmake_clean_and_reconfigure_finished.strOr(context))
        assertThat(text.failureMessage)
            .isEqualTo(Strings.compile_cmake_clean_and_reconfigure_failed.strOr(context))
    }

    @Test
    fun `resolveUiText keeps every maintenance action fully populated`() {
        val context = RuntimeEnvironment.getApplication()

        CompileProjectUseCase.Action.entries
            .filter { it.isCMakeMaintenance() }
            .forEach { action ->
                val text = action.resolveUiText(context)

                assertThat(text.menuLabel).isNotEmpty()
                assertThat(text.progressMessage).isNotEmpty()
                assertThat(text.successMessage).isNotEmpty()
                assertThat(text.failureMessage).isNotEmpty()
            }
    }
}
