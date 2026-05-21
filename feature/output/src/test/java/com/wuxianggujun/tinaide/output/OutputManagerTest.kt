package com.wuxianggujun.tinaide.output

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OutputManagerTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun appendAndClear_shouldKeepChannelsIndependent() {
        val manager = OutputManager(context)

        manager.appendOutput("build log", IOutputManager.OutputChannel.BUILD)
        manager.appendOutput("run log", IOutputManager.OutputChannel.RUN)

        assertThat(manager.getOutput(IOutputManager.OutputChannel.BUILD)).isEqualTo("build log")
        assertThat(manager.getOutput(IOutputManager.OutputChannel.RUN)).isEqualTo("run log")

        manager.clearOutput(IOutputManager.OutputChannel.BUILD)

        assertThat(manager.getOutput(IOutputManager.OutputChannel.BUILD)).isEmpty()
        assertThat(manager.getOutput(IOutputManager.OutputChannel.RUN)).isEqualTo("run log")
    }

    @Test
    fun listeners_shouldReceiveAppendAndClearEventsUntilRemoved() {
        val manager = OutputManager(context)
        val events = mutableListOf<String>()
        val listener = object : IOutputManager.OutputListener {
            override fun onOutputAppended(text: String, channel: IOutputManager.OutputChannel) {
                events += "append:$channel:$text"
            }

            override fun onOutputCleared(channel: IOutputManager.OutputChannel) {
                events += "clear:$channel"
            }
        }

        manager.addOutputListener(listener)
        manager.appendOutput("one", IOutputManager.OutputChannel.RUN)
        manager.clearOutput(IOutputManager.OutputChannel.RUN)
        manager.removeOutputListener(listener)
        manager.appendOutput("ignored", IOutputManager.OutputChannel.RUN)

        assertThat(events).containsExactly(
            "append:RUN:one",
            "clear:RUN"
        ).inOrder()
    }
}
