package com.wuxianggujun.tinaide.ui.compose.components.editor

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.commands.HostCommandExecutor
import com.wuxianggujun.tinaide.plugin.PluginManager
import com.wuxianggujun.tinaide.ui.compose.components.EditorTab
import com.wuxianggujun.tinaide.ui.compose.components.TabContextMenu

/**
 * 编辑器标签栏组件
 *
 * 底部指示器由本组件统一绘制：
 * - 静态：固定在选中 Tab 宽度。
 * - isLoading = true：双向扩散动画，替代原编辑器顶部的 2dp 进度条。
 */
@Composable
fun EditorTabBar(
    tabs: List<EditorTabState>,
    selectedIndex: Int,
    showMenuForIndex: Int,
    pluginManager: PluginManager,
    hostCommandExecutor: HostCommandExecutor?,
    isLoading: Boolean,
    onTabClick: (Int) -> Unit,
    onTabDoubleClick: (Int) -> Unit,
    onTabLongPress: (Int) -> Unit,
    onMenuDismiss: () -> Unit,
    onCloseCurrent: (Int) -> Unit,
    onCloseOthers: (Int) -> Unit,
    onCloseAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyState = rememberLazyListState()
    val indicatorColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val indicatorHeightPx = with(LocalDensity.current) { 2.dp.toPx() }

    // 加载态扩散动画：0 = 静态 tab 宽度，1 = 完全伸展到屏幕两端（含 overshoot）
    val transition = rememberInfiniteTransition(label = "tabIndicatorLoading")
    val expansionState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tabIndicatorExpansion"
    )

    // 选中 Tab 可能已经滚出 LazyRow 视口（visibleItemsInfo 就拿不到它），这里保证它始终可见。
    LaunchedEffect(selectedIndex, tabs.size) {
        if (selectedIndex !in 0 until tabs.size) return@LaunchedEffect
        val layoutInfo = lazyState.layoutInfo
        val fullyVisible = layoutInfo.visibleItemsInfo.any {
            it.index == selectedIndex &&
                it.offset >= 0 &&
                it.offset + it.size <= layoutInfo.viewportEndOffset
        }
        if (!fullyVisible) {
            lazyState.animateScrollToItem(selectedIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(surfaceColor)
            .clipToBounds()
            .drawWithContent {
                drawContent()
                if (tabs.isEmpty() || selectedIndex !in 0 until tabs.size) return@drawWithContent
                val selectedItem = lazyState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == selectedIndex }
                val expansion = if (isLoading) expansionState.value else 0f

                val baseLeft: Float
                val tabWidth: Float
                if (selectedItem != null) {
                    baseLeft = selectedItem.offset.toFloat()
                    tabWidth = selectedItem.size.toFloat()
                } else if (isLoading) {
                    // Fallback：layout 尚未稳定（如刚打开新 Tab），从 TabBar 中心扩散，避免丢帧。
                    baseLeft = size.width / 2f
                    tabWidth = 0f
                } else {
                    return@drawWithContent
                }

                // 两端同步扩散：reach 是向两侧各自伸出的距离
                val reach = expansion * (size.width * 0.6f)
                val left = baseLeft - reach
                val right = baseLeft + tabWidth + reach
                val y = size.height - indicatorHeightPx
                drawRect(
                    color = indicatorColor,
                    topLeft = Offset(left, y),
                    size = Size(right - left, indicatorHeightPx)
                )
            }
    ) {
        LazyRow(
            state = lazyState,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(
                count = tabs.size,
                key = { index -> tabs[index].id }
            ) { index ->
                val tab = tabs[index]
                Box {
                    EditorTab(
                        fileName = tab.displayName,
                        selected = index == selectedIndex,
                        isDirty = tab.isDirty,
                        onClick = { onTabClick(index) },
                        onDoubleClick = { onTabDoubleClick(index) },
                        onLongClick = { onTabLongPress(index) }
                    )

                    // 上下文菜单
                    TabContextMenu(
                        file = tab.file,
                        isDirty = tab.isDirty,
                        expanded = showMenuForIndex == index,
                        onDismiss = onMenuDismiss,
                        onCloseCurrent = { onCloseCurrent(index) },
                        onCloseOthers = { onCloseOthers(index) },
                        onCloseAll = onCloseAll,
                        pluginManager = pluginManager,
                        hostCommandExecutor = hostCommandExecutor,
                    )
                }
            }
        }
    }
}
