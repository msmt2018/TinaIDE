package com.wuxianggujun.tinaide.ui.compose.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.diff.*
import kotlinx.coroutines.launch

/**
 * 文件对比查看器屏幕
 * 
 * 支持两种视图模式：
 * - 统一视图（Unified）：上下排列显示差异
 * - 并排视图（Side by Side）：左右并排显示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffViewerScreen(
    fileDiff: FileDiff?,
    isLoading: Boolean = false,
    error: String? = null,
    onBack: () -> Unit,
    onNavigateToHunk: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableStateOf(DiffViewMode.UNIFIED) }
    var showStats by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            DiffViewerTopBar(
                leftLabel = fileDiff?.leftFile?.label ?: "",
                rightLabel = fileDiff?.rightFile?.label ?: "",
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                onBack = onBack
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 统计信息栏
            AnimatedVisibility(
                visible = showStats && fileDiff != null,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                fileDiff?.let { diff ->
                    DiffStatsBar(
                        stats = diff.stats,
                        hunksCount = diff.hunks.size,
                        onHunkClick = { index ->
                            scope.launch {
                                // 计算 hunk 在列表中的位置
                                var position = 0
                                for (i in 0 until index) {
                                    position += diff.hunks[i].lines.size + 1 // +1 for header
                                }
                                listState.animateScrollToItem(position)
                            }
                        },
                        onToggle = { showStats = !showStats }
                    )
                }
            }
            
            // 主内容区域
            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    error != null -> {
                        ErrorContent(
                            error = error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    fileDiff != null -> {
                        when (viewMode) {
                            DiffViewMode.UNIFIED -> UnifiedDiffView(
                                diff = fileDiff,
                                listState = listState
                            )
                            DiffViewMode.SIDE_BY_SIDE -> SideBySideDiffView(
                                diff = fileDiff,
                                listState = listState
                            )
                        }
                    }
                    else -> {
                        EmptyDiffContent(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 顶部工具栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiffViewerTopBar(
    leftLabel: String,
    rightLabel: String,
    viewMode: DiffViewMode,
    onViewModeChange: (DiffViewMode) -> Unit,
    onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(Strings.diff_viewer_title),
                    style = MaterialTheme.typography.titleMedium
                )
                if (leftLabel.isNotEmpty() && rightLabel.isNotEmpty()) {
                    Text(
                        text = "$leftLabel ↔ $rightLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Strings.btn_back)
                )
            }
        },
        actions = {
            // 视图模式切换
            IconButton(
                onClick = {
                    onViewModeChange(
                        if (viewMode == DiffViewMode.UNIFIED) 
                            DiffViewMode.SIDE_BY_SIDE 
                        else 
                            DiffViewMode.UNIFIED
                    )
                }
            ) {
                Icon(
                    imageVector = if (viewMode == DiffViewMode.UNIFIED)
                        Icons.Outlined.ViewColumn
                    else
                        Icons.Outlined.ViewAgenda,
                    contentDescription = stringResource(
                        if (viewMode == DiffViewMode.UNIFIED)
                            Strings.diff_view_side_by_side
                        else
                            Strings.diff_view_unified
                    )
                )
            }
        }
    )
}

/**
 * 统计信息栏
 */
@Composable
private fun DiffStatsBar(
    stats: DiffStats,
    hunksCount: Int,
    onHunkClick: (Int) -> Unit,
    onToggle: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 新增行数
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = DiffColors.AddedText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${stats.additions}",
                    style = MaterialTheme.typography.labelMedium,
                    color = DiffColors.AddedText
                )
            }
            
            // 删除行数
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = null,
                    tint = DiffColors.RemovedText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${stats.deletions}",
                    style = MaterialTheme.typography.labelMedium,
                    color = DiffColors.RemovedText
                )
            }
            
            // 变更块数
            Text(
                text = stringResource(Strings.diff_hunks_count, hunksCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 折叠按钮
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ExpandLess,
                    contentDescription = stringResource(Strings.btn_collapse),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 统一视图（上下排列）
 */
@Composable
private fun UnifiedDiffView(
    diff: FileDiff,
    listState: LazyListState
) {
    val horizontalScrollState = rememberScrollState()
    
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(horizontalScrollState)
    ) {
        diff.hunks.forEachIndexed { hunkIndex, hunk ->
            // Hunk 头部
            item(key = "hunk_header_$hunkIndex") {
                HunkHeader(
                    leftStart = hunk.leftStart,
                    leftCount = hunk.leftCount,
                    rightStart = hunk.rightStart,
                    rightCount = hunk.rightCount,
                    hunkIndex = hunkIndex + 1,
                    totalHunks = diff.hunks.size
                )
            }
            
            // Hunk 内容
            itemsIndexed(
                items = hunk.lines,
                key = { lineIndex, _ -> "hunk_${hunkIndex}_line_$lineIndex" }
            ) { _, line ->
                UnifiedDiffLineRow(line = line)
            }
            
            // Hunk 之间的分隔
            if (hunkIndex < diff.hunks.size - 1) {
                item(key = "hunk_separator_$hunkIndex") {
                    HunkSeparator()
                }
            }
        }
    }
}

/**
 * 并排视图（左右排列）
 */
@Composable
private fun SideBySideDiffView(
    diff: FileDiff,
    listState: LazyListState
) {
    val leftScrollState = rememberScrollState()
    val rightScrollState = rememberScrollState()
    
    // 将 diff 转换为并排格式
    val sideBySideLines = remember(diff) {
        convertToSideBySide(diff)
    }
    
    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧面板
        Column(modifier = Modifier.weight(1f)) {
            // 左侧标题
            SidePanelHeader(
                label = diff.leftFile.label,
                fileName = diff.leftFile.name,
                isLeft = true
            )
            
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(leftScrollState)
            ) {
                itemsIndexed(
                    items = sideBySideLines,
                    key = { index, _ -> "left_$index" }
                ) { _, line ->
                    SideBySideLineRow(
                        lineNumber = line.leftLineNumber,
                        content = line.leftContent,
                        type = line.leftType,
                        isLeft = true
                    )
                }
            }
        }
        
        // 分隔线
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        
        // 右侧面板
        Column(modifier = Modifier.weight(1f)) {
            // 右侧标题
            SidePanelHeader(
                label = diff.rightFile.label,
                fileName = diff.rightFile.name,
                isLeft = false
            )
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rightScrollState)
            ) {
                itemsIndexed(
                    items = sideBySideLines,
                    key = { index, _ -> "right_$index" }
                ) { _, line ->
                    SideBySideLineRow(
                        lineNumber = line.rightLineNumber,
                        content = line.rightContent,
                        type = line.rightType,
                        isLeft = false
                    )
                }
            }
        }
    }
}

/**
 * 并排视图的面板标题
 */
@Composable
private fun SidePanelHeader(
    label: String,
    fileName: String,
    isLeft: Boolean
) {
    Surface(
        color = if (isLeft) 
            DiffColors.RemovedBackground.copy(alpha = 0.3f)
        else 
            DiffColors.AddedBackground.copy(alpha = 0.3f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isLeft) Icons.Outlined.Description else Icons.Outlined.EditNote,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Hunk 头部
 */
@Composable
private fun HunkHeader(
    leftStart: Int,
    leftCount: Int,
    rightStart: Int,
    rightCount: Int,
    hunkIndex: Int,
    totalHunks: Int
) {
    Surface(
        color = DiffColors.HunkHeaderBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "@@ -$leftStart,$leftCount +$rightStart,$rightCount @@",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = DiffColors.HunkHeaderText
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(Strings.diff_hunk_index, hunkIndex, totalHunks),
                style = MaterialTheme.typography.labelSmall,
                color = DiffColors.HunkHeaderText.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Hunk 分隔符
 */
@Composable
private fun HunkSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center
    ) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(0.3f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/**
 * 统一视图的行
 */
@Composable
private fun UnifiedDiffLineRow(line: DiffLine) {
    val backgroundColor = when (line.type) {
        DiffLineType.ADDED -> DiffColors.AddedBackground
        DiffLineType.REMOVED -> DiffColors.RemovedBackground
        DiffLineType.CONTEXT -> Color.Transparent
    }
    
    val textColor = when (line.type) {
        DiffLineType.ADDED -> DiffColors.AddedText
        DiffLineType.REMOVED -> DiffColors.RemovedText
        DiffLineType.CONTEXT -> MaterialTheme.colorScheme.onSurface
    }
    
    val prefix = when (line.type) {
        DiffLineType.ADDED -> "+"
        DiffLineType.REMOVED -> "-"
        DiffLineType.CONTEXT -> " "
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧行号
        Text(
            text = line.leftLineNumber?.toString() ?: "",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(40.dp)
        )
        
        // 右侧行号
        Text(
            text = line.rightLineNumber?.toString() ?: "",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(40.dp)
        )
        
        // 前缀符号
        Text(
            text = prefix,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            color = textColor,
            modifier = Modifier.width(16.dp)
        )
        
        // 内容
        Text(
            text = line.content,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Visible
        )
    }
}

/**
 * 并排视图的行
 */
@Composable
private fun SideBySideLineRow(
    lineNumber: Int?,
    content: String?,
    type: DiffLineType?,
    isLeft: Boolean
) {
    val backgroundColor = when (type) {
        DiffLineType.ADDED -> DiffColors.AddedBackground
        DiffLineType.REMOVED -> DiffColors.RemovedBackground
        DiffLineType.CONTEXT -> Color.Transparent
        null -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
    }
    
    val textColor = when (type) {
        DiffLineType.ADDED -> DiffColors.AddedText
        DiffLineType.REMOVED -> DiffColors.RemovedText
        DiffLineType.CONTEXT -> MaterialTheme.colorScheme.onSurface
        null -> Color.Transparent
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 行号
        Text(
            text = lineNumber?.toString() ?: "",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(40.dp)
        )
        
        // 内容
        Text(
            text = content ?: "",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Visible
        )
    }
}

/**
 * 错误内容
 */
@Composable
private fun ErrorContent(
    error: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * 空内容
 */
@Composable
private fun EmptyDiffContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.CompareArrows,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Strings.diff_no_changes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Diff 颜色定义
 */
private object DiffColors {
    val AddedBackground = Color(0xFF2D4A2D).copy(alpha = 0.4f)
    val AddedText = Color(0xFF98C379)
    val RemovedBackground = Color(0xFF4A2D2D).copy(alpha = 0.4f)
    val RemovedText = Color(0xFFE06C75)
    val HunkHeaderBackground = Color(0xFF2D4A6E).copy(alpha = 0.3f)
    val HunkHeaderText = Color(0xFF6B9BD2)
}

/**
 * 并排视图的行数据
 */
private data class SideBySideLine(
    val leftLineNumber: Int?,
    val leftContent: String?,
    val leftType: DiffLineType?,
    val rightLineNumber: Int?,
    val rightContent: String?,
    val rightType: DiffLineType?
)

/**
 * 将 FileDiff 转换为并排格式
 */
private fun convertToSideBySide(diff: FileDiff): List<SideBySideLine> {
    val result = mutableListOf<SideBySideLine>()
    
    diff.hunks.forEach { hunk ->
        val leftLines = mutableListOf<DiffLine>()
        val rightLines = mutableListOf<DiffLine>()
        
        // 分离左右两侧的行
        hunk.lines.forEach { line ->
            when (line.type) {
                DiffLineType.CONTEXT -> {
                    leftLines.add(line)
                    rightLines.add(line)
                }
                DiffLineType.REMOVED -> leftLines.add(line)
                DiffLineType.ADDED -> rightLines.add(line)
            }
        }
        
        // 合并为并排格式
        val maxLines = maxOf(leftLines.size, rightLines.size)
        for (i in 0 until maxLines) {
            val left = leftLines.getOrNull(i)
            val right = rightLines.getOrNull(i)
            
            result.add(SideBySideLine(
                leftLineNumber = left?.leftLineNumber,
                leftContent = left?.content,
                leftType = left?.type,
                rightLineNumber = right?.rightLineNumber,
                rightContent = right?.content,
                rightType = right?.type
            ))
        }
    }
    
    return result
}
