package com.wuxianggujun.tinaide.core.compile

import android.content.Context
import com.wuxianggujun.tinaide.core.security.PathValidator

/**
 * 编译器路径验证辅助类
 *
 * **用途**:
 * - 在编译前验证源文件和输出路径
 * - 集成 PathValidator 到编译流程
 * - 提供编译器特定的路径检查
 *
 * **使用示例**:
 * ```kotlin
 * val validator = CompilerPathValidator(context)
 * 
 * // 验证编译输入输出路径
 * validator.validateCompilePaths(
 *     sourcePath = "/workspace/main.cpp",
 *     outputPath = "/workspace/main.o"
 * )
 * ```
 */
class CompilerPathValidator(context: Context) {
    
    private val pathValidator = PathValidator(context)
    
    /**
     * 验证编译路径（源文件和输出文件）
     *
     * **检查内容**:
     * - 源文件路径是否在白名单内
     * - 输出文件路径是否在白名单内
     * - 路径是否包含危险的遍历字符
     *
     * @param sourcePath 源文件路径（PRoot guest 路径）
     * @param outputPath 输出文件路径（PRoot guest 路径）
     * @throws com.wuxianggujun.tinaide.core.exception.TinaIDEException.PathValidationException 如果路径不合法
     */
    fun validateCompilePaths(sourcePath: String, outputPath: String) {
        pathValidator.validateGuestPath(sourcePath)
        pathValidator.validateGuestPath(outputPath)
    }
    
    /**
     * 验证链接路径（目标文件列表和输出文件）
     *
     * @param objectPaths 目标文件路径列表
     * @param outputPath 输出文件路径
     * @throws com.wuxianggujun.tinaide.core.exception.TinaIDEException.PathValidationException 如果路径不合法
     */
    fun validateLinkPaths(objectPaths: List<String>, outputPath: String) {
        objectPaths.forEach { path ->
            pathValidator.validateGuestPath(path)
        }
        pathValidator.validateGuestPath(outputPath)
    }
    
    /**
     * 验证项目路径（Android host 路径）
     *
     * @param projectPath 项目根目录路径
     * @throws com.wuxianggujun.tinaide.core.exception.TinaIDEException.PathValidationException 如果路径不合法
     */
    fun validateProjectPath(projectPath: String) {
        pathValidator.validateHostPath(projectPath)
    }
    
    /**
     * 检查路径是否安全（不抛出异常）
     *
     * @param guestPath PRoot guest 路径
     * @return true 表示路径安全
     */
    fun isPathSafe(guestPath: String): Boolean {
        return pathValidator.isGuestPathAllowed(guestPath)
    }
}