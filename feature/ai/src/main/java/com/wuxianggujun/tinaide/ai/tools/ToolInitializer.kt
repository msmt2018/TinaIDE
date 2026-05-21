package com.wuxianggujun.tinaide.ai.tools

import com.wuxianggujun.tinaide.ai.tools.code.FindReferencesTool
import com.wuxianggujun.tinaide.ai.tools.code.FindSymbolTool
import com.wuxianggujun.tinaide.ai.tools.code.GetCodeOutlineTool
import com.wuxianggujun.tinaide.ai.tools.code.SearchCodeTool
import com.wuxianggujun.tinaide.ai.tools.diagnostics.ClearDiagnosticsTool
import com.wuxianggujun.tinaide.ai.tools.diagnostics.GetAllDiagnosticsTool
import com.wuxianggujun.tinaide.ai.tools.diagnostics.GetDiagnosticsTool
import com.wuxianggujun.tinaide.ai.tools.editor.GetCurrentFileTool
import com.wuxianggujun.tinaide.ai.tools.editor.GetSelectedCodeTool
import com.wuxianggujun.tinaide.ai.tools.editor.InsertCodeTool
import com.wuxianggujun.tinaide.ai.tools.editor.ReplaceSelectedCodeTool
import com.wuxianggujun.tinaide.ai.tools.execution.BuildProjectTool
import com.wuxianggujun.tinaide.ai.tools.execution.GetBuildErrorsTool
import com.wuxianggujun.tinaide.ai.tools.execution.GetExecutionOutputTool
import com.wuxianggujun.tinaide.ai.tools.execution.GetExecutionStatusTool
import com.wuxianggujun.tinaide.ai.tools.execution.NavigateToBuildLogTool
import com.wuxianggujun.tinaide.ai.tools.execution.NavigateToRunOutputTool
import com.wuxianggujun.tinaide.ai.tools.execution.RunProjectTool
import com.wuxianggujun.tinaide.ai.tools.execution.RunTestsTool
import com.wuxianggujun.tinaide.ai.tools.execution.StopExecutionTool
import com.wuxianggujun.tinaide.ai.tools.filesystem.*
import com.wuxianggujun.tinaide.ai.tools.project.CountCodeLinesTool
import com.wuxianggujun.tinaide.ai.tools.project.FindFileTool
import com.wuxianggujun.tinaide.ai.tools.project.GetProjectStructureTool
import com.wuxianggujun.tinaide.ai.tools.refactor.AddDocumentationTool
import com.wuxianggujun.tinaide.ai.tools.refactor.ExtractMethodTool
import com.wuxianggujun.tinaide.ai.tools.refactor.FormatCodeTool
import com.wuxianggujun.tinaide.ai.tools.search.GitHubSearchTool
import com.wuxianggujun.tinaide.ai.tools.search.ReadGitHubFileTool
import com.wuxianggujun.tinaide.ai.tools.search.WebSearchTool

/**
 * 工具初始化器
 * 负责注册所有内置工具
 */
object ToolInitializer {

    /**
     * 注册所有内置工具
     */
    fun registerBuiltInTools() {
        // 编辑器工具
        ToolRegistry.registerAll(
            GetCurrentFileTool,
            GetSelectedCodeTool,
            InsertCodeTool,
            ReplaceSelectedCodeTool
        )

        // 文件系统工具
        ToolRegistry.registerAll(
            ReadFileTool,
            WriteFileTool,
            ListFilesTool,
            DeleteFileTool,
            CreateDirectoryTool,
            MoveFileTool,
            CopyFileTool,
            GetFileInfoTool,
            ReplaceTextTool,
            ReplaceLineTool,
            InsertLineTool,
            DeleteLinesTool
        )

        // 代码分析工具
        ToolRegistry.registerAll(
            SearchCodeTool,
            FindSymbolTool,
            FindReferencesTool,
            GetCodeOutlineTool
        )

        // 诊断工具
        ToolRegistry.registerAll(
            GetDiagnosticsTool,
            GetAllDiagnosticsTool,
            ClearDiagnosticsTool
        )

        // 执行和构建工具
        ToolRegistry.registerAll(
            RunProjectTool,
            RunTestsTool,
            BuildProjectTool,
            StopExecutionTool,
            GetExecutionStatusTool,
            GetExecutionOutputTool,
            GetBuildErrorsTool,
            NavigateToRunOutputTool,
            NavigateToBuildLogTool
        )

        // 项目管理工具
        ToolRegistry.registerAll(
            GetProjectStructureTool,
            FindFileTool,
            CountCodeLinesTool
        )

        // 重构工具
        ToolRegistry.registerAll(
            FormatCodeTool,
            ExtractMethodTool,
            AddDocumentationTool
        )

        // 网络搜索工具
        ToolRegistry.registerAll(
            GitHubSearchTool,
            ReadGitHubFileTool,
            WebSearchTool
        )
    }

    /**
     * 注册基础工具（仅编辑器相关）
     */
    fun registerBasicTools() {
        ToolRegistry.registerAll(
            GetCurrentFileTool,
            GetSelectedCodeTool,
            InsertCodeTool
        )
    }

    /**
     * 获取默认启用的工具名称列表
     */
    fun getDefaultEnabledToolNames(): List<String> = listOf(
        // 编辑器工具
        GetCurrentFileTool.name,
        GetSelectedCodeTool.name,
        InsertCodeTool.name,

        // 文件系统工具
        ReadFileTool.name,
        WriteFileTool.name,
        ListFilesTool.name,
        FindFileTool.name,

        // 代码分析工具
        SearchCodeTool.name,
        FindSymbolTool.name,
        GetCodeOutlineTool.name,
        GetProjectStructureTool.name,

        // 诊断工具
        GetDiagnosticsTool.name,
        GetAllDiagnosticsTool.name,

        // 构建工具
        BuildProjectTool.name,

        // 重构工具
        FormatCodeTool.name,

        // 网络搜索工具
        GitHubSearchTool.name,
        ReadGitHubFileTool.name,
        WebSearchTool.name
    )

    /**
     * 按分类获取工具
     */
    fun getToolsByCategory(): Map<ToolCategory, List<AiTool>> = ToolRegistry.getAllTools().groupBy { it.category }

    /**
     * 获取推荐的工具组合
     */
    fun getRecommendedToolSets(): Map<String, List<String>> = mapOf(
        "basic" to listOf(
            GetCurrentFileTool.name,
            GetSelectedCodeTool.name,
            InsertCodeTool.name,
            ReadFileTool.name,
            FindFileTool.name
        ),
        "code_analysis" to listOf(
            SearchCodeTool.name,
            FindSymbolTool.name,
            FindReferencesTool.name,
            GetCodeOutlineTool.name,
            GetProjectStructureTool.name,
            CountCodeLinesTool.name,
            GetDiagnosticsTool.name
        ),
        "development" to listOf(
            GetCurrentFileTool.name,
            ReadFileTool.name,
            WriteFileTool.name,
            SearchCodeTool.name,
            FindFileTool.name,
            GetDiagnosticsTool.name,
            BuildProjectTool.name,
            RunTestsTool.name,
            FormatCodeTool.name,
            GitHubSearchTool.name,
            ReadGitHubFileTool.name,
            WebSearchTool.name
        ),
        "refactoring" to listOf(
            GetCurrentFileTool.name,
            GetSelectedCodeTool.name,
            FindSymbolTool.name,
            FindReferencesTool.name,
            FormatCodeTool.name,
            ExtractMethodTool.name,
            AddDocumentationTool.name
        ),
        "full" to ToolRegistry.getAllTools().map { it.name }
    )

    /**
     * 获取工具统计信息
     */
    fun getToolStatistics(): ToolStatistics {
        val allTools = ToolRegistry.getAllTools()
        val enabledTools = ToolRegistry.getEnabledTools()
        val dangerousTools = allTools.filter { it.isDangerous }

        return ToolStatistics(
            totalCount = allTools.size,
            enabledCount = enabledTools.size,
            disabledCount = allTools.size - enabledTools.size,
            dangerousCount = dangerousTools.size,
            byCategory = allTools.groupBy { it.category }.mapValues { it.value.size }
        )
    }

    data class ToolStatistics(
        val totalCount: Int,
        val enabledCount: Int,
        val disabledCount: Int,
        val dangerousCount: Int,
        val byCategory: Map<ToolCategory, Int>
    )
}
