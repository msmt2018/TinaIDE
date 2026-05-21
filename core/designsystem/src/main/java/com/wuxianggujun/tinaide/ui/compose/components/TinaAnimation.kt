package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Easing

/**
 * TinaIDE 统一动画常量
 *
 * 设计规范：
 * - Fast (150ms): 快速动画，用于状态切换、微交互
 * - Normal (300ms): 标准动画，用于页面切换、组件展开/收起
 * - Slow (500ms): 慢速动画，用于强调效果、引导动画
 *
 * 使用示例：
 * ```kotlin
 * // 淡入淡出
 * AnimatedVisibility(
 *     visible = isVisible,
 *     enter = fadeIn(animationSpec = TinaAnimation.normalTween()),
 *     exit = fadeOut(animationSpec = TinaAnimation.normalTween())
 * )
 *
 * // 数值动画
 * val animatedValue by animateFloatAsState(
 *     targetValue = targetValue,
 *     animationSpec = TinaAnimation.normalTween(),
 *     label = "valueAnimation"
 * )
 * ```
 */
object TinaAnimation {
    // ============ 动画时长常量 ============

    /** 快速动画时长 150ms - 用于状态切换、微交互 */
    const val Fast = 150

    /** 标准动画时长 300ms - 用于页面切换、组件展开/收起 */
    const val Normal = 300

    /** 慢速动画时长 500ms - 用于强调效果、引导动画 */
    const val Slow = 500

    // ============ 缓动函数 ============

    /** 标准缓动 - 最常用，自然流畅 */
    val StandardEasing: Easing = FastOutSlowInEasing

    /** 线性缓动 - 用于循环动画 */
    val LinearEasing: Easing = androidx.compose.animation.core.LinearEasing

    /** 进入缓动 - 用于进入动画 */
    val EnterEasing: Easing = LinearOutSlowInEasing

    /** 退出缓动 - 用于退出动画 */
    val ExitEasing: Easing = androidx.compose.animation.core.FastOutLinearInEasing

    // ============ 预设 AnimationSpec ============

    /**
     * 快速动画 tween
     * @param easing 缓动函数，默认使用标准缓动
     */
    fun <T> fastTween(easing: Easing = StandardEasing) = tween<T>(
        durationMillis = Fast,
        easing = easing
    )

    /**
     * 标准动画 tween
     * @param easing 缓动函数，默认使用标准缓动
     */
    fun <T> normalTween(easing: Easing = StandardEasing) = tween<T>(
        durationMillis = Normal,
        easing = easing
    )

    /**
     * 慢速动画 tween
     * @param easing 缓动函数，默认使用标准缓动
     */
    fun <T> slowTween(easing: Easing = StandardEasing) = tween<T>(
        durationMillis = Slow,
        easing = easing
    )

    /**
     * 自定义时长动画 tween
     * @param durationMillis 动画时长
     * @param delayMillis 延迟时长
     * @param easing 缓动函数
     */
    fun <T> customTween(
        durationMillis: Int,
        delayMillis: Int = 0,
        easing: Easing = StandardEasing
    ) = tween<T>(
        durationMillis = durationMillis,
        delayMillis = delayMillis,
        easing = easing
    )
}
