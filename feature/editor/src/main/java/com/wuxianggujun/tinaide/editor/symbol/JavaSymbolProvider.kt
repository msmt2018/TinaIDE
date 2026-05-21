package com.wuxianggujun.tinaide.editor.symbol

import com.itsaky.androidide.treesitter.TSLanguage
import com.itsaky.androidide.treesitter.TSNode
import com.itsaky.androidide.treesitter.java.TSLanguageJava
import java.util.ArrayDeque

/**
 * Java 符号提取 Provider。
 *
 * 提取全局符号：Class / Interface / Enum / Method / Field。
 */
internal class JavaSymbolProvider : LanguageSymbolProvider {

    override val supportedExtensions: Set<String> = setOf("java")

    override fun createLanguage(): TSLanguage = TSLanguageJava.getInstance()

    override fun extractSymbols(root: TSNode, source: String): List<GlobalSymbol> {
        if (!root.canAccess()) return emptyList()

        val globals = LinkedHashSet<GlobalSymbol>()
        val stack = ArrayDeque<TSNode>()
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (!node.canAccess()) continue

            when (node.type) {
                "class_declaration",
                "record_declaration",
                -> extractSingleNameSymbol(
                    node = node,
                    source = source,
                    kind = SymbolKind.Class,
                    detail = "class",
                )?.let(globals::add)

                "interface_declaration" -> extractSingleNameSymbol(
                    node = node,
                    source = source,
                    kind = SymbolKind.Interface,
                    detail = "interface",
                )?.let(globals::add)

                "enum_declaration" -> extractSingleNameSymbol(
                    node = node,
                    source = source,
                    kind = SymbolKind.Enum,
                    detail = "enum",
                )?.let(globals::add)

                "method_declaration",
                "constructor_declaration",
                -> {
                    val nameNode = node.getChildByFieldName("name")
                        ?: findFirstNamedChildOfType(node, "identifier")
                        ?: continue
                    val name = nodeText(nameNode, source)?.trim().orEmpty()
                    if (name.isEmpty()) continue
                    globals.add(
                        GlobalSymbol(
                            name = name,
                            kind = SymbolKind.Method,
                            detail = "method",
                            location = locationOf(nameNode),
                            signature = nodeText(node, source)?.trim(),
                        )
                    )
                }

                "field_declaration" -> {
                    val typeText = node.getChildByFieldName("type")
                        ?.let { nodeText(it, source)?.trim() }
                        .orEmpty()
                    for (fieldNameNode in findVariableDeclaratorNames(node)) {
                        val name = nodeText(fieldNameNode, source)?.trim().orEmpty()
                        if (name.isEmpty()) continue
                        globals.add(
                            GlobalSymbol(
                                name = name,
                                kind = SymbolKind.Field,
                                detail = if (typeText.isEmpty()) "field" else "field: $typeText",
                                location = locationOf(fieldNameNode),
                            )
                        )
                    }
                }
            }

            val childCount = node.namedChildCount
            for (i in childCount - 1 downTo 0) {
                val child = node.getNamedChild(i)
                if (child.canAccess()) stack.addLast(child)
            }
        }

        return globals
            .asSequence()
            .distinctBy { it.kind to it.name }
            .sortedWith(compareBy<GlobalSymbol>({ it.name }, { it.kind.ordinal }))
            .toList()
    }

    private fun extractSingleNameSymbol(
        node: TSNode,
        source: String,
        kind: SymbolKind,
        detail: String,
    ): GlobalSymbol? {
        val nameNode = node.getChildByFieldName("name")
            ?: findFirstNamedChildOfType(node, "identifier")
            ?: return null
        val name = nodeText(nameNode, source)?.trim().orEmpty()
        if (name.isEmpty()) return null
        return GlobalSymbol(
            name = name,
            kind = kind,
            detail = detail,
            location = locationOf(nameNode),
        )
    }

    private fun findVariableDeclaratorNames(fieldDecl: TSNode): List<TSNode> {
        if (!fieldDecl.canAccess()) return emptyList()
        val out = mutableListOf<TSNode>()
        val stack = ArrayDeque<TSNode>()
        stack.addLast(fieldDecl)

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (!node.canAccess()) continue

            if (node.type == "variable_declarator") {
                val nameNode = node.getChildByFieldName("name")
                    ?: findFirstNamedChildOfType(node, "identifier")
                if (nameNode != null && nameNode.canAccess()) {
                    out.add(nameNode)
                }
            }

            val childCount = node.namedChildCount
            for (i in childCount - 1 downTo 0) {
                val child = node.getNamedChild(i)
                if (child.canAccess()) stack.addLast(child)
            }
        }

        return out
    }

    private fun findFirstNamedChildOfType(node: TSNode, type: String): TSNode? {
        if (!node.canAccess()) return null
        val stack = ArrayDeque<TSNode>()
        stack.addLast(node)

        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (!cur.canAccess()) continue
            if (cur.type == type) return cur
            val childCount = cur.namedChildCount
            for (i in childCount - 1 downTo 0) {
                val child = cur.getNamedChild(i)
                if (child.canAccess()) stack.addLast(child)
            }
        }
        return null
    }

    private fun locationOf(node: TSNode): SymbolLocation {
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
}
