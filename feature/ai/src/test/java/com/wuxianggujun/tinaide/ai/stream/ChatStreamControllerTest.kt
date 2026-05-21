package com.wuxianggujun.tinaide.ai.stream

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.ai.api.AiApiClient
import com.wuxianggujun.tinaide.ai.api.ChatMessage
import com.wuxianggujun.tinaide.ai.api.ChatRole
import com.wuxianggujun.tinaide.ai.api.ChatUsage
import com.wuxianggujun.tinaide.ai.api.ToolCall
import com.wuxianggujun.tinaide.ai.api.ToolFunction
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatStreamControllerTest {

    @Test
    fun `stream accumulates events and returns completed assistant message`(): Unit = runTest {
        val client = mockk<AiApiClient>()
        val toolCall = ToolCall(
            id = "call-1",
            type = "function",
            function = ToolFunction(name = "read_file", arguments = "{}"),
        )
        val usage = ChatUsage(promptTokens = 1, completionTokens = 2, totalTokens = 3)
        coEvery { client.chatStream(any(), any(), any(), any()) } coAnswers {
            val onEvent = secondArg<(AiApiClient.ChatStreamEvent) -> Unit>()
            onEvent(AiApiClient.ChatStreamEvent.TextDelta("hel"))
            onEvent(AiApiClient.ChatStreamEvent.ReasoningDelta("why"))
            onEvent(AiApiClient.ChatStreamEvent.ToolCallsUpdate(listOf(toolCall)))
            onEvent(AiApiClient.ChatStreamEvent.Usage(usage))
            onEvent(AiApiClient.ChatStreamEvent.TextDelta("lo"))
            onEvent(AiApiClient.ChatStreamEvent.Done)
        }
        val controller = ChatStreamController(this)

        val result = controller.stream(
            client = client,
            messages = listOf(ChatMessage(role = ChatRole.USER, content = "hello")),
        )

        val completed = result as ChatStreamController.StreamResult.Completed
        assertThat(completed.message.role).isEqualTo(ChatRole.ASSISTANT)
        assertThat(completed.message.content).isEqualTo("hello")
        assertThat(completed.message.reasoningContent).isEqualTo("why")
        assertThat(completed.message.toolCalls).containsExactly(toolCall)
        assertThat(completed.message.usage).isEqualTo(usage)
        assertThat(controller.content.value).isEqualTo("hello")
        assertThat(controller.reasoning.value).isEqualTo("why")
    }

    @Test
    fun `stream failure keeps partial snapshot available`(): Unit = runTest {
        val client = mockk<AiApiClient>()
        val failure = IllegalStateException("boom")
        coEvery { client.chatStream(any(), any(), any(), any()) } coAnswers {
            val onEvent = secondArg<(AiApiClient.ChatStreamEvent) -> Unit>()
            val onError = thirdArg<(Throwable) -> Unit>()
            onEvent(AiApiClient.ChatStreamEvent.TextDelta("partial"))
            onError(failure)
        }
        val controller = ChatStreamController(this)

        val result = controller.stream(
            client = client,
            messages = listOf(ChatMessage(role = ChatRole.USER, content = "hello")),
        )
        val snapshot = controller.snapshotPartialMessage(suffix = "stopped")

        assertThat(result).isEqualTo(ChatStreamController.StreamResult.Failed(failure))
        assertThat(snapshot?.content).isEqualTo("partial\n\nstopped")
    }

    @Test
    fun `stream cancellation cancels active client request`(): Unit = runTest {
        val client = mockk<AiApiClient>(relaxed = true)
        val started = CompletableDeferred<Unit>()
        coEvery { client.chatStream(any(), any(), any(), any()) } coAnswers {
            started.complete(Unit)
        }
        val controller = ChatStreamController(this)

        val job = launch {
            controller.stream(
                client = client,
                messages = listOf(ChatMessage(role = ChatRole.USER, content = "hello")),
            )
        }
        started.await()
        job.cancelAndJoin()

        verify(exactly = 1) { client.cancelRequest() }
        assertThat(controller.snapshotPartialMessage()).isNull()
    }

    @Test
    fun `stale callbacks from cancelled stream are ignored`(): Unit = runTest {
        val client = mockk<AiApiClient>(relaxed = true)
        val firstStarted = CompletableDeferred<Unit>()
        var staleEvent: ((AiApiClient.ChatStreamEvent) -> Unit)? = null
        var callCount = 0
        coEvery { client.chatStream(any(), any(), any(), any()) } coAnswers {
            callCount += 1
            val onEvent = secondArg<(AiApiClient.ChatStreamEvent) -> Unit>()
            if (callCount == 1) {
                staleEvent = onEvent
                firstStarted.complete(Unit)
            } else {
                onEvent(AiApiClient.ChatStreamEvent.TextDelta("new"))
                onEvent(AiApiClient.ChatStreamEvent.Done)
            }
        }
        val controller = ChatStreamController(this)

        val firstJob = launch {
            controller.stream(
                client = client,
                messages = listOf(ChatMessage(role = ChatRole.USER, content = "hello")),
            )
        }
        firstStarted.await()
        firstJob.cancelAndJoin()

        val second = controller.stream(
            client = client,
            messages = listOf(ChatMessage(role = ChatRole.USER, content = "hello again")),
        ) as ChatStreamController.StreamResult.Completed
        staleEvent?.invoke(AiApiClient.ChatStreamEvent.TextDelta("old"))
        staleEvent?.invoke(AiApiClient.ChatStreamEvent.Done)

        assertThat(second.message.content).isEqualTo("new")
        assertThat(controller.content.value).isEqualTo("new")
    }

    @Test
    fun `reset clears public state and empty snapshot`() {
        val controller = ChatStreamController(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job()))

        controller.reset()

        assertThat(controller.content.value).isEmpty()
        assertThat(controller.reasoning.value).isEmpty()
        assertThat(controller.toolCalls.value).isEmpty()
        assertThat(controller.usage.value).isNull()
        assertThat(controller.snapshotPartialMessage()).isNull()
        controller.cancel()
    }
}
