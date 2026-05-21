package com.wuxianggujun.tinaide.ui.compose.screens.testing

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.ai.api.ChatRole
import org.junit.Test

class AiChatTestScreenSupportTest {

    @Test
    fun buildScenarios_shouldProvideValidCatalogAndCoverage() {
        val scenarios = AiChatTestScreenSupport.buildScenarios()
        val validation = AiChatTestScreenSupport.validateScenarioCatalog(scenarios)

        assertThat(scenarios).hasSize(6)
        assertThat(validation.scenarioCount).isEqualTo(6)
        assertThat(validation.isValid).isTrue()
        assertThat(validation.issues).isEmpty()
        assertThat(validation.hasMarkdownCodeFence).isTrue()
        assertThat(validation.hasToolCallScenario).isTrue()
        assertThat(validation.hasToolResponseScenario).isTrue()
        assertThat(validation.hasUsageScenario).isTrue()
        assertThat(validation.hasTableScenario).isTrue()
    }

    @Test
    fun buildScenarios_shouldKeepToolCallAndToolResponseLinked() {
        val toolScenario = AiChatTestScreenSupport.buildScenarios()
            .single { scenario ->
                scenario.messages.any { message -> !message.toolCalls.isNullOrEmpty() }
            }
        val declaredToolCallIds = toolScenario.messages
            .flatMap { message -> message.toolCalls.orEmpty() }
            .mapNotNull { toolCall -> toolCall.id }
        val toolResponseIds = toolScenario.messages
            .filter { message -> message.role == ChatRole.TOOL }
            .mapNotNull { message -> message.toolCallId }

        assertThat(declaredToolCallIds).containsExactly("call_1")
        assertThat(toolResponseIds).containsExactly("call_1")
    }

    @Test
    fun buildScenarios_shouldExposeUsageMetadataScenario() {
        val usageMessage = AiChatTestScreenSupport.buildScenarios()
            .flatMap { scenario -> scenario.messages }
            .single { message -> message.usage != null }

        assertThat(usageMessage.role).isEqualTo(ChatRole.ASSISTANT)
        assertThat(usageMessage.usage?.promptTokens).isEqualTo(15)
        assertThat(usageMessage.usage?.completionTokens).isEqualTo(320)
        assertThat(usageMessage.usage?.totalTokens).isEqualTo(335)
    }
}
