@file:Suppress("unused")

package com.wuxianggujun.tinaide.ui.compose.components

/**
 * TinaIDE 统一 UI 组件库
 *
 * 此文件作为统一导出入口，方便一次性导入所有组件。
 *
 * 使用方式：
 * ```kotlin
 * import com.wuxianggujun.tinaide.ui.compose.components.*
 * ```
 *
 * 组件分类：
 *
 * ## 设计规范常量
 * - [TinaShapes] - 圆角常量 (TinaShapes.kt)
 * - [TinaSpacing] - 间距常量 (TinaSpacing.kt)
 * - [TinaSemanticColors] - 语义化颜色 (TinaSemanticColors.kt)
 * - [TinaAnimation] - 动画常量 (TinaAnimation.kt)
 *
 * ## 顶部栏组件 (TinaTopBar.kt)
 * - [TinaTopBar] - 标准顶部栏
 * - [TinaCenterAlignedTopBar] - 居中标题顶部栏
 * - [TinaLargeTopBar] - 大标题顶部栏
 * - [TinaTopBarAction] - 顶部栏操作按钮
 *
 * ## 按钮组件 (TinaButtons.kt)
 * - [TinaPrimaryButton] - 主要操作按钮
 * - [TinaPrimaryButtonLarge] - 大号主要按钮
 * - [TinaSecondaryButton] - 次要操作按钮
 * - [TinaOutlinedButton] - 轮廓按钮
 * - [TinaOutlinedButtonLarge] - 大号轮廓按钮
 * - [TinaTextButton] - 文本按钮
 * - [TinaDangerButton] - 危险操作按钮
 * - [TinaDangerOutlinedButton] - 危险轮廓按钮
 *
 * ## 对话框组件 (TinaDialogs.kt)
 * - [TinaAlertDialog] - 基础对话框
 * - [TinaConfirmDialog] - 确认对话框
 * - [TinaInfoDialog] - 信息对话框
 * - [TinaInputDialog] - 输入对话框
 * - [TinaLoadingDialog] - 加载对话框
 * - [TinaListDialog] - 选择列表对话框
 *
 * ## 卡片组件 (TinaCards.kt)
 * - [TinaCard] - 基础卡片
 * - [TinaOutlinedCard] - 轮廓卡片
 * - [TinaElevatedCard] - 高亮卡片
 *
 * ## 输入框组件 (TinaTextFields.kt)
 * - [TinaTextField] - 基础输入框
 * - [TinaMultiLineTextField] - 多行输入框
 * - [TinaSearchField] - 搜索输入框
 *
 * ## 徽章组件 (TinaBadges.kt)
 * - [TinaRecommendedBadge] - 推荐徽章
 * - [TinaBadge] - 自定义徽章
 * - [TinaStatusBadge] - 状态徽章
 * - [BadgeStatus] - 徽章状态枚举
 *
 * ## 分隔线组件 (TinaDividers.kt)
 * - [TinaDivider] - 水平分隔线
 * - [TinaVerticalDivider] - 垂直分隔线
 *
 * ## 骨架屏组件 (TinaSkeletons.kt)
 * - [SkeletonBox] - 基础骨架屏占位块
 * - [ProjectCardSkeleton] - 项目卡片骨架屏
 * - [PluginCardSkeleton] - 插件/包卡片骨架屏
 * - [ListItemSkeleton] - 通用列表项骨架屏
 * - [SettingsItemSkeleton] - 设置项骨架屏
 *
 * ## 下拉刷新组件 (TinaPullToRefresh.kt)
 * - [TinaPullToRefreshBox] - 带触觉反馈的下拉刷新容器
 *
 * ## 状态容器组件 (TinaStateContainer.kt)
 * - [TinaStateContainer] - 通用三态容器（Loading/Error/Empty/Content）
 * - [TinaUiState] - UI 状态枚举
 *
 * ## 详情页组件 (DetailScreenComponents.kt)
 * - [DetailIconPlaceholder] - 详情页图标占位符
 * - [DetailInfoCard] - 详情页信息卡片
 * - [DetailHeaderCard] - 详情页头部卡片
 * - [DetailMetadataItem] - 详情页元数据项
 *
 * ## 列表卡片组件 (TinaListCard.kt)
 * - [TinaListCard] - 通用列表卡片
 * - [TinaListCardBadge] - 列表卡片标签
 *
 * ## 设计规范
 *
 * ### 圆角规范
 * - 按钮圆角：12dp
 * - 卡片圆角：16dp
 * - 对话框圆角：24dp
 * - 输入框圆角：12dp
 * - 小圆角：8dp
 * - 超小圆角：4dp
 *
 * ### 间距规范
 * - xxs (2dp): 极小间距
 * - xs (4dp): 小间距
 * - sm (6dp): 较小间距
 * - md (8dp): 中等间距（最常用）
 * - lg (12dp): 较大间距
 * - xl (16dp): 大间距
 * - xxl (20dp): 较大间距
 * - xxxl (24dp): 超大间距
 * - huge (32dp): 巨大间距
 *
 * ### 动画规范
 * - TinaAnimation.Fast (150ms): 快速动画
 * - TinaAnimation.Normal (300ms): 标准动画
 * - TinaAnimation.Slow (500ms): 慢速动画
 *
 * ### 语义化颜色
 * - TinaSemanticColors.Log - 日志级别颜色
 * - TinaSemanticColors.Git - Git 状态颜色
 * - TinaSemanticColors.Editor - 编辑器状态颜色
 * - TinaSemanticColors.Debug - 调试状态颜色
 * - TinaSemanticColors.Diagnostic - 诊断严重性颜色
 */

// 此文件仅作为文档和统一导入入口
// 所有组件已拆分到独立文件中