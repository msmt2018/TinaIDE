package com.wuxianggujun.tinaide.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiagnosticLogFormatterTest {

    @Test
    fun `format renders stable key value pairs in order`() {
        val message = DiagnosticLogFormatter.format(
            prefix = "Native exec diag",
            "sdk" to 28,
            "preferLinker64" to true,
            "launchMode" to "linker64",
            "cwd" to "/tmp/work"
        )

        assertThat(message).isEqualTo(
            "Native exec diag: sdk=28, preferLinker64=true, launchMode=linker64, cwd=/tmp/work"
        )
    }

    @Test
    fun `format falls back to null marker when value is null`() {
        val message = DiagnosticLogFormatter.format(
            prefix = "Resolved build tool",
            "buildToolShim" to null
        )

        assertThat(message).isEqualTo("Resolved build tool: buildToolShim=<null>")
    }
}
