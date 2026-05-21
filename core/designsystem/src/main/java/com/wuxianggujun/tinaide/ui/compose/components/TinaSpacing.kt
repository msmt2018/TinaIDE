package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.ui.unit.dp

/**
 * TinaIDE 统一间距常量
 *
 * 基于 Material Design 8dp 网格系统设计
 *
 * 使用规范：
 * - xxs (2dp): 极小间距，用于紧凑元素内部
 * - xs (4dp): 小间距，用于相关元素之间
 * - sm (6dp): 较小间距，用于图标与文字之间
 * - md (8dp): 中等间距，最常用的基础间距
 * - lg (12dp): 较大间距，用于分组内元素
 * - xl (16dp): 大间距，用于卡片内边距、分组之间
 * - xxl (20dp): 较大间距，用于区域分隔
 * - xxxl (24dp): 超大间距，用于页面边距
 * - huge (32dp): 巨大间距，用于主要区域分隔
 */
object TinaSpacing {
    /** 极小间距 2dp - 用于紧凑元素内部 */
    val xxs = 2.dp

    /** 小间距 4dp - 用于相关元素之间 */
    val xs = 4.dp

    /** 较小间距 6dp - 用于图标与文字之间 */
    val sm = 6.dp

    /** 中等间距 8dp - 最常用的基础间距 */
    val md = 8.dp

    /** 较大间距 10dp - 用于中等密度布局 */
    val mdLg = 10.dp

    /** 较大间距 12dp - 用于分组内元素 */
    val lg = 12.dp

    /** 大间距 16dp - 用于卡片内边距、分组之间 */
    val xl = 16.dp

    /** 较大间距 20dp - 用于区域分隔 */
    val xxl = 20.dp

    /** 超大间距 24dp - 用于页面边距 */
    val xxxl = 24.dp

    /** 巨大间距 32dp - 用于主要区域分隔 */
    val huge = 32.dp

    // ============ 语义化间距别名 ============

    /** 图标与文字间距 */
    val iconText = sm

    /** 列表项垂直间距 */
    val listItemVertical = xxs

    /** 列表项水平内边距 */
    val listItemHorizontal = xs

    /** 卡片内边距 */
    val cardPadding = lg

    /** 卡片之间间距 */
    val cardGap = md

    /** 对话框内边距 */
    val dialogPadding = xxxl

    /** 工具栏内边距 */
    val toolbarPadding = md

    /** 状态栏内边距 */
    val statusBarPadding = lg

    /** 按钮之间间距 */
    val buttonGap = md

    /** 输入框与标签间距 */
    val inputLabelGap = xs

    /** 分组标题与内容间距 */
    val sectionGap = xl

    /** 页面水平边距 */
    val pageHorizontal = xl

    /** 页面垂直边距 */
    val pageVertical = md
}
