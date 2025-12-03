package com.wuxianggujun.tinaide.editor.language.cpp

import android.content.Context
import android.util.Log
import com.itsaky.androidide.treesitter.cpp.TSLanguageCpp
import io.github.rosemoe.sora.editor.ts.LocalsCaptureSpec
import io.github.rosemoe.sora.editor.ts.TsLanguage
import io.github.rosemoe.sora.editor.ts.TsLanguageSpec
import io.github.rosemoe.sora.editor.ts.TsThemeBuilder
import io.github.rosemoe.sora.lang.styling.TextStyle.makeStyle
import io.github.rosemoe.sora.lang.styling.textStyle
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Provides Tree-sitter powered syntax highlighting for C/C++ sources.
 */
object CppTreeSitterLanguageProvider {

    private const val TAG = "CppTreeSitterLanguage"
    private const val HIGHLIGHTS = "tree-sitter-queries/cpp/highlights.scm"
    private const val BLOCKS = "tree-sitter-queries/cpp/blocks.scm"
    private const val BRACKETS = "tree-sitter-queries/cpp/brackets.scm"
    private const val LOCALS = "tree-sitter-queries/cpp/locals.scm"

    private val nativeLoaded = AtomicBoolean(false)
    @Volatile
    private var cachedSources: QuerySources? = null

    fun create(context: Context): TsLanguage {
        ensureNativeLibraries()
        val sources = ensureSources(context.applicationContext)
        val spec = CppLanguageSpec(
            highlightScmSource = sources.highlights,
            codeBlocksScmSource = sources.blocks,
            bracketsScmSource = sources.brackets,
            localsScmSource = sources.locals
        )
        return TsLanguage(spec, tab = true) {
            applyCppTheme()
        }
    }

    private fun ensureNativeLibraries() {
        if (nativeLoaded.compareAndSet(false, true)) {
            try {
                System.loadLibrary("android-tree-sitter")
            } catch (_: UnsatisfiedLinkError) {
                // Already loaded by another language
            }
            System.loadLibrary("tree-sitter-cpp")
        }
    }

    private fun ensureSources(context: Context): QuerySources {
        return cachedSources ?: synchronized(this) {
            cachedSources ?: QuerySources(
                highlights = readAsset(context, HIGHLIGHTS),
                blocks = readAsset(context, BLOCKS),
                brackets = readAsset(context, BRACKETS),
                locals = readAsset(context, LOCALS)
            ).also { cachedSources = it }
        }
    }

    private fun readAsset(context: Context, path: String): String {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (ioe: IOException) {
            throw IllegalStateException("Missing Tree-sitter asset: $path", ioe)
        }
    }

    private data class QuerySources(
        val highlights: String,
        val blocks: String,
        val brackets: String,
        val locals: String
    )
}

private class CppLanguageSpec(
    highlightScmSource: String,
    codeBlocksScmSource: String,
    bracketsScmSource: String,
    localsScmSource: String
) : TsLanguageSpec(
    TSLanguageCpp.getInstance(),
    highlightScmSource,
    codeBlocksScmSource,
    bracketsScmSource,
    localsScmSource,
    CppLocalsCaptureSpec
)

private object CppLocalsCaptureSpec : LocalsCaptureSpec() {
    override fun isDefinitionCapture(captureName: String): Boolean {
        return captureName.startsWith("local.definition")
    }

    override fun isReferenceCapture(captureName: String): Boolean {
        return captureName == "local.reference"
    }

    override fun isScopeCapture(captureName: String): Boolean {
        return captureName == "local.scope"
    }

    override fun isMembersScopeCapture(captureName: String): Boolean {
        return captureName == "local.scope.members"
    }
}

private fun TsThemeBuilder.applyCppTheme() {
    textStyle(EditorColorScheme.COMMENT, italic = true) applyTo "comment"
    textStyle(EditorColorScheme.KEYWORD, bold = true) applyTo "keyword"
    makeStyle(EditorColorScheme.LITERAL) applyTo arrayOf(
        "string",
        "number",
        "constant",
        "boolean"
    )
    makeStyle(EditorColorScheme.IDENTIFIER_NAME) applyTo arrayOf(
        "type",
        "type.builtin",
        "namespace",
        "class",
        "struct"
    )
    makeStyle(EditorColorScheme.IDENTIFIER_VAR) applyTo arrayOf(
        "variable",
        "variable.builtin",
        "field",
        "property",
        "constant.macro"
    )
    makeStyle(EditorColorScheme.FUNCTION_NAME) applyTo arrayOf(
        "function",
        "function.method",
        "constructor",
        "destructor"
    )
    makeStyle(EditorColorScheme.OPERATOR) applyTo "operator"
}
