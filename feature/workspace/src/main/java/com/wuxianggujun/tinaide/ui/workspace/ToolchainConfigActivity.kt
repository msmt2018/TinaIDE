package com.wuxianggujun.tinaide.ui.workspace

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gyf.immersionbar.ktx.immersionBar
import com.wuxianggujun.tinaide.storage.StorageManager
import com.wuxianggujun.tinaide.storage.compose.rememberStoragePermissionRequester
import com.wuxianggujun.tinaide.core.network.server.TinaServerConfig
import com.wuxianggujun.tinaide.core.i18n.Drawables
import com.wuxianggujun.tinaide.core.i18n.strOr
import com.wuxianggujun.tinaide.core.proot.ToolchainConfig
import org.koin.compose.koinInject
import com.wuxianggujun.tinaide.ui.theme.TinaIDETheme
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogActionRow
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogContentColumn
import com.wuxianggujun.tinaide.ui.compose.components.TinaOverlayPanelSurface
import com.wuxianggujun.tinaide.ui.compose.components.TinaOutlinedButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaPanelSegmentButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaShapes
import com.wuxianggujun.tinaide.ui.compose.components.TinaPrimaryButtonLarge
import com.wuxianggujun.tinaide.ui.compose.components.StoragePermissionDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaCustomDialogHeader
import com.wuxianggujun.tinaide.ui.compose.components.TinaBackHandlers
import com.wuxianggujun.tinaide.ui.workspace.components.rememberWorkspacePainter
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.ui.compose.components.tinaBackAction
import timber.log.Timber

/**
 * 工具链配置页面
 *
 * 让用户选择需要安装的工具链组件
 */
class ToolchainConfigActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ToolchainConfigActivity"
        private const val EXTRA_IS_FIRST_LAUNCH = "is_first_launch"

        /**
         * 创建启动 Intent
         * @param context 上下文
         * @param isFirstLaunch 是否为首次启动（首次启动时不显示返回按钮）
         */
        fun createIntent(context: Context, isFirstLaunch: Boolean = true): Intent {
            return Intent(context, ToolchainConfigActivity::class.java).apply {
                putExtra(EXTRA_IS_FIRST_LAUNCH, isFirstLaunch)
            }
        }
    }

    private val isFirstLaunch: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_IS_FIRST_LAUNCH, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置沉浸式状态栏
        immersionBar {
            transparentStatusBar()
            statusBarDarkFont(true)
            navigationBarColor(android.R.color.transparent)
            navigationBarDarkIcon(true)
        }

        setContent {
            TinaIDETheme {
                val storageManager: StorageManager = koinInject()
                var showPermissionDialog by rememberSaveable { mutableStateOf(false) }

                val permissionRequester = rememberStoragePermissionRequester(storageManager) { granted ->
                    showPermissionDialog = false
                    if (granted) {
                        Timber.tag(TAG).i("Storage permission granted")
                    } else {
                        Timber.tag(TAG).w("Storage permission denied")
                    }
                }

                // 首次启动时未授权则弹出权限引导对话框
                LaunchedEffect(Unit) {
                    if (isFirstLaunch && !storageManager.hasExternalStoragePermission()) {
                        showPermissionDialog = true
                    }
                }

                ToolchainConfigScreen(
                    isFirstLaunch,
                    { config, preferredLlvmMajorVersion ->
                        // 跳转到安装页面，传递配置
                        val intent = DependencyInstallActivity.createIntent(
                            context = this,
                            config = config,
                            preferredLlvmMajorVersion = preferredLlvmMajorVersion
                        )
                        startActivity(intent)
                        finish()
                    },
                    { finish() }
                )

                // 存储权限请求对话框
                if (showPermissionDialog) {
                    StoragePermissionDialog(
                        onRequestPermission = { permissionRequester.request() },
                        onDismiss = { showPermissionDialog = false }
                    )
                }
            }
        }
    }
}

/**
 * 工具链配置屏幕
 * @param isFirstLaunch 是否为首次启动（首次启动时不显示返回按钮）
 */
@Composable
private fun ToolchainConfigScreen(
    isFirstLaunch: Boolean,
    onConfigSelected: (ToolchainConfig, Int?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showCustomConfig by remember { mutableStateOf(false) }
    var customConfig by remember { mutableStateOf(ToolchainConfig.recommended()) }
    var preferredLlvmMajorVersion by remember { mutableStateOf<Int?>(19) }

    // 获取可用存储空间
    val availableSpaceGB = remember {
        try {
            val stat = StatFs(context.filesDir.path)
            val availableBytes = stat.availableBytes
            (availableBytes / 1024 / 1024 / 1024).toFloat()
        } catch (e: Exception) {
            0f
        }
    }

    val handleBack = {
        if (showCustomConfig) {
            showCustomConfig = false
        } else if (!isFirstLaunch) {
            onBack()
        }
    }

    TinaBackHandlers(
        tinaBackAction(enabled = showCustomConfig) {
            handleBack()
        },
        tinaBackAction(enabled = !isFirstLaunch, onBack = onBack)
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 状态栏占位
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
            )

            // 顶部导航栏 - 新设计
            TinaCustomDialogHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                title = stringResource(Strings.env_deploy_title),
                trailingContent = {
                    ToolchainConfigActionButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TinaServerConfig.URL_HELP))
                            runCatching { context.startActivity(intent) }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        Strings.error_cannot_open_link.strOr(context),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .border(
                                    width = 1.5.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
            
            // 主内容区域
            AnimatedContent(
                targetState = showCustomConfig,
                label = "configMode"
            ) { isCustom ->
                if (!isCustom) {
                    // 快速模式：显示推荐配置选项
                    QuickModeContent(
                        availableSpaceGB = availableSpaceGB,
                        onRecommendedInstall = { onConfigSelected(ToolchainConfig.recommended(), preferredLlvmMajorVersion) },
                        onCustomInstall = { showCustomConfig = true }
                    )
                } else {
                    // 自定义模式：显示详细选项
                    CustomModeContent(
                        config = customConfig,
                        preferredLlvmMajorVersion = preferredLlvmMajorVersion,
                        availableSpaceGB = availableSpaceGB,
                        onConfigChange = { customConfig = it },
                        onPreferredLlvmMajorVersionChange = { preferredLlvmMajorVersion = it },
                        onConfirm = { onConfigSelected(customConfig, preferredLlvmMajorVersion) },
                        onBack = handleBack
                    )
                }
            }
        }
    }
}

/**
 * 快速模式内容：新版设计 - 推荐配置 + 自定义选项
 */
@Composable
private fun QuickModeContent(
    availableSpaceGB: Float,
    onRecommendedInstall: () -> Unit,
    onCustomInstall: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 主内容区域（可滚动）
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 大标题
            Text(
                text = stringResource(Strings.toolchain_config_main_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 描述文字
            Text(
                text = stringResource(Strings.toolchain_config_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3f
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 推荐配置卡片 - 新设计
            StandardDevKitCard(
                onClick = onRecommendedInstall
            )

            // 自定义专家模式卡片 - 新设计
            CustomExpertModeCard(
                onClick = onCustomInstall
            )
        }

        // 底部固定区域
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 开始安装按钮 - 使用主题色
            TinaPrimaryButtonLarge(
                text = stringResource(Strings.btn_start_install),
                onClick = onRecommendedInstall,
                icon = rememberWorkspacePainter(Drawables.ic_download_circle)
            )

            // 服务协议提示
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Strings.agree_by_clicking_prefix),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(Strings.service_agreement),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TinaServerConfig.URL_SERVICE_AGREEMENT))
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    Strings.error_cannot_open_link.strOr(context),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                )
                Text(
                    text = " ${stringResource(Strings.and_word)} ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(Strings.privacy_policy),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TinaServerConfig.URL_PRIVACY_POLICY))
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    Strings.error_cannot_open_link.strOr(context),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                )
            }
        }
    }
}

/**
 * 标准开发包卡片 - 新设计（使用 MD3 主题色）
 */
@Composable
private fun StandardDevKitCard(
    onClick: () -> Unit
) {
    // 使用 MaterialTheme 的主题色
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer

    ToolchainConfigSectionCard(
        onClick = onClick,
        elevation = 2.dp,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 顶部：图标 + 推荐标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // 主题色图标背景
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(TinaShapes.CardCorner))
                    .background(primaryColor),
                contentAlignment = Alignment.Center
            ) {
                // 星星闪光图标
                SparkleIcon(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            ToolchainConfigBadge(
                text = stringResource(Strings.badge_recommended_choice),
                color = primaryContainerColor,
                textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                horizontalPadding = 12.dp,
                verticalPadding = 6.dp
            )
        }

        // 标题和副标题
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(Strings.standard_dev_kit),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(Strings.standard_dev_kit_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = primaryColor
            )
        }

        // 功能列表
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FeatureCheckItem(
                text = stringResource(Strings.feature_cpp20_stdlib),
                color = primaryColor
            )
            FeatureCheckItem(
                text = stringResource(Strings.feature_hardware_accel),
                color = primaryColor
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // 底部：预计占用 + 箭头
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Strings.estimated_size_value, "1.2 GB"),
                style = MaterialTheme.typography.bodyMedium,
                color = primaryColor
            )
            Icon(
                painter = rememberWorkspacePainter(Drawables.ic_arrow_forward),
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 自定义专家模式卡片（使用 MD3 主题色）
 */
@Composable
private fun CustomExpertModeCard(
    onClick: () -> Unit
) {
    ToolchainConfigSectionCard(
        onClick = onClick,
        elevation = 1.dp,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 使用主题色变体的图标背景
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(TinaShapes.CardCorner))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    // 滑块图标
                    SlidersIcon(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(Strings.custom_expert_mode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(Strings.custom_expert_mode_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                painter = rememberWorkspacePainter(Drawables.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ToolchainConfigActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable BoxScope.() -> Unit
) {
    TinaPanelSegmentButton(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        minHeight = 36.dp,
        color = color,
        contentPadding = PaddingValues(0.dp),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun ToolchainConfigBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    textColor: Color,
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 4.dp
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = color
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding)
        )
    }
}

@Composable
private fun ToolchainConfigSectionCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    border: androidx.compose.foundation.BorderStroke? = null,
    elevation: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(TinaShapes.CardCorner)
    val cardContent: @Composable () -> Unit = {
        TinaDialogContentColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content
        )
    }

    if (onClick != null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = color),
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            onClick = onClick,
            content = { cardContent() }
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = color),
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            content = { cardContent() }
        )
    }
}

/**
 * 功能勾选项
 */
@Composable
private fun FeatureCheckItem(
    text: String,
    color: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 勾选圆圈
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(1.5.dp, color.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = rememberWorkspacePainter(Drawables.ic_check),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 星星闪光图标（自定义绘制）
 */
@Composable
private fun SparkleIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val starSize = size.minDimension * 0.4f

        // 绘制主星星
        val mainPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(centerX, centerY - starSize)
            lineTo(centerX + starSize * 0.3f, centerY - starSize * 0.3f)
            lineTo(centerX + starSize, centerY)
            lineTo(centerX + starSize * 0.3f, centerY + starSize * 0.3f)
            lineTo(centerX, centerY + starSize)
            lineTo(centerX - starSize * 0.3f, centerY + starSize * 0.3f)
            lineTo(centerX - starSize, centerY)
            lineTo(centerX - starSize * 0.3f, centerY - starSize * 0.3f)
            close()
        }
        drawPath(mainPath, color)

        // 绘制小星星（右上）
        val smallStarX = centerX + starSize * 0.8f
        val smallStarY = centerY - starSize * 0.6f
        val smallSize = starSize * 0.35f
        val smallPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(smallStarX, smallStarY - smallSize)
            lineTo(smallStarX + smallSize * 0.3f, smallStarY - smallSize * 0.3f)
            lineTo(smallStarX + smallSize, smallStarY)
            lineTo(smallStarX + smallSize * 0.3f, smallStarY + smallSize * 0.3f)
            lineTo(smallStarX, smallStarY + smallSize)
            lineTo(smallStarX - smallSize * 0.3f, smallStarY + smallSize * 0.3f)
            lineTo(smallStarX - smallSize, smallStarY)
            lineTo(smallStarX - smallSize * 0.3f, smallStarY - smallSize * 0.3f)
            close()
        }
        drawPath(smallPath, color)
    }
}

/**
 * 滑块图标（自定义绘制）
 */
@Composable
private fun SlidersIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Gray
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.width * 0.12f
        val lineStartX = size.width * 0.2f
        val lineEndX = size.width * 0.8f

        // 三条水平线
        val y1 = size.height * 0.25f
        val y2 = size.height * 0.5f
        val y3 = size.height * 0.75f

        // 滑块位置
        val slider1X = size.width * 0.35f
        val slider2X = size.width * 0.65f
        val slider3X = size.width * 0.45f

        // 绘制线条
        listOf(y1 to slider1X, y2 to slider2X, y3 to slider3X).forEach { (y, sliderX) ->
            drawLine(
                color = color.copy(alpha = 0.5f),
                start = androidx.compose.ui.geometry.Offset(lineStartX, y),
                end = androidx.compose.ui.geometry.Offset(lineEndX, y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            // 滑块圆点
            drawCircle(
                color = color,
                radius = strokeWidth * 1.2f,
                center = androidx.compose.ui.geometry.Offset(sliderX, y)
            )
        }
    }
}

/**
 * 自定义模式内容：详细的工具选择
 */
@Composable
private fun CustomModeContent(
    config: ToolchainConfig,
    preferredLlvmMajorVersion: Int?,
    availableSpaceGB: Float,
    onConfigChange: (ToolchainConfig) -> Unit,
    onPreferredLlvmMajorVersionChange: (Int?) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 主内容区域（可滚动）
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Strings.custom_config_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // 版本选择（仅影响 Clang/LLVM/Lldb 的“版本化包优先级”，失败会自动降级）
            ConfigSection(
                title = stringResource(Strings.llvm_version_selection),
                description = stringResource(Strings.llvm_version_selection_desc)
            ) {
                LlvmVersionRadioGroup(
                    selectedMajorVersion = preferredLlvmMajorVersion,
                    onSelectedMajorVersionChange = onPreferredLlvmMajorVersionChange
                )
            }
             
            // 编译器选择
            ConfigSection(
                title = stringResource(Strings.compiler_selection),
                description = stringResource(Strings.compiler_selection_desc)
            ) {
                ConfigOptionItem(
                    title = "Clang",
                    description = stringResource(Strings.toolchain_clang_desc),
                    sizeInMB = 300,
                    isChecked = config.installClang,
                    onCheckedChange = { onConfigChange(config.copy(installClang = it)) }
                )
                
                ConfigOptionItem(
                    title = "GCC",
                    description = stringResource(Strings.toolchain_gcc_desc),
                    sizeInMB = 250,
                    isChecked = config.installGcc,
                    onCheckedChange = { onConfigChange(config.copy(installGcc = it)) }
                )
            }
            
            // 链接器选择
            ConfigSection(
                title = stringResource(Strings.linker_selection),
                description = stringResource(Strings.linker_selection_desc)
            ) {
                ConfigOptionItem(
                    title = "LLD",
                    description = stringResource(Strings.toolchain_lld_desc),
                    sizeInMB = 20,
                    isChecked = config.installLld,
                    onCheckedChange = { onConfigChange(config.copy(installLld = it)) }
                )
                
                ConfigOptionItem(
                    title = "GNU ld",
                    description = stringResource(Strings.toolchain_gnu_ld_desc),
                    sizeInMB = 30,
                    isChecked = config.installGnuLd,
                    onCheckedChange = { onConfigChange(config.copy(installGnuLd = it)) }
                )
            }
            
            // 调试器选择
            ConfigSection(
                title = stringResource(Strings.debugger_selection),
                description = stringResource(Strings.debugger_selection_desc)
            ) {
                ConfigOptionItem(
                    title = "LLDB",
                    description = stringResource(Strings.debugger_llvm_desc),
                    sizeInMB = 50,
                    isChecked = config.installLldb,
                    onCheckedChange = { onConfigChange(config.copy(installLldb = it)) }
                )
                
                ConfigOptionItem(
                    title = "GDB",
                    description = stringResource(Strings.debugger_gdb_desc),
                    sizeInMB = 30,
                    isChecked = config.installGdb,
                    onCheckedChange = { onConfigChange(config.copy(installGdb = it)) }
                )
            }
            
            // 必装组件提示
            ToolchainConfigSectionCard(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = rememberWorkspacePainter(Drawables.ic_info_outline),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Column {
                        Text(
                            text = stringResource(Strings.required_components),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(Strings.required_components_list),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // 底部固定区域
        TinaOverlayPanelSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(
                topStart = TinaShapes.DialogCorner,
                topEnd = TinaShapes.DialogCorner
            ),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp
        ) {
            TinaDialogContentColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 总计大小显示
                TotalSizeDisplay(
                    totalSizeInMB = config.estimateSize(),
                    availableSpaceGB = availableSpaceGB
                )
                
                // 开始安装按钮
                TinaPrimaryButtonLarge(
                    text = stringResource(Strings.btn_start_install),
                    onClick = onConfirm,
                    enabled = config.installClang || config.installGcc
                )
                
                // 返回按钮
                TinaDialogActionRow(horizontalArrangement = Arrangement.Center) {
                    TinaOutlinedButton(
                        text = stringResource(Strings.btn_back_to_presets),
                        onClick = onBack
                    )
                }
            }
        }
    }
}

@Composable
private fun LlvmVersionRadioGroup(
    selectedMajorVersion: Int?,
    onSelectedMajorVersionChange: (Int?) -> Unit
) {
    val options: List<Pair<Int?, String>> = listOf(
        null to stringResource(Strings.llvm_version_auto_recommended),
        19 to "LLVM 19",
        18 to "LLVM 18",
        17 to "LLVM 17"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectedMajorVersionChange(value) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(
                    selected = selectedMajorVersion == value,
                    onClick = { onSelectedMajorVersionChange(value) }
                )
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * 配置区块
 */
@Composable
private fun ConfigSection(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

/**
 * 配置选项项
 */
@Composable
private fun ConfigOptionItem(
    title: String,
    description: String,
    sizeInMB: Int,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ToolchainConfigSectionCard(
        color = if (isChecked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (isChecked) {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange
                )
                
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "~${sizeInMB}MB",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * 总计大小显示
 */
@Composable
private fun TotalSizeDisplay(
    totalSizeInMB: Int,
    availableSpaceGB: Float
) {
    val totalSizeGB = totalSizeInMB / 1024f
    val isSpaceInsufficient = totalSizeGB > availableSpaceGB
    
    ToolchainConfigSectionCard(
        color = if (isSpaceInsufficient) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        },
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(Strings.total_install_size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = stringResource(Strings.toolchain_total_size, totalSizeInMB),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSpaceInsufficient) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    Text(
                        text = stringResource(Strings.storage_available_gb, availableSpaceGB),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSpaceInsufficient) {
                Icon(
                    painter = rememberWorkspacePainter(Drawables.ic_warning_amber),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 空间信息横幅
 */
@Composable
private fun SpaceInfoBanner(availableSpaceGB: Float) {
    ToolchainConfigSectionCard(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = rememberWorkspacePainter(Drawables.ic_folder),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = stringResource(Strings.available_space),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = String.format("%.1f GB", availableSpaceGB),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

