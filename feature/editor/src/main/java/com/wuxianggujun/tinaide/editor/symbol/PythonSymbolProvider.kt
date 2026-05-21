package com.wuxianggujun.tinaide.editor.symbol

import com.itsaky.androidide.treesitter.TSLanguage
import com.itsaky.androidide.treesitter.TSNode
import com.itsaky.androidide.treesitter.python.TSLanguagePython
import java.util.ArrayDeque

/**
 * Python 符号提取 Provider。
 *
 * 提取全局符号：Class / Function / Variable。
 */
internal class PythonSymbolProvider : LanguageSymbolProvider {

    override val supportedExtensions: Set<String> = setOf("py", "pyw")

    override fun createLanguage(): TSLanguage = TSLanguagePython.getInstance()

    override fun extractSymbols(root: TSNode, source: String): List<GlobalSymbol> {
        if (!root.canAccess()) return emptyList()

        val globals = LinkedHashSet<GlobalSymbol>()
        val stack = ArrayDeque<Pair<TSNode, Int>>() // Int: 嵌套深度（类/函数内部深度）
        stack.addLast(root to 0)

        while (stack.isNotEmpty()) {
            val (node, scopeDepth) = stack.removeLast()
            if (!node.canAccess()) continue

            when (node.type) {
                "class_definition" -> extractSingleNameSymbol(
                    node = node,
                    source = source,
                    kind = SymbolKind.Class,
                    detail = "class",
                )?.let(globals::add)

                "function_definition" -> extractSingleNameSymbol(
                    node = node,
                    source = source,
                    kind = SymbolKind.Function,
                    detail = "function",
                )?.let(globals::add)

                "assignment",
                "augmented_assignment",
                "annotated_assignment",
                -> {
                    if (scopeDepth == 0) {
                        for (nameNode in extractAssignmentTargets(node)) {
                            val name = nodeText(nameNode, source)?.trim().orEmpty()
                            if (name.isEmpty()) continue
                            globals.add(
                                GlobalSymbol(
                                    name = name,
                                    kind = SymbolKind.Variable,
                                    detail = "variable",
                                    location = locationOf(nameNode),
                                )
                            )
                        }
                    }
                }
            }

            val nextDepth = when (node.type) {
                "class_definition", "function_definition", "lambda" -> scopeDepth + 1
                else -> scopeDepth
            }

            val childCount = node.namedChildCount
            for (i in childCount - 1 downTo 0) {
                val child = node.getNamedChild(i)
                if (child.canAccess()) stack.addLast(child to nextDepth)
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

    private fun extractAssignmentTargets(node: TSNode): List<TSNode> {
        if (!node.canAccess()) return emptyList()

        val left = node.getChildByFieldName("left")
        if (left != null && left.canAccess()) {
            return collectIdentifierNodes(left)
        }

        val first = node.getNamedChild(0)
        return if (first.canAccess()) collectIdentifierNodes(first) else emptyList()
    }

    private fun collectIdentifierNodes(node: TSNode): List<TSNode> {
        if (!node.canAccess()) return emptyList()
        val out = mutableListOf<TSNode>()
        val stack = ArrayDeque<TSNode>()
        stack.addLast(node)

        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (!cur.canAccess()) continue
            if (cur.type == "identifier") {
                out.add(cur)
                continue
            }
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
