package com.wuxianggujun.tinaide.editor.symbol

import com.itsaky.androidide.treesitter.TSLanguage
import com.itsaky.androidide.treesitter.TSNode
import com.itsaky.androidide.treesitter.rust.TSLanguageRust
import java.util.ArrayDeque

/**
 * Rust 符号提取 Provider。
 *
 * 提取全局符号：Struct / Enum / Trait / Function / Module / Constant / Variable。
 */
internal class RustSymbolProvider : LanguageSymbolProvider {

    override val supportedExtensions: Set<String> = setOf("rs")

    override fun createLanguage(): TSLanguage = TSLanguageRust.getInstance()

    override fun extractSymbols(root: TSNode, source: String): List<GlobalSymbol> {
        if (!root.canAccess()) return emptyList()

        val globals = LinkedHashSet<GlobalSymbol>()
        val stack = ArrayDeque<TSNode>()
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (!node.canAccess()) continue

            when (node.type) {
                "struct_item" -> extractSingleNameSymbol(
                    node = node,
                    source = source,
                    kind = SymbolKind.Struct,
                    detail = "struct",
                )?.let(globals::add)

                "enum_item" -> extractSingleNameSymbol(
                    node = node,
                    source = source,
                    kind = SymbolKind.Enum,
                    detail = "enum",
                )?.let(globals::add)

                "trait_item" -> extractSingleNameSymbol(
                    node = node,
                    source = source,
                    kind = SymbolKind.Trait,
                    detail = "trait",
                )?.let(globals::add)

                "function_item" -> extractSingleNameSymbol(
                    node = node,
                    source = source,
                    kind = SymbolKind.Function,
                    detail = "function",
                )?.let(globals::add)

                "mod_item" -> extractSingleNameSymbol(
                    node = node,
                    source = source,
                    kind = SymbolKind.Module,
                    detail = "module",
                )?.let(globals::add)

                "const_item" -> extractSingleNameSymbol(
                    node = node,
                    source = source,
                    kind = SymbolKind.Constant,
                    detail = "const",
                )?.let(globals::add)

                "static_item" -> extractSingleNameSymbol(
                    node = node,
                    source = source,
                    kind = SymbolKind.Variable,
                    detail = "static",
                )?.let(globals::add)
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
