package com.wuxianggujun.tinaide.editor.symbol

import com.itsaky.androidide.treesitter.TSLanguage
import com.itsaky.androidide.treesitter.TSNode
import com.itsaky.androidide.treesitter.cpp.TSLanguageCpp
import com.wuxianggujun.tinaide.core.lang.CxxFileSupport
import java.util.ArrayDeque

/**
 * C/C++ 符号提取 Provider。
 *
 * 从 Tree-sitter C++ AST 中提取全局符号（namespace / enum / class / struct / function / variable），
 * 服务于 Outline / Symbols 面板。
 */
internal class CxxSymbolProvider : LanguageSymbolProvider {

    override val supportedExtensions: Set<String> = CxxFileSupport.editorRelatedExtensions

    override fun createLanguage(): TSLanguage = TSLanguageCpp.getInstance()

    private data class VariableDecl(val startIndex: Int, val typeName: String)

    override fun extractSymbols(root: TSNode, source: String): List<GlobalSymbol> {
        if (!root.canAccess()) {
            return emptyList()
        }
        val variableDecls = LinkedHashMap<String, VariableDecl>()

        val globals = LinkedHashSet<GlobalSymbol>()
        val sourceLines = source.lines()

        val stack = ArrayDeque<Pair<TSNode, Boolean>>()
        stack.addLast(root to false)

        while (stack.isNotEmpty()) {
            val (node, inTypeBody) = stack.removeLast()

            if (!node.canAccess()) {
                break
            }

            val type = node.type

            when (type) {
                "namespace_definition" -> {
                    extractNamespaceNode(node)?.let { nameNode ->
                        val ns = nodeText(nameNode, source)?.trim().orEmpty()
                        if (ns.isNotEmpty()) {
                            globals.add(
                                GlobalSymbol(
                                    name = ns,
                                    kind = SymbolKind.Namespace,
                                    detail = "namespace",
                                    location = locationOf(nameNode),
                                    signature = null,
                                    documentation = extractDocComment(node, sourceLines),
                                )
                            )
                        }
                    }
                }

                "enum_specifier" -> {
                    val nameNode = node.getChildByFieldName("name")
                    val enumName = extractTypeName(nameNode, source)
                    if (enumName == null) {
                        pushChildren(node, inTypeBody, stack)
                    } else {
                        globals.add(
                            GlobalSymbol(
                                name = enumName,
                                kind = SymbolKind.Enum,
                                detail = "enum",
                                location = nameNode?.let(::locationOf),
                                signature = null,
                                documentation = extractDocComment(node, sourceLines),
                            )
                        )
                    }
                }

                "class_specifier", "struct_specifier" -> {
                    val kind = if (type == "class_specifier") SymbolKind.Class else SymbolKind.Struct
                    val nameNode = node.getChildByFieldName("name")
                    val typeName = extractTypeName(nameNode, source)
                    if (typeName == null) {
                        pushChildren(node, inTypeBody, stack)
                    } else {
                        globals.add(
                            GlobalSymbol(
                                name = typeName,
                                kind = kind,
                                detail = kind.name.lowercase(),
                                location = nameNode?.let(::locationOf),
                                signature = null,
                                documentation = extractDocComment(node, sourceLines),
                            )
                        )
                    }
                }

                "function_definition" -> {
                    extractFunction(node, source, sourceLines)?.let { fn ->
                        globals.add(
                            GlobalSymbol(
                                name = fn.name,
                                kind = SymbolKind.Function,
                                detail = "function",
                                location = fn.location,
                                signature = fn.signature,
                                documentation = fn.documentation,
                            )
                        )
                    }
                }

                "declaration", "parameter_declaration" -> {
                    if (!inTypeBody) {
                        extractVariableDecls(node, source, variableDecls)
                    }
                }
            }

            val nextInTypeBody = inTypeBody || type == "class_specifier" || type == "struct_specifier"
            pushChildren(node, nextInTypeBody, stack)
        }

        for ((name, decl) in variableDecls) {
            val typeName = decl.typeName
            globals.add(
                GlobalSymbol(
                    name = name,
                    kind = SymbolKind.Variable,
                    detail = if (typeName.isEmpty()) "variable" else "variable: $typeName",
                    signature = if (typeName.isNotEmpty()) "$typeName $name" else null,
                    documentation = null,
                )
            )
        }

        return globals
            .asSequence()
            .distinctBy { it.kind to it.name }
            .sortedWith(compareBy<GlobalSymbol>({ it.name }, { it.kind.ordinal }))
            .toList()
    }

    private fun pushChildren(node: TSNode, inTypeBody: Boolean, stack: ArrayDeque<Pair<TSNode, Boolean>>) {
        if (!node.canAccess()) return
        val count = node.namedChildCount
        for (i in count - 1 downTo 0) {
            val child = node.getNamedChild(i)
            if (child.canAccess()) {
                stack.addLast(child to inTypeBody)
            }
        }
    }

    private fun extractNamespaceNode(node: TSNode): TSNode? {
        if (!node.canAccess()) return null
        val nameNode = node.getChildByFieldName("name") ?: return null
        if (!nameNode.canAccess() || nameNode.isNull) return null
        return nameNode
    }

    private data class FunctionSymbol(
        val name: String,
        val location: SymbolLocation,
        val signature: String? = null,
        val documentation: String? = null,
    )

    private fun extractFunction(node: TSNode, source: String, sourceLines: List<String>): FunctionSymbol? {
        if (!node.canAccess()) return null
        val declarator = node.getChildByFieldName("declarator") ?: return null
        if (!declarator.canAccess()) return null
        val nameNode = findIdentifierNodeInDeclarator(declarator, preferFieldIdentifier = false) ?: return null
        val name = nodeText(nameNode, source)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val signature = extractFunctionSignature(node, source)
        val documentation = extractDocComment(node, sourceLines)
        return FunctionSymbol(
            name = name,
            location = locationOf(nameNode),
            signature = signature,
            documentation = documentation,
        )
    }

    private fun extractVariableDecls(
        node: TSNode,
        source: String,
        out: MutableMap<String, VariableDecl>,
    ) {
        if (!node.canAccess()) return
        val typeName = extractDeclaredTypeName(node, source) ?: return

        val declarators = findTopLevelDeclarators(node)
        for (decl in declarators) {
            if (!decl.canAccess()) continue
            val identNode = findIdentifierNodeInDeclarator(decl, preferFieldIdentifier = false) ?: continue
            val name = nodeText(identNode, source)?.trim().orEmpty()
            if (name.isBlank()) continue
            val startIndex = identNode.startByte / 2
            val existing = out[name]
            if (existing == null || startIndex >= existing.startIndex) {
                out[name] = VariableDecl(startIndex = startIndex, typeName = typeName)
            }
        }
    }

    private fun findTopLevelDeclarators(node: TSNode): List<TSNode> {
        if (!node.canAccess()) return emptyList()
        val result = mutableListOf<TSNode>()
        val count = node.namedChildCount
        for (i in 0 until count) {
            val child = node.getNamedChild(i)
            if (!child.canAccess()) continue
            when (child.type) {
                "init_declarator" -> {
                    val decl = child.getChildByFieldName("declarator")
                    if (decl != null && decl.canAccess() && !decl.isNull) result.add(decl)
                }

                "identifier", "pointer_declarator", "reference_declarator", "array_declarator", "function_declarator" -> {
                    result.add(child)
                }
            }
        }
        return result
    }

    private fun extractDeclaredTypeName(node: TSNode, source: String): String? {
        if (!node.canAccess()) return null
        val typeNode = node.getChildByFieldName("type") ?: return null
        val name = extractTypeName(typeNode, source) ?: return null
        return name.ifBlank { null }
    }

    private fun extractTypeName(nameNode: TSNode?, source: String): String? {
        if (nameNode == null || !nameNode.canAccess() || nameNode.isNull) return null
        return when (nameNode.type) {
            "type_identifier", "identifier" -> nodeText(nameNode, source)
            "qualified_identifier" -> {
                val last = nameNode.getChildByFieldName("name")
                extractTypeName(last, source) ?: nodeText(nameNode, source)
            }

            else -> nodeText(nameNode, source)
        }?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun findIdentifierNodeInDeclarator(
        declarator: TSNode,
        preferFieldIdentifier: Boolean,
    ): TSNode? {
        if (!declarator.canAccess()) return null
        val stack = ArrayDeque<TSNode>()
        stack.addLast(declarator)

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (!node.canAccess()) continue
            val type = node.type

            if (preferFieldIdentifier && type == "field_identifier") {
                return node
            }
            if (!preferFieldIdentifier && type == "identifier") {
                return node
            }
            if (type == "field_identifier" || type == "identifier") {
                return node
            }

            val next = node.getChildByFieldName("declarator")
            if (next != null && next.canAccess() && !next.isNull) {
                stack.addLast(next)
                continue
            }

            val count = node.namedChildCount
            for (i in count - 1 downTo 0) {
                val child = node.getNamedChild(i)
                if (child.canAccess()) {
                    stack.addLast(child)
                }
            }
        }
        return null
    }

    private fun locationOf(node: TSNode): SymbolLocation {
        if (!node.canAccess()) return SymbolLocation(line = 0, column = 0)
        val start = node.startPoint
        return SymbolLocation(
            line = start.row,
            column = start.column / 2,
        )
    }

    private fun nodeText(node: TSNode?, source: String): String? {
        if (node == null || !node.canAccess() || node.isNull) return null
        val start = (node.startByte / 2).coerceIn(0, source.length)
        val end = (node.endByte / 2).coerceIn(0, source.length)
        if (end <= start) return null
        return source.substring(start, end)
    }

    private fun extractFunctionSignature(functionNode: TSNode, source: String): String? {
        if (!functionNode.canAccess()) return null
        val declarator = functionNode.getChildByFieldName("declarator") ?: return null
        val returnType = functionNode.getChildByFieldName("type")

        val returnTypeText = returnType?.let { nodeText(it, source)?.trim() } ?: "void"
        val declaratorText = nodeText(declarator, source)?.trim() ?: return null

        return "$returnTypeText $declaratorText"
    }

    private fun extractDocComment(node: TSNode, lines: List<String>): String? {
        if (!node.canAccess() || node.isNull) return null

        val startLine = node.startPoint.row
        if (startLine == 0) return null

        val comments = mutableListOf<String>()

        for (i in (startLine - 1) downTo maxOf(0, startLine - 5)) {
            val line = lines.getOrNull(i)?.trim() ?: continue

            when {
                line.startsWith("///") -> {
                    comments.add(0, line.removePrefix("///").trim())
                }
                line.startsWith("//") -> {
                    comments.add(0, line.removePrefix("//").trim())
                }
                line.contains("*/") && !line.startsWith("*/") -> {
                    val commentContent = line
                        .substringAfter("/*")
                        .substringBefore("*/")
                        .trim()
                    if (commentContent.isNotEmpty()) {
                        comments.add(0, commentContent)
                    }
                    break
                }
                line.isEmpty() -> continue
                else -> break
            }
        }

        return if (comments.isNotEmpty()) {
            comments.joinToString(" ").take(200)
        } else {
            null
        }
    }
}
