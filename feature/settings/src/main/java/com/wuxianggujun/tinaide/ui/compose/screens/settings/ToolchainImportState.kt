package com.wuxianggujun.tinaide.ui.compose.screens.settings

/**
 * 工具链导入状态
 */
sealed class ToolchainImportState {
    /**
     * 空闲状态
     */
    data object Idle : ToolchainImportState()

    /**
     * 导入中
     */
    data class Importing(
        val fileName: String,
        val fileSize: Long,
        val fileType: String,
        val targetPath: String,
        val progress: Float,
        val currentStep: String,
        val logs: List<String>
    ) : ToolchainImportState()

    /**
     * 导入成功
     */
    data class Success(
        val toolchainId: String,
        val toolchainName: String,
        val logs: List<String>
    ) : ToolchainImportState()

    /**
     * 导入失败
     */
    data class Failed(
        val error: String,
        val logs: List<String>
    ) : ToolchainImportState()
}
