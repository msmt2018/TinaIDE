package com.wuxianggujun.tinaide.editor.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IncludeParserTest {

    @Test
    fun parse_shouldReadSystemAndProjectIncludes() {
        val system = IncludeParser.parse("  #  include <sys/types.h>")
        val project = IncludeParser.parse("#include \"include/app/config.hpp\"")

        assertThat(system?.path).isEqualTo("sys/types.h")
        assertThat(system?.isSystemHeader).isTrue()
        assertThat(project?.path).isEqualTo("include/app/config.hpp")
        assertThat(project?.isSystemHeader).isFalse()
    }

    @Test
    fun parse_shouldExposePathColumnRange() {
        val line = "  #include <stdio.h>"
        val include = requireNotNull(IncludeParser.parse(line))

        assertThat(include.pathStartColumn).isEqualTo(line.indexOf("stdio.h"))
        assertThat(include.pathEndColumn).isEqualTo(line.indexOf("stdio.h") + "stdio.h".length)
        assertThat(IncludeParser.isColumnInPath(include.pathStartColumn, include)).isTrue()
        assertThat(IncludeParser.isColumnInPath(include.pathEndColumn, include)).isFalse()
    }

    @Test
    fun parse_shouldIgnoreNonIncludeLines() {
        assertThat(IncludeParser.parse("// #include <stdio.h>")).isNull()
        assertThat(IncludeParser.parse("#define include <stdio.h>")).isNull()
        assertThat(IncludeParser.parse("#include")).isNull()
    }
}
