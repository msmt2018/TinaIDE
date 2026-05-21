package com.wuxianggujun.tinaide.editor.symbol

import com.itsaky.androidide.treesitter.TSLanguage
import com.itsaky.androidide.treesitter.TSNode

/**
 * 语言级符号提取策略。
 *
 * 每种语言实现一个，注册到 [ProjectSymbolIndexService]。
 * 框架负责文件收集、解析调度、缓存、查询；
 * Provider 只负责声明"我处理哪些扩展名"以及"如何从 AST 提取符号"。
 */
interface LanguageSymbolProvider {

    /** 该语言支持的文件扩展名（小写，不含点号），例如 `setOf("java")` */
    val supportedExtensions: Set<String>

    /** 创建 Tree-sitter 语言实例，用于初始化 TSParser */
    fun createLanguage(): TSLanguage

    /**
     * 从 AST 根节点提取全局符号。
     *
     * 注意：此方法必须在 TSTree 关闭之前完成执行。
     *
     * @param root AST 根节点
     * @param source 源文件完整文本
     * @return 提取到的全局符号列表
     */
    fun extractSymbols(root: TSNode, source: String): List<GlobalSymbol>
}
