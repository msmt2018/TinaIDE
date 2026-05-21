package com.wuxianggujun.tinaide.ai.api.stream

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.ai.api.ToolCallDelta
import com.wuxianggujun.tinaide.ai.api.ToolFunctionDelta
import org.junit.Test

class ChatStreamParserTest {

    @Test
    fun `tool call accumulator merges indexed and id-only deltas`() {
        val accumulator = ToolCallAccumulator()

        accumulator.applyDelta(
            ToolCallDelta(
                index = 0,
                id = "call-1",
                type = "function",
                function = ToolFunctionDelta(name = "read_file", arguments = "{\"path\":")
            )
        )
        accumulator.applyDelta(
            ToolCallDelta(
                index = 0,
                function = ToolFunctionDelta(arguments = "\"main.kt\"}")
            )
        )
        accumulator.applyDelta(
            ToolCallDelta(
                id = "call-1",
                function = ToolFunctionDelta(arguments = " ")
            )
        )

        val calls = accumulator.toToolCalls()

        assertThat(accumulator.hasCalls()).isTrue()
        assertThat(calls).hasSize(1)
        assertThat(calls.single().id).isEqualTo("call-1")
        assertThat(calls.single().type).isEqualTo("function")
        assertThat(calls.single().function?.name).isEqualTo("read_file")
        assertThat(calls.single().function?.arguments).isEqualTo("{\"path\":\"main.kt\"}")
    }

    @Test
    fun `tool call accumulator ignores deltas without identity`() {
        val accumulator = ToolCallAccumulator()

        accumulator.applyDelta(
            ToolCallDelta(function = ToolFunctionDelta(name = "ignored", arguments = "{}"))
        )

        assertThat(accumulator.hasCalls()).isFalse()
        assertThat(accumulator.toToolCalls()).isEmpty()
    }

    @Test
    fun `tool call accumulator supplies stable fallback values`() {
        val accumulator = ToolCallAccumulator()

        accumulator.applyDelta(ToolCallDelta(index = 0))

        val call = accumulator.toToolCalls().single()
        assertThat(call.id).isEqualTo("call_1")
        assertThat(call.type).isEqualTo("function")
        assertThat(call.function?.name).isEqualTo("unknown")
        assertThat(call.function?.arguments).isNull()
    }

    @Test
    fun `tool call accumulator supports id only deltas`() {
        val accumulator = ToolCallAccumulator()

        accumulator.applyDelta(
            ToolCallDelta(
                id = "call-id",
                function = ToolFunctionDelta(name = "search", arguments = "{}")
            )
        )

        val call = accumulator.toToolCalls().single()
        assertThat(call.id).isEqualTo("call-id")
        assertThat(call.type).isEqualTo("function")
        assertThat(call.function?.name).isEqualTo("search")
        assertThat(call.function?.arguments).isEqualTo("{}")
    }
}
