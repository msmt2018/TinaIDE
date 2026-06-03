package com.wuxianggujun.tinaide.ai.tools

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ToolParameterParserTest {

    @Test
    fun `parseArguments preserves primitive array and object values`() {
        val args = ToolParameterParser.parseArguments(
            toolCall(
                "fake",
                """{"flag":true,"count":3,"items":["one"," two ",""],"nested":{"key":"value"}}"""
            )
        )

        assertThat(args["flag"]).isEqualTo("true")
        assertThat(args["count"]).isEqualTo("3")
        assertThat(args["nested"]).contains("key")
        assertThat(ToolParameterParser.getStringListParameter(args, "items"))
            .containsExactly("one", "two")
            .inOrder()
    }

    @Test
    fun `getStringListParameter supports comma separated fallback`() {
        val values = ToolParameterParser.getStringListParameter(
            mapOf("arguments" to " --verbose, ,--filter=Foo "),
            "arguments"
        )

        assertThat(values).containsExactly("--verbose", "--filter=Foo").inOrder()
    }

    @Test
    fun `getStringListParameter ignores non primitive json array entries`() {
        val values = ToolParameterParser.getStringListParameter(
            mapOf("arguments" to """["--verbose",{"flag":true},null," --filter=Foo "]"""),
            "arguments"
        )

        assertThat(values).containsExactly("--verbose", "--filter=Foo").inOrder()
    }

    @Test
    fun `typed parameter helpers apply defaults for invalid values`() {
        val args = mapOf("enabled" to "not-bool", "count" to "NaN")

        assertThat(ToolParameterParser.getBooleanParameter(args, "enabled", default = true)).isTrue()
        assertThat(ToolParameterParser.getIntParameter(args, "count", default = 7)).isEqualTo(7)
        assertThat(ToolParameterParser.getStringParameter(args, "missing", default = "fallback"))
            .isEqualTo("fallback")
    }

    @Test
    fun `parseArguments rejects invalid json`() {
        assertFailsWith<IllegalArgumentException> {
            ToolParameterParser.parseArguments(toolCall("fake", "{invalid-json"))
        }
    }

    @Test
    fun `parseArguments rejects non object json`() {
        assertFailsWith<IllegalArgumentException> {
            ToolParameterParser.parseArguments(toolCall("fake", """["path","value"]"""))
        }
    }
}
