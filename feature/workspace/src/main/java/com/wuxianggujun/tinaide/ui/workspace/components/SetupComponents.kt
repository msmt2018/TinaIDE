package com.wuxianggujun.tinaide.ui.workspace.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Drawables
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogSelectableCard
import com.wuxianggujun.tinaide.ui.compose.components.TinaOverlayPanelSurface
import com.wuxianggujun.tinaide.ui.compose.components.TinaPanelSegmentButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaShapes
import com.wuxianggujun.tinaide.ui.compose.components.TinaPrimaryButtonLarge
import com.wuxianggujun.tinaide.core.i18n.Strings

/**
 * 安装向导共享 UI 组件
 *
 * 提供 MD3 风格的现代化卡片布局组件
 */

/**
 * 统一的顶部导航栏高度常量
 */
object SetupTopBarDefaults {
    val Height = 56.dp
    val IconSize = 40.dp
    val HorizontalPadding = 4.dp
    val VerticalPadding = 8.dp
}

@Composable
fun SetupActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    TinaPanelSegmentButton(
        onClick = onClick,
        modifier = modifier,
        minHeight = SetupTopBarDefaults.IconSize,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = contentPadding,
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * 统一的安装向导顶部导航栏
 *
 * @param onBack 返回按钮点击回调
 * @param title 标题（可选，居中显示）
 * @param backgroundColor 背景颜色
 * @param contentColor 内容颜色（图标和文字）
 * @param actions 右侧操作按钮（可选）
 */
@Composable
fun SetupTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        // 状态栏占位
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
        )
        
        // 顶部导航栏内容
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SetupTopBarDefaults.Height)
                .padding(
                    horizontal = SetupTopBarDefaults.HorizontalPadding,
                    vertical = SetupTopBarDefaults.VerticalPadding
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回按钮
            SetupActionButton(
                onClick = onBack,
                modifier = Modifier.size(SetupTopBarDefaults.IconSize)
            ) {
                Icon(
                    painter = rememberWorkspacePainter(Drawables.ic_arrow_back),
                    contentDescription = stringResource(Strings.content_desc_back),
                    tint = contentColor
                )
            }
            
            // 标题（居中）
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            
            // 右侧操作按钮
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
            
            // 如果没有右侧操作按钮，添加占位以保持标题居中
            if (title != null) {
                Spacer(modifier = Modifier.size(SetupTopBarDefaults.IconSize))
            }
        }
    }
}

/**
 * 深色背景的统一顶部导航栏（用于安装进度页面）
 *
 * 注意：现在使用主题颜色而非硬编码颜色，以保持与应用主题的一致性
 */
@Composable
fun SetupTopBarDark(
    onBack: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    SetupTopBar(
        onBack = onBack,
        title = title,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
        actions = actions
    )
}

/**
 * 安装向导页面容器
 */
@Composable
fun SetupPageContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp),
            content = content
        )
    }
}

/**
 * 带顶部导航栏的安装向导页面容器
 */
@Composable
fun SetupPageContainerWithTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 统一的顶部导航栏
            SetupTopBar(
                onBack = onBack,
                title = title,
                actions = actions
            )
            
            // 内容区域
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                content = content
            )
        }
    }
}

/**
 * 安装向导标题区域
 */
@Composable
fun SetupHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 选项卡片 - 用于工作模式和 Linux 系统选择
 *
 * @param title 标题
 * @param subtitle 副标题（可选）
 * @param description 描述文字
 * @param iconRes 图标资源 ID
 * @param isSelected 是否选中
 * @param isRecommended 是否推荐
 * @param isEnabled 是否可用
 * @param badge 徽章文字（如"即将推出"）
 * @param features 特性列表
 * @param warnings 警告列表
 * @param onClick 点击回调
 */
@Composable
fun SelectionCard(
    title: String,
    @DrawableRes iconRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    description: String? = null,
    isRecommended: Boolean = false,
    isEnabled: Boolean = true,
    badge: String? = null,
    features: List<FeatureItem> = emptyList(),
    warnings: List<String> = emptyList()
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            !isEnabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            isSelected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "borderColor"
    )
    
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        label = "borderWidth"
    )
    
    val containerColor by animateColorAsState(
        targetValue = when {
            !isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surface
        },
        label = "containerColor"
    )

    TinaOverlayPanelSurface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isEnabled) {
                    Modifier.selectable(
                        selected = isSelected,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(TinaShapes.CardCorner),
        containerColor = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 选中指示器 - 放在右上角
            if (isSelected && isEnabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = rememberWorkspacePainter(Drawables.ic_check_circle),
                            contentDescription = stringResource(Strings.content_desc_selected),
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified
                        )
                    }
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 顶部：图标 + 标题 + 徽章
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 图标容器
                    IconContainer(
                        iconRes = iconRes,
                        isSelected = isSelected,
                        isEnabled = isEnabled
                    )
                    
                    // 标题和徽章
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            }
                        )
                        
                        // 推荐徽章
                        if (isRecommended) {
                            RecommendedBadge()
                        }
                        
                        // 自定义徽章（如"即将推出"）
                        badge?.let {
                            CustomBadge(text = it)
                        }
                    }
                }
                
                // 副标题（描述）
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isEnabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.padding(start = 56.dp) // 与图标对齐
                    )
                }
                
                // 特性列表
                if (features.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 56.dp), // 与图标对齐
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        features.forEach { feature ->
                            FeatureRow(
                                feature = feature,
                                isEnabled = isEnabled
                            )
                        }
                    }
                }
                
                // 警告列表
                if (warnings.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 56.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        warnings.forEach { warning ->
                            WarningRow(
                                text = warning,
                                isEnabled = isEnabled
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 图标容器
 */
@Composable
private fun IconContainer(
    @DrawableRes iconRes: Int,
    isSelected: Boolean,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    !isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = rememberWorkspacePainter(iconRes),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = Color.Unspecified // 使用图标原始颜色
        )
    }
}

/**
 * 推荐徽章
 */
@Composable
fun RecommendedBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(TinaShapes.ExtraSmallCorner),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = stringResource(Strings.badge_recommended),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * 自定义徽章
 */
@Composable
fun CustomBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onTertiaryContainer
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(TinaShapes.ExtraSmallCorner),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * 特性项数据类
 */
data class FeatureItem(
    val text: String,
    val type: FeatureType = FeatureType.POSITIVE
)

enum class FeatureType {
    POSITIVE,   // 正面特性（绿色勾选）
    NEUTRAL,    // 中性特性（灰色）
    NEGATIVE    // 负面特性（橙色警告）
}

/**
 * 特性行
 */
@Composable
private fun FeatureRow(
    feature: FeatureItem,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 使用绿色圆点图标来匹配设计图
        Icon(
            painter = rememberWorkspacePainter(
                when (feature.type) {
                    FeatureType.POSITIVE -> Drawables.ic_dot_green
                    FeatureType.NEGATIVE -> Drawables.ic_warning_amber
                    FeatureType.NEUTRAL -> Drawables.ic_info_outline
                }
            ),
            contentDescription = null,
            modifier = Modifier.size(if (feature.type == FeatureType.POSITIVE) 8.dp else 16.dp),
            tint = when {
                !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                feature.type == FeatureType.POSITIVE -> Color.Unspecified // 使用图标原始颜色
                feature.type == FeatureType.NEGATIVE -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        
        Text(
            text = feature.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isEnabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }
        )
    }
}

/**
 * 警告行
 */
@Composable
private fun WarningRow(
    text: String,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val warningColor = MaterialTheme.colorScheme.error
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = rememberWorkspacePainter(Drawables.ic_warning_amber),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (isEnabled) {
                warningColor
            } else {
                warningColor.copy(alpha = 0.5f)
            }
        )

        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isEnabled) {
                warningColor
            } else {
                warningColor.copy(alpha = 0.5f)
            }
        )
    }
}

/**
 * 底部操作按钮
 */
@Composable
fun SetupBottomButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showArrow: Boolean = true
) {
    TinaPrimaryButtonLarge(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = if (showArrow) rememberWorkspacePainter(Drawables.ic_arrow_forward) else null
    )
}

/**
 * 底部提示文字
 */
@Composable
fun SetupFooterHint(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * 返回按钮
 */
@Composable
fun SetupBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SetupActionButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .offset(x = (-8).dp) // 向左偏移以抵消容器的 padding，使箭头与屏幕边缘对齐
    ) {
        Icon(
            painter = rememberWorkspacePainter(Drawables.ic_arrow_back),
            contentDescription = stringResource(Strings.content_desc_back),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 信息卡片 - 用于显示说明信息
 */
@Composable
fun InfoCard(
    title: String,
    content: String,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int = Drawables.ic_info_outline
) {
    TinaOverlayPanelSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TinaShapes.ButtonCorner),
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = rememberWorkspacePainter(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 可点击的选择卡片 - 用于工作空间选择
 */
@Composable
fun ClickableSelectionCard(
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    selectedText: String? = null
) {
    TinaDialogSelectableCard(
        selected = isSelected,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TinaShapes.ButtonCorner),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        selectedColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        unselectedColor = MaterialTheme.colorScheme.surface,
        selectedBorder = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        unselectedBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = rememberWorkspacePainter(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color.Unspecified
                )
            }
            
            // 文字
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isSelected && selectedText != null) selectedText else title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            // 箭头
            Icon(
                painter = rememberWorkspacePainter(Drawables.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 提示行
 */
@Composable
fun HintRow(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int = Drawables.ic_lightbulb
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = rememberWorkspacePainter(iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )

        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
