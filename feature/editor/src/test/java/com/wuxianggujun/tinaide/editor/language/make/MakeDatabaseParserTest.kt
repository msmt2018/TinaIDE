package com.wuxianggujun.tinaide.editor.language.make

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MakeDatabaseParserTest {

    @Test
    fun parseVariables_shouldKeepRequestedOriginsAndSupportedOperators() {
        val variables = MakeDatabaseParser.parseVariables(
            dbText = """
                # Variables
                # makefile
                CC := clang
                # environment
                CFLAGS = -O2
                # automatic
                @ = target
                # default
                SHELL = /bin/sh
                # variable set hash-table stats:
            """.trimIndent(),
            origins = setOf("makefile", "environment")
        )

        assertThat(variables).containsExactly(
            "CC", "clang",
            "CFLAGS", "-O2"
        ).inOrder()
    }

    @Test
    fun parseTargets_shouldReadRulesAndIgnoreSpecialOrDynamicTargets() {
        val recipeLine = "\t${'$'}(CC) main.c"
        val targets = MakeDatabaseParser.parseTargets(
            """
                # Files

                app main.o: main.c util.c | generated
                $recipeLine

                .PHONY: clean

                %.o: %.c

                ${'$'}(dynamic): input

                # VPATH Search Paths
            """.trimIndent()
        )

        assertThat(targets.keys).containsExactly("app", "main.o").inOrder()
        assertThat(targets["app"]?.prerequisites).containsExactly("main.c", "util.c", "generated")
    }

    @Test
    fun streamingExtractor_shouldCollectVariablesAndTargetsIncrementally() {
        val text = """
            # Variables
            # makefile
            BUILD_DIR = build
            # variable set hash-table stats:

            # Files
            app: main.o lib.o

            # VPATH Search Paths
        """.trimIndent()

        val extractor = MakeDatabaseParser.StreamingExtractor()
        text.lineSequence().forEach(extractor::acceptLine)

        val parsed = extractor.build()

        assertThat(parsed.variables).containsExactly("BUILD_DIR", "build")
        assertThat(parsed.targets.keys).containsExactly("app")
        assertThat(parsed.targets["app"]?.prerequisites).containsExactly("main.o", "lib.o")
    }
}
