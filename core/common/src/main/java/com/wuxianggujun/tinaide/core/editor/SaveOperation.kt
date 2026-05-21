package com.wuxianggujun.tinaide.core.editor

/**
 * 保存原因
 *
 * 架构说明：
 * - 枚举定义在 core:common 层
 * - feature:editor 层的 SaveReason 需要映射到此类型
 */
enum class SaveReason {
    /** 手动保存（用户主动触发） */
    MANUAL,

    /** 自动保存（定时触发） */
    AUTO,

    /** 关闭文件时保存 */
    CLOSE
}

/**
 * 保存结果
 *
 * 架构说明：
 * - 密封类定义在 core:common 层
 * - feature:editor 层的 SaveResult 需要映射到此类型
 */
sealed class SaveResult {
    /**
     * 保存成功
     *
     * @param timestamp 保存时间戳
     * @param reason 保存原因
     */
    data class Success(val timestamp: Long, val reason: SaveReason) : SaveResult()

    /**
     * 保存失败
     *
     * @param message 错误消息
     */
    data class Failure(val message: String) : SaveResult()

    /**
     * 无操作（文件未修改，无需保存）
     */
    data object NoOp : SaveResult()
}
