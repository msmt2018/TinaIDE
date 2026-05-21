package com.wuxianggujun.tinaide.ui.compose.screens.testing

import com.google.common.truth.Truth.assertThat
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.Test

class CompilerProjectSmokeSupportTest {

    @Test
    fun scenarioSpecs_shouldRemainSelfConsistent() {
        val scenarios = CompilerProjectSmokeTestSupport.scenarioSpecs()

        assertThat(scenarios.map { it.id }).containsExactly(
            "single_file_std_headers",
            "cmake_std_headers",
            "sdl3_std_headers"
        ).inOrder()
        assertThat(scenarios.map { it.id }.distinct()).hasSize(scenarios.size)

        scenarios.forEach { scenario ->
            assertThat(scenario.files).containsKey(scenario.targetFileRelativePath)
            assertThat(scenario.files.getValue(scenario.targetFileRelativePath))
                .contains(CompilerProjectSmokeTestSupport.completionPlaceholder)
            assertThat(scenario.buildReplacement).isNotEmpty()
            assertThat(scenario.completionReplacement).contains(scenario.completionNeedle)
            assertThat(scenario.expectedCompletionLabels).isNotEmpty()
        }
    }

    @Test
    fun completionHelpers_shouldHandleEitherShapesAndOffsets() {
        val content = """
            int main() {
                names.em
            }
        """.trimIndent()
        val endOffset = content.indexOf("names.em") + "names.em".length
        val expectedPosition = CompilerProjectSmokeTestSupport.offsetToLineColumnInText(content, endOffset)
        val resolvedPosition = CompilerProjectSmokeTestSupport.resolveCompletionPositionInContent(content, "names.em")

        val leftItems: Either<List<CompletionItem>, CompletionList> = Either.forLeft(
            listOf(CompletionItem("empty"), CompletionItem("emplace_back"))
        )
        val rightItems: Either<List<CompletionItem>, CompletionList> = Either.forRight(
            CompletionList().apply {
                items = listOf(CompletionItem("push_back"))
            }
        )

        assertThat(resolvedPosition).isEqualTo(expectedPosition)
        assertThat(CompilerProjectSmokeTestSupport.resolveCompletionPositionInContent(content, "missing")).isNull()
        assertThat(CompilerProjectSmokeTestSupport.extractCompletionLabelsFromResult(leftItems))
            .containsExactly("empty", "emplace_back")
            .inOrder()
        assertThat(CompilerProjectSmokeTestSupport.extractCompletionLabelsFromResult(rightItems))
            .containsExactly("push_back")
        assertThat(
            CompilerProjectSmokeTestSupport.normalizeCompletionLabelsForMatch(
                listOf(" emplace", " emplace_back", "empty", " ", "empty")
            )
        ).containsExactly("emplace", "emplace_back", "empty").inOrder()
    }

    @Test
    fun formattingHelpers_shouldKeepReadableOutput() {
        val shortDuration = CompilerProjectSmokeTestSupport.formatDuration(85)
        val longDuration = CompilerProjectSmokeTestSupport.formatDuration(1_520)
        val limitedText = CompilerProjectSmokeTestSupport.limitReportText("x".repeat(32), maxChars = 12)

        assertThat(shortDuration).isEqualTo("85 ms")
        assertThat(longDuration).isEqualTo("1.52 s")
        assertThat(limitedText).startsWith("xxxxxxxxxxxx")
        assertThat(limitedText).contains("...<截断>...")
    }

    @Test
    fun sampleLoggerResult_shouldAggregateChecksMetricsAndFailureMetadata() {
        val scenarioId = CompilerProjectSmokeTestSupport.scenarioSpecs().first().id
        val result = CompilerProjectSmokeTestSupport.buildSampleLoggerResult(
            scenarioId = scenarioId,
            success = false,
            failureDetail = "detail-".repeat(400)
        )

        assertThat(result.success).isFalse()
        assertThat(result.failedStage).isEqualTo("clangd diagnostics")
        assertThat(result.failureReason).isEqualTo("存在 error 级诊断")
        assertThat(result.passedChecksCount).isEqualTo(1)
        assertThat(result.totalChecksCount).isEqualTo(2)
        assertThat(result.stageTimings.map { it.label }).containsExactly("准备", "补全").inOrder()
        assertThat(result.stageTimings.first { it.label == "准备" }.durationMs).isEqualTo(95)
        assertThat(result.matchedCompletionLabels).containsExactly("empty", "emplace_back").inOrder()
        assertThat(result.reportText).contains("[PASS] compile_commands - updated")
        assertThat(result.reportText).contains("[FAIL] clangd diagnostics - 1 error")
        assertThat(result.reportText).contains("关键指标:")
        assertThat(result.reportText).contains("...<截断>...")
    }

    @Test
    fun unhandledExceptionResult_shouldDescribeScenarioAndFailureStage() {
        val scenario = CompilerProjectSmokeTestSupport.scenarioSpecs().first()
        val result = CompilerProjectSmokeTestSupport.buildUnhandledExceptionResultForScenario(
            scenarioId = scenario.id,
            throwable = IllegalStateException("boom")
        )

        assertThat(result.success).isFalse()
        assertThat(result.scenarioId).isEqualTo(scenario.id)
        assertThat(result.failedStage).isEqualTo("执行异常")
        assertThat(result.failureReason).isEqualTo("boom")
        assertThat(result.reportText).contains(scenario.displayName)
        assertThat(result.reportText).contains("失败阶段: 执行异常")
    }
}
