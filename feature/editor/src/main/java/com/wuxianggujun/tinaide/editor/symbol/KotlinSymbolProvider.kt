package com.wuxianggujun.tinaide.editor.symbol

import com.itsaky.androidide.treesitter.TSLanguage
import com.itsaky.androidide.treesitter.TSNode
import com.itsaky.androidide.treesitter.kotlin.TSLanguageKotlin
import java.util.ArrayDeque

/**
 * Kotlin 符号提取 Provider。
 *
 * 提取全局符号：Class / Interface / Function / Property。
 */
internal class KotlinSymbolProvider : LanguageSymbolProvider {

    override val supportedExtensions: Set<String> = setOf("kt", "kts")

    override fun createLanguage(): TSLanguage = TSLanguageKotlin.getInstance()

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
                "object_declaration",
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

                "function_declaration" -> {
                    val nameNode = node.getChildByFieldName("name")
                        ?: findFirstNamedChildOfType(node, "simple_identifier")
                        ?: findFirstNamedChildOfType(node, "identifier")
                        ?: continue
                    val name = nodeText(nameNode, source)?.trim().orEmpty()
                    if (name.isEmpty()) continue
                    globals.add(
                        GlobalSymbol(
                            name = name,
                            kind = SymbolKind.Function,
                            detail = "function",
                            location = locationOf(nameNode),
                            signature = nodeText(node, source)?.trim(),
                        )
                    )
                }

                "property_declaration" -> {
                    val typeText = node.getChildByFieldName("type")
                        ?.let { nodeText(it, source)?.trim() }
                        .orEmpty()

                    val propertyNameNodes = findDescendantsByType(
                        node = node,
                        types = setOf("variable_declaration", "simple_identifier", "identifier"),
                    )
                        .filter { it.type == "variable_declaration" || it.type == "simple_identifier" || it.type == "identifier" }

                    val preferredNames = mutableListOf<TSNode>()
                    for (candidate in propertyNameNodes) {
                        when (candidate.type) {
                            "variable_declaration" -> {
                                val nameNode = candidate.getChildByFieldName("name")
                                    ?: findFirstNamedChildOfType(candidate, "simple_identifier")
                                    ?: findFirstNamedChildOfType(candidate, "identifier")
                                if (nameNode != null && nameNode.canAccess()) {
                                    preferredNames.add(nameNode)
                                }
                            }

                            "simple_identifier", "identifier" -> preferredNames.add(candidate)
                        }
                    }

                    for (nameNode in preferredNames.distinctBy { it.startByte }) {
                        val name = nodeText(nameNode, source)?.trim().orEmpty()
                        if (name.isEmpty()) continue
                        globals.add(
                            GlobalSymbol(
                                name = name,
                                kind = SymbolKind.Property,
                                detail = if (typeText.isEmpty()) "property" else "property: $typeText",
                                location = locationOf(nameNode),
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
            ?: findFirstNamedChildOfType(node, "simple_identifier")
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

    private fun findDescendantsByType(node: TSNode, types: Set<String>): List<TSNode> {
        if (!node.canAccess()) return emptyList()
        val out = mutableListOf<TSNode>()
        val stack = ArrayDeque<TSNode>()
        stack.addLast(node)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (!cur.canAccess()) continue
            if (cur.type in types) out.add(cur)
            val childCount = cur.namedChildCount
            for (i in childCount - 1 downTo 0) {
                val child = cur.getNamedChild(i)
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
