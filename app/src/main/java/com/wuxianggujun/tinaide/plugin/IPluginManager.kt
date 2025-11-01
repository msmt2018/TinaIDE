package com.wuxianggujun.tinaide.plugin

import android.content.Context
import java.io.File

/**
 * 插件管理器接口
 * 负责管理语言插件的生命周期和注册
 */
interface IPluginManager {
    /**
     * 注册插件
     */
    fun registerPlugin(plugin: ILanguagePlugin)
    
    /**
     * 注销插件
     */
    fun unregisterPlugin(pluginId: String)
    
    /**
     * 根据语言获取插件
     */
    fun getPlugin(language: String): ILanguagePlugin?
    
    /**
     * 获取所有插件
     */
    fun getAllPlugins(): List<ILanguagePlugin>
    
    /**
     * 根据文件获取对应的插件
     */
    fun getPluginForFile(file: File): ILanguagePlugin?
    
    /**
     * 启用插件
     */
    fun enablePlugin(pluginId: String)
    
    /**
     * 禁用插件
     */
    fun disablePlugin(pluginId: String)
    
    /**
     * 检查插件是否已启用
     */
    fun isPluginEnabled(pluginId: String): Boolean
}

/**
 * 语言插件接口
 */
interface ILanguagePlugin {
    /** 插件 ID */
    val id: String
    
    /** 插件名称 */
    val name: String
    
    /** 插件版本 */
    val version: String
    
    /** 支持的文件扩展名 */
    val supportedExtensions: List<String>
    
    /**
     * 初始化插件
     */
    fun initialize(context: Context)
    
    /**
     * 销毁插件
     */
    fun dispose()
    
    /**
     * 是否支持编译
     */
    fun canCompile(): Boolean
    
    /**
     * 编译文件
     */
    fun compile(file: File, options: CompileOptions): CompileResult
    
    /**
     * 运行可执行文件
     */
    fun run(executable: File, args: List<String>): RunResult
}

/**
 * 编译选项
 */
data class CompileOptions(
    val outputPath: String,
    val optimizationLevel: Int = 0,
    val debugSymbols: Boolean = false,
    val additionalFlags: List<String> = emptyList()
)

/**
 * 编译结果
 */
data class CompileResult(
    val success: Boolean,
    val output: String,
    val errors: List<CompileError> = emptyList()
)

/**
 * 编译错误
 */
data class CompileError(
    val file: String,
    val line: Int,
    val column: Int,
    val message: String,
    val severity: ErrorSeverity
)

/**
 * 错误严重程度
 */
enum class ErrorSeverity {
    ERROR,
    WARNING,
    INFO
}

/**
 * 运行结果
 */
data class RunResult(
    val success: Boolean,
    val message: String = ""
)
