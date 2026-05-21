package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.plugin.PluginLogEntry
import com.wuxianggujun.tinaide.plugin.PluginLogLevel
import com.wuxianggujun.tinaide.plugin.PluginLogManager
import com.wuxianggujun.tinaide.storage.ExternalFileIntents
import com.wuxianggujun.tinaide.ui.compose.components.BadgeStatus
import com.wuxianggujun.tinaide.ui.compose.components.TinaAlertDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaConfirmDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogCard
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogContentColumn
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogTitleText
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenu
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuDangerItem
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuDivider
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuItem
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuSectionHeader
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuSectionTitle
import com.wuxianggujun.tinaide.ui.compose.components.TinaStatusBadge
import com.wuxianggujun.tinaide.ui.compose.components.TinaTextButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaTopBar
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object PluginLogScreenSupport {

    private val lspRepairEventCodes = setOf(
        "lsp.toolchain.not_ready",
        "lsp.toolchain.install_failed",
        "lsp.server.start_failed",
        "lsp.server.stderr",
    )

    fun shouldShowLspRepairAction(log: PluginLogEntry): Boolean = log.eventCode in lspRepairEventCodes

    fun resolveHighlightedLogId(
        initialLevel: PluginLogLevel?,
        selectedLevel: PluginLogLevel?,
        searchQuery: String,
        filteredLogs: List<PluginLogEntry>,
    ): Long? {
        val shouldHighlightLatestError = initialLevel == PluginLogLevel.ERROR &&
            selectedLevel == PluginLogLevel.ERROR &&
            searchQuery.isBlank()
        return if (shouldHighlightLatestError) {
            filteredLogs.lastOrNull()?.id
        } else {
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginLogScreen(
    onBack: () -> Unit,
    initialPluginId: String? = null,
    initialPluginName: String? = null,
    initialLevel: PluginLogLevel? = null,
    onRepairLspDependencies: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val logManager = remember { PluginLogManager.getInstance(context) }
    val allLogs by logManager.logsFlow.collectAsState()

    var selectedLevel by remember(initialLevel) { mutableStateOf(initialLevel) }
    var selectedPluginId by remember(initialPluginId) { mutableStateOf(initialPluginId) }
    var showMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }
    var selectedLog by remember { mutableStateOf<PluginLogEntry?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val filteredLogs = remember(allLogs, selectedLevel, selectedPluginId, searchQuery) {
        allLogs.filter { log ->
            (selectedLevel == null || log.level == selectedLevel) &&
                (selectedPluginId == null || log.pluginId == selectedPluginId) &&
                (searchQuery.isBlank() || log.message.contains(searchQuery, ignoreCase = true))
        }
    }
    val highlightedLogId = remember(initialLevel, selectedLevel, searchQuery, filteredLogs) {
        PluginLogScreenSupport.resolveHighlightedLogId(
            initialLevel = initialLevel,
            selectedLevel = selectedLevel,
            searchQuery = searchQuery,
            filteredLogs = filteredLogs,
        )
    }

    val pluginIds = remember(allLogs, initialPluginId, initialPluginName) {
        buildList {
            addAll(allLogs.map { it.pluginId to it.pluginName })
            if (!initialPluginId.isNullOrBlank() && !initialPluginName.isNullOrBlank()) {
                add(initialPluginId to initialPluginName)
            }
        }.distinctBy { it.first }
    }

    val listState = rememberLazyListState()

    // 自动滚动到底部
    LaunchedEffect(filteredLogs.size, autoScroll) {
        if (filteredLogs.isNotEmpty() && autoScroll) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    // 标题显示日志数量
    val title = if (filteredLogs.isEmpty()) {
        stringResource(Strings.plugin_log_title)
    } else {
        "${stringResource(Strings.plugin_log_title)} (${filteredLogs.size})"
    }

    Scaffold(
        topBar = {
            TinaTopBar(
                title = title,
                onNavigateBack = onBack,
                actions = {
                    // 自动滚动开关
                    IconButton(onClick = { autoScroll = !autoScroll }) {
                        Icon(
                            imageVector = if (autoScroll) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = if (autoScroll) {
                                stringResource(Strings.plugin_log_pause_scroll)
                            } else {
                                stringResource(Strings.plugin_log_resume_scroll)
                            },
                            tint = if (autoScroll) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    // 筛选按钮
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = stringResource(Strings.plugin_log_filter)
                            )
                        }

                        TinaDropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            TinaDropdownMenuSectionHeader {
                                TinaDropdownMenuSectionTitle(
                                    text = stringResource(Strings.plugin_log_filter_level)
                                )
                            }
                            TinaDropdownMenuItem(
                                text = { Text(stringResource(Strings.plugin_log_filter_all)) },
                                onClick = {
                                    selectedLevel = null
                                    showFilterMenu = false
                                }
                            )
                            PluginLogLevel.entries.forEach { level ->
                                TinaDropdownMenuItem(
                                    text = { Text(level.name) },
                                    onClick = {
                                        selectedLevel = level
                                        showFilterMenu = false
                                    }
                                )
                            }

                            // 插件筛选
                            if (pluginIds.isNotEmpty()) {
                                TinaDropdownMenuDivider()
                                TinaDropdownMenuSectionHeader {
                                    TinaDropdownMenuSectionTitle(
                                        text = stringResource(Strings.plugin_log_filter_plugin)
                                    )
                                }
                                TinaDropdownMenuItem(
                                    text = { Text(stringResource(Strings.plugin_log_filter_all)) },
                                    onClick = {
                                        selectedPluginId = null
                                        showFilterMenu = false
                                    }
                                )
                                pluginIds.forEach { (id, name) ->
                                    TinaDropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            selectedPluginId = id
                                            showFilterMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 更多按钮
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(Strings.action_more)
                            )
                        }

                        TinaDropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            TinaDropdownMenuSectionHeader {
                                TinaDropdownMenuSectionTitle(
                                    text = stringResource(Strings.action_more)
                                )
                            }
                            TinaDropdownMenuItem(
                                text = { Text(stringResource(Strings.plugin_log_export)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    // 导出日志
                                    try {
                                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                        val fileName = "plugin_log_$timestamp.txt"
                                        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
                                        val file = File(exportsDir, fileName)

                                        val logText = if (filteredLogs.isEmpty()) {
                                            logManager.exportToText()
                                        } else {
                                            buildString {
                                                filteredLogs.forEach { entry ->
                                                    appendLine("${entry.getFormattedDate()} [${entry.level}] [${entry.pluginName}] ${entry.message}")
                                                    entry.stackTrace?.let {
                                                        appendLine("Stack trace:")
                                                        appendLine(it)
                                                        appendLine()
                                                    }
                                                }
                                            }
                                        }

                                        file.writeText(logText)

                                        val uri = ExternalFileIntents.getShareableUri(context, file)

                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }

                                        context.startActivity(Intent.createChooser(intent, context.getString(Strings.plugin_log_export)))
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            context.getString(Strings.plugin_log_export_failed, e.message ?: ""),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                            TinaDropdownMenuDivider()
                            TinaDropdownMenuDangerItem(
                                text = { Text(stringResource(Strings.plugin_log_clear)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    showClearConfirmDialog = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(Strings.plugin_log_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // 筛选器显示
            if (selectedLevel != null || selectedPluginId != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedLevel?.let { level ->
                        FilterChip(
                            selected = true,
                            onClick = { selectedLevel = null },
                            label = { Text(level.name) }
                        )
                    }
                    selectedPluginId?.let { id ->
                        val pluginName = pluginIds.find { it.first == id }?.second ?: id
                        FilterChip(
                            selected = true,
                            onClick = { selectedPluginId = null },
                            label = { Text(pluginName) }
                        )
                    }
                }
            }

            // 日志列表
            if (filteredLogs.isEmpty()) {
                EmptyLogView()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        LogEntryCard(
                            log = log,
                            highlighted = log.id == highlightedLogId,
                            onClick = { selectedLog = log }
                        )
                    }
                }
            }
        }
    }

    // 日志详情对话框
    selectedLog?.let { log ->
        val repairAction = if (
            onRepairLspDependencies != null &&
            PluginLogScreenSupport.shouldShowLspRepairAction(log)
        ) {
            {
                selectedLog = null
                onRepairLspDependencies(log.pluginId)
            }
        } else {
            null
        }
        LogDetailDialog(
            log = log,
            onDismiss = { selectedLog = null },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Plugin Log", log.toClipboardText())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, context.getString(Strings.plugin_log_copied), Toast.LENGTH_SHORT).show()
            },
            onRepairLspDependencies = repairAction,
        )
    }

    // 清空确认对话框
    if (showClearConfirmDialog) {
        val pluginName = selectedPluginId?.let { id ->
            pluginIds.find { it.first == id }?.second ?: id
        }
        TinaConfirmDialog(
            title = stringResource(Strings.plugin_log_clear_confirm_title),
            message = if (pluginName != null) {
                stringResource(Strings.plugin_log_clear_plugin_confirm_message, pluginName)
            } else {
                stringResource(Strings.plugin_log_clear_confirm_message)
            },
            onConfirm = {
                showClearConfirmDialog = false
                if (selectedPluginId != null) {
                    logManager.clearForPlugin(selectedPluginId!!)
                } else {
                    logManager.clearAll()
                }
            },
            onDismiss = { showClearConfirmDialog = false }
        )
    }
}

@Composable
private fun EmptyLogView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(Strings.plugin_log_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(Strings.plugin_log_empty_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LogEntryCard(
    log: PluginLogEntry,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val levelColor = when (log.level) {
        PluginLogLevel.DEBUG -> MaterialTheme.colorScheme.tertiary
        PluginLogLevel.INFO -> MaterialTheme.colorScheme.primary
        PluginLogLevel.WARN -> Color(0xFFFFA726) // Orange
        PluginLogLevel.ERROR -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (highlighted) 2.dp else 1.dp),
        border = if (highlighted) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 级别指示器
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(levelColor)
                    .align(Alignment.Top)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // 时间和插件名
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = log.getFormattedTime(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                        if (highlighted) {
                            TinaStatusBadge(
                                text = stringResource(Strings.plugin_log_latest_error),
                                status = BadgeStatus.ERROR,
                            )
                        }
                    }
                    Text(
                        text = log.pluginName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 日志消息
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )

                // 堆栈跟踪
                log.stackTrace?.let { stack ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stack,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LogDetailDialog(
    log: PluginLogEntry,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onRepairLspDependencies: (() -> Unit)? = null,
) {
    val scrollState = rememberScrollState()

    TinaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TinaDialogTitleText(log.pluginName)
                Text(
                    text = log.getFormattedDate(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            TinaDialogContentColumn(
                modifier = Modifier.verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val levelColor = when (log.level) {
                    PluginLogLevel.DEBUG -> MaterialTheme.colorScheme.tertiary
                    PluginLogLevel.INFO -> MaterialTheme.colorScheme.primary
                    PluginLogLevel.WARN -> Color(0xFFFFA726)
                    PluginLogLevel.ERROR -> MaterialTheme.colorScheme.error
                }

                TinaDialogCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Strings.plugin_log_level),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = log.level.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = levelColor,
                            modifier = Modifier
                                .background(levelColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                TinaDialogCard {
                    Text(
                        text = stringResource(Strings.plugin_log_plugin_id),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = log.pluginId,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }

                TinaDialogCard {
                    Text(
                        text = stringResource(Strings.plugin_log_message),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = log.message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }

                log.stackTrace?.let { stack ->
                    TinaDialogCard(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.36f)
                    ) {
                        Text(
                            text = stringResource(Strings.plugin_log_stack_trace),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stack,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onRepairLspDependencies?.let { repair ->
                    TinaTextButton(
                        text = stringResource(Strings.plugin_log_repair_lsp_dependencies),
                        onClick = repair
                    )
                }
                TinaTextButton(
                    text = stringResource(Strings.plugin_log_copy),
                    onClick = onCopy
                )
            }
        },
        dismissButton = {
            TinaTextButton(
                text = stringResource(Strings.btn_confirm),
                onClick = onDismiss
            )
        }
    )
}
