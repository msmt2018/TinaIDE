package com.wuxianggujun.tinaide.output

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OutputChannelDefaultsTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun interfaceDefaults_shouldUseRunChannel() {
        val manager: IOutputManager = OutputManager(context)

        manager.appendOutput("stdout")

        assertThat(manager.getOutput()).isEqualTo("stdout")
        assertThat(manager.getOutput(IOutputManager.OutputChannel.BUILD)).isEmpty()
    }

    @Test
    fun clearingDefaultChannel_shouldNotClearBuildOutput() {
        val manager: IOutputManager = OutputManager(context)

        manager.appendOutput("build", IOutputManager.OutputChannel.BUILD)
        manager.appendOutput("run")
        manager.clearOutput()

        assertThat(manager.getOutput()).isEmpty()
        assertThat(manager.getOutput(IOutputManager.OutputChannel.BUILD)).isEqualTo("build")
    }
}
