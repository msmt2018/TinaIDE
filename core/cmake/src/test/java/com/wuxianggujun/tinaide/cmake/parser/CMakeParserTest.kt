/*
 * CMake Parser Tests for TinaIDE
 * Copyright (C) 2024 wuxianggujun
 */

package com.wuxianggujun.tinaide.cmake.parser

import org.junit.Assert.*
import org.junit.Test

class CMakeLexerTest {

    @Test
    fun testParseIdentifier() {
        val lexer = CMakeLexer("set VAR value")
        val token = lexer.parseIdentifier()

        assertNotNull(token)
        assertEquals("set", token!!.text)
        assertFalse(token.quoted)
    }

    @Test
    fun testParseIdentifierWithUnderscore() {
        val lexer = CMakeLexer("my_variable_name")
        val token = lexer.parseIdentifier()

        assertNotNull(token)
        assertEquals("my_variable_name", token!!.text)
    }

    @Test
    fun testParseQuotedArgument() {
        val lexer = CMakeLexer("\"hello world\"")
        val result = lexer.parseQuotedArgument()

        assertTrue(result.isSuccess)
        val token = result.getOrNull()!!
        assertEquals("hello world", token.text)
        assertTrue(token.quoted)
    }

    @Test
    fun testParseQuotedArgumentWithEscape() {
        val lexer = CMakeLexer("\"hello\\nworld\"")
        val result = lexer.parseQuotedArgument()

        assertTrue(result.isSuccess)
        assertEquals("hello\nworld", result.getOrNull()!!.text)
    }

    @Test
    fun testParseQuotedArgumentWithLineContinuation() {
        val lexer = CMakeLexer("\"hello\\\n world\"")
        val result = lexer.parseQuotedArgument()

        assertTrue(result.isSuccess)
        assertEquals("hello world", result.getOrNull()!!.text)
    }

    @Test
    fun testParseBracketArgument() {
        val lexer = CMakeLexer("[[hello world]]")
        val result = lexer.parseBracketArgument()

        assertTrue(result.isSuccess)
        assertEquals("hello world", result.getOrNull()!!.text)
    }

    @Test
    fun testParseBracketArgumentWithEquals() {
        val lexer = CMakeLexer("[==[hello]] world]==]")
        val result = lexer.parseBracketArgument()

        assertTrue(result.isSuccess)
        assertEquals("hello]] world", result.getOrNull()!!.text)
    }

    @Test
    fun testParseUnquotedArgument() {
        val lexer = CMakeLexer("hello")
        val result = lexer.parseUnquotedArgument()

        assertTrue(result.isSuccess)
        assertEquals("hello", result.getOrNull()!!.text)
        assertFalse(result.getOrNull()!!.quoted)
    }

    @Test
    fun testParseUnquotedArgumentWithEscape() {
        val lexer = CMakeLexer("hello\\;world")
        val result = lexer.parseUnquotedArgument()

        assertTrue(result.isSuccess)
        assertEquals("hello;world", result.getOrNull()!!.text)
    }

    @Test
    fun testSkipSpaces() {
        val lexer = CMakeLexer("   hello")
        val count = lexer.skipSpaces()

        assertEquals(3, count)
        assertEquals('h', lexer.currentChar())
    }

    @Test
    fun testUnterminatedString() {
        val lexer = CMakeLexer("\"hello")
        val result = lexer.parseQuotedArgument()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CMakeLexer.LexerError.UnterminatedString)
    }

    @Test
    fun testUnterminatedBracket() {
        val lexer = CMakeLexer("[[hello")
        val result = lexer.parseBracketArgument()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CMakeLexer.LexerError.UnterminatedBracket)
    }

    // ========== Legacy Unquoted 参数测试 ==========

    @Test
    fun testParseLegacyUnquotedWithEmbeddedQuotes() {
        // -DVERSION="1.0.0" 应该被解析为一个完整的 token
        val lexer = CMakeLexer("-DVERSION=\"1.0.0\"")
        val result = lexer.parseLegacyUnquotedArgument()

        assertTrue(result.isSuccess)
        assertEquals("-DVERSION=\"1.0.0\"", result.getOrNull()!!.text)
        assertFalse(result.getOrNull()!!.quoted)
    }

    @Test
    fun testParseLegacyUnquotedWithSpacesInQuotes() {
        // -Da="b c" 内嵌引号包含空格
        val lexer = CMakeLexer("-Da=\"b c\"")
        val result = lexer.parseLegacyUnquotedArgument()

        assertTrue(result.isSuccess)
        assertEquals("-Da=\"b c\"", result.getOrNull()!!.text)
    }

    @Test
    fun testParseLegacyUnquotedOldStyleVariable() {
        // $(VAR) 旧式变量引用
        val lexer = CMakeLexer("\$(MY_VAR)")
        val result = lexer.parseLegacyUnquotedArgument()

        assertTrue(result.isSuccess)
        assertEquals("\$(MY_VAR)", result.getOrNull()!!.text)
    }

    @Test
    fun testParseLegacyUnquotedEnvVariable() {
        // $ENV(PATH) 环境变量
        val lexer = CMakeLexer("\$ENV(PATH)")
        val result = lexer.parseLegacyUnquotedArgument()

        assertTrue(result.isSuccess)
        assertEquals("\$ENV(PATH)", result.getOrNull()!!.text)
    }

    @Test
    fun testParseLegacyUnquotedMixed() {
        // 混合格式: -Da="b" 后面跟其他内容
        val lexer = CMakeLexer("-Da=\"b\"_suffix")
        val result = lexer.parseLegacyUnquotedArgument()

        assertTrue(result.isSuccess)
        assertEquals("-Da=\"b\"_suffix", result.getOrNull()!!.text)
    }

    @Test
    fun testParseLegacyUnquotedMultipleQuotes() {
        // a"b"c"d" 多个内嵌引号
        val lexer = CMakeLexer("a\"b\"c\"d\"")
        val result = lexer.parseLegacyUnquotedArgument()

        assertTrue(result.isSuccess)
        assertEquals("a\"b\"c\"d\"", result.getOrNull()!!.text)
    }

    @Test
    fun testParseLegacyUnquotedStandardVariable() {
        // ${VAR} 标准变量格式也支持
        val lexer = CMakeLexer("prefix\${VAR}suffix")
        val result = lexer.parseLegacyUnquotedArgument()

        assertTrue(result.isSuccess)
        assertEquals("prefix\${VAR}suffix", result.getOrNull()!!.text)
    }

    @Test
    fun testParseArgumentAutoDetectsLegacy() {
        // parseArgument() 应该自动检测并使用 Legacy 模式
        val lexer = CMakeLexer("-DFOO=\"bar\"")
        val result = lexer.parseArgument()

        assertNotNull(result)
        assertTrue(result!!.isSuccess)
        assertEquals("-DFOO=\"bar\"", result.getOrNull()!!.text)
    }

    @Test
    fun testParseLegacyUnquotedNestedParens() {
        // $(OUTER$(INNER)) 嵌套括号
        val lexer = CMakeLexer("\$(OUTER\$(INNER))")
        val result = lexer.parseLegacyUnquotedArgument()

        assertTrue(result.isSuccess)
        assertEquals("\$(OUTER\$(INNER))", result.getOrNull()!!.text)
    }
}

class CMakeParserTest {

    @Test
    fun testParseSimpleCommand() {
        val source = "message(STATUS \"Hello\")\n"
        val result = CMakeParser.parse(source)

        assertTrue(result.isSuccess)
        val doc = result.getOrNull()!!
        assertEquals(1, doc.commands().size)

        val cmd = doc.commands()[0]
        assertEquals("message", cmd.identifier)
        assertEquals(2, cmd.arguments.size)
        assertEquals("STATUS", cmd.arguments[0].text)
        assertEquals("Hello", cmd.arguments[1].text)
    }

    @Test
    fun testParseSetCommand() {
        val source = "set(MY_VAR \"value1\" \"value2\")\n"
        val result = CMakeParser.parse(source)

        assertTrue(result.isSuccess)
        val doc = result.getOrNull()!!
        assertEquals(1, doc.commands().size)

        val cmd = doc.commands()[0]
        assertEquals("set", cmd.identifier)
        assertEquals(3, cmd.arguments.size)
    }

    @Test
    fun testParseProject() {
        val source = """
            cmake_minimum_required(VERSION 3.14)
            project(MyProject VERSION 1.0.0 LANGUAGES CXX)
        """.trimIndent() + "\n"

        val result = CMakeParser.parse(source)

        assertTrue(result.isSuccess)
        val doc = result.getOrNull()!!
        assertEquals(2, doc.commands().size)

        assertEquals("cmake_minimum_required", doc.commands()[0].identifier)
        assertEquals("project", doc.commands()[1].identifier)
    }

    @Test
    fun testParseAddExecutable() {
        val source = "add_executable(myapp main.cpp util.cpp)\n"
        val result = CMakeParser.parse(source)

        assertTrue(result.isSuccess)
        val cmd = result.getOrNull()!!.commands()[0]
        assertEquals("add_executable", cmd.identifier)
        assertEquals(3, cmd.arguments.size)
    }

    @Test
    fun testParseTargetLinkLibraries() {
        val source = "target_link_libraries(myapp PRIVATE lib1 lib2 PUBLIC lib3)\n"
        val result = CMakeParser.parse(source)

        assertTrue(result.isSuccess)
        val cmd = result.getOrNull()!!.commands()[0]
        assertEquals("target_link_libraries", cmd.identifier)
    }

    @Test
    fun testParseIfBlock() {
        val source = """
            if(CONDITION)
                message(STATUS "True")
            else()
                message(STATUS "False")
            endif()
        """.trimIndent() + "\n"

        val result = CMakeParser.parse(source)

        assertTrue(result.isSuccess)
        val commands = result.getOrNull()!!.commands()
        assertEquals(5, commands.size)
        assertEquals("if", commands[0].identifier)
        assertEquals("message", commands[1].identifier)
        assertEquals("else", commands[2].identifier)
        assertEquals("message", commands[3].identifier)
        assertEquals("endif", commands[4].identifier)
    }

    @Test
    fun testParseForEach() {
        val source = """
            foreach(item IN ITEMS a b c)
                message(STATUS ${"\${item}"})
            endforeach()
        """.trimIndent() + "\n"

        val result = CMakeParser.parse(source)

        assertTrue(result.isSuccess)
        val commands = result.getOrNull()!!.commands()
        assertEquals(3, commands.size)
        assertEquals("foreach", commands[0].identifier)
        assertEquals("endforeach", commands[2].identifier)
    }

    @Test
    fun testParseFunction() {
        val source = """
            function(my_func ARG1 ARG2)
                message(STATUS ${"\${ARG1}"})
            endfunction()
        """.trimIndent() + "\n"

        val result = CMakeParser.parse(source)

        assertTrue(result.isSuccess)
        val commands = result.getOrNull()!!.commands()
        assertEquals(3, commands.size)
        assertEquals("function", commands[0].identifier)
        assertEquals("endfunction", commands[2].identifier)
    }

    @Test
    fun testParseMultilineCommand() {
        val source = """
            set(SOURCES
                main.cpp
                util.cpp
                helper.cpp
            )
        """.trimIndent() + "\n"

        val result = CMakeParser.parse(source)

        assertTrue(result.isSuccess)
        val cmd = result.getOrNull()!!.commands()[0]
        assertEquals("set", cmd.identifier)
        assertEquals(4, cmd.arguments.size) // SOURCES + 3 files
    }

    @Test
    fun testParseWithComments() {
        val source = """
            # This is a comment
            set(VAR "value") # Inline comment
        """.trimIndent() + "\n"

        val result = CMakeParser.parse(source)

        assertTrue(result.isSuccess)
        val commands = result.getOrNull()!!.commands()
        assertEquals(1, commands.size)
        assertEquals("set", commands[0].identifier)
    }

    @Test
    fun testParseBracketComment() {
        val source = """
            #[[
            This is a
            multi-line comment
            ]]
            set(VAR "value")
        """.trimIndent() + "\n"

        val result = CMakeParser.parse(source)

        assertTrue(result.isSuccess)
        val commands = result.getOrNull()!!.commands()
        assertEquals(1, commands.size)
    }

    @Test
    fun testParseNestedParentheses() {
        val source = "set(VAR \"\${OTHER_VAR}\")\n"
        val result = CMakeParser.parse(source)

        assertTrue(result.isSuccess)
        val cmd = result.getOrNull()!!.commands()[0]
        assertEquals(2, cmd.arguments.size)
    }

    @Test
    fun testParseEmptyFile() {
        val result = CMakeParser.parse("")

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.commands().size)
    }

    @Test
    fun testParseOnlyComments() {
        val source = """
            # Comment 1
            # Comment 2
        """.trimIndent() + "\n"

        val result = CMakeParser.parse(source)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.commands().size)
    }

    @Test
    fun testCommandsByName() {
        val source = """
            set(VAR1 "a")
            message(STATUS "msg")
            set(VAR2 "b")
        """.trimIndent() + "\n"

        val result = CMakeParser.parse(source)
        assertTrue(result.isSuccess)

        val setCommands = result.getOrNull()!!.commandsByName("set")
        assertEquals(2, setCommands.size)

        val msgCommands = result.getOrNull()!!.commandsByName("MESSAGE") // 大小写无关
        assertEquals(1, msgCommands.size)
    }

    // ========== Legacy 语法集成测试 ==========

    @Test
    fun testParseAddDefinitionsWithLegacy() {
        // 常见用例: add_definitions(-DVERSION="1.0.0")
        val source = "add_definitions(-DVERSION=\"1.0.0\")\n"
        val result = CMakeParser.parse(source)

        assertTrue(result.isSuccess)
        val cmd = result.getOrNull()!!.commands()[0]
        assertEquals("add_definitions", cmd.identifier)
        assertEquals(1, cmd.arguments.size)
        assertEquals("-DVERSION=\"1.0.0\"", cmd.arguments[0].text)
    }

    @Test
    fun testParseCompileOptionsWithLegacy() {
        // target_compile_options 中的 Legacy 参数
        val source = "target_compile_options(app PRIVATE -DFOO=\"bar\" -DBAZ=\"qux\")\n"
        val result = CMakeParser.parse(source)

        assertTrue(result.isSuccess)
        val cmd = result.getOrNull()!!.commands()[0]
        assertEquals("target_compile_options", cmd.identifier)
        assertEquals(4, cmd.arguments.size)
        assertEquals("-DFOO=\"bar\"", cmd.arguments[2].text)
        assertEquals("-DBAZ=\"qux\"", cmd.arguments[3].text)
    }

    @Test
    fun testParseSetWithOldStyleVariable() {
        // 旧式变量引用 $(VAR)
        val source = "set(FLAGS \$(OLD_FLAGS))\n"
        val result = CMakeParser.parse(source)

        assertTrue(result.isSuccess)
        val cmd = result.getOrNull()!!.commands()[0]
        assertEquals(2, cmd.arguments.size)
        assertEquals("\$(OLD_FLAGS)", cmd.arguments[1].text)
    }

    @Test
    fun testParseMixedLegacyAndStandard() {
        // 混合 Legacy 和标准参数
        val source = """
            set(FLAGS
                -DVERSION="1.0"
                -DDEBUG
                normal_arg
            )
        """.trimIndent() + "\n"

        val result = CMakeParser.parse(source)
        assertTrue(result.isSuccess)

        val cmd = result.getOrNull()!!.commands()[0]
        assertEquals("set", cmd.identifier)
        assertEquals(4, cmd.arguments.size)
        assertEquals("FLAGS", cmd.arguments[0].text)
        assertEquals("-DVERSION=\"1.0\"", cmd.arguments[1].text)
        assertEquals("-DDEBUG", cmd.arguments[2].text)
        assertEquals("normal_arg", cmd.arguments[3].text)
    }
}
