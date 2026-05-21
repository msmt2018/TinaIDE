package com.wuxianggujun.tinaide.output

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OutputAccumulationTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun appendOutput_shouldAccumulateTextInArrivalOrderForSameChannel() {
        val manager = OutputManager(context)

        manager.appendOutput("compile ", IOutputManager.OutputChannel.BUILD)
        manager.appendOutput("finished", IOutputManager.OutputChannel.BUILD)

        assertThat(manager.getOutput(IOutputManager.OutputChannel.BUILD))
            .isEqualTo("compile finished")
    }

    @Test
    fun clearingEmptyChannel_shouldStillNotifyListeners() {
        val manager = OutputManager(context)
        val clearedChannels = mutableListOf<IOutputManager.OutputChannel>()
        val listener = object : IOutputManager.OutputListener {
            override fun onOutputAppended(text: String, channel: IOutputManager.OutputChannel) = Unit

            override fun onOutputCleared(channel: IOutputManager.OutputChannel) {
                clearedChannels += channel
            }
        }

        manager.addOutputListener(listener)
        manager.clearOutput(IOutputManager.OutputChannel.BUILD)

        assertThat(clearedChannels).containsExactly(IOutputManager.OutputChannel.BUILD)
        assertThat(manager.getOutput(IOutputManager.OutputChannel.BUILD)).isEmpty()
    }
}
