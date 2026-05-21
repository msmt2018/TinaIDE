package com.wuxianggujun.tinaide.ui.compose.screens.main.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import com.wuxianggujun.tinaide.core.user.DownloadHistoryItem
import com.wuxianggujun.tinaide.core.user.DownloadItemType
import com.wuxianggujun.tinaide.ui.compose.components.TinaAlertDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogCard
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogContentColumn
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogTitleText
import com.wuxianggujun.tinaide.ui.compose.components.TinaTextButton
import java.text.SimpleDateFormat
import java.util.*
import org.koin.androidx.compose.koinViewModel

/**
 * 下载历史界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadHistoryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // 拦截返回手势
    BackHandler(enabled = true) {
        onNavigateBack()
    }

    // 自动加载更多
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= uiState.downloads.size - 3 && uiState.hasMore && !uiState.isLoading
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Strings.profile_download_history)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Strings.content_desc_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(Strings.btn_refresh)
                        )
                    }
                }
            )
        }
    ) { padding ->
        val safePadding = padding.sanitizeForScaffoldContent()
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(safePadding)
        ) {
            // 筛选器
            FilterChips(
                selectedType = uiState.filterType,
                onTypeSelected = { viewModel.setFilter(it) }
            )

            // 内容区域
            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading && uiState.downloads.isEmpty() -> {
                        LoadingContent()
                    }
                    uiState.error != null && uiState.downloads.isEmpty() -> {
                        ErrorContent(
                            message = uiState.error ?: "",
                            onRetry = { viewModel.refresh() }
                        )
                    }
                    uiState.downloads.isEmpty() -> {
                        EmptyContent()
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.downloads, key = { it.id }) { download ->
                                DownloadHistoryCard(download = download)
                            }

                            if (uiState.isLoading) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChips(
    selectedType: DownloadItemType?,
    onTypeSelected: (DownloadItemType?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedType == null,
                onClick = { onTypeSelected(null) },
                label = { Text(Strings.filter_all.str()) }
            )
        }
        item {
            FilterChip(
                selected = selectedType == DownloadItemType.PLUGIN,
                onClick = { onTypeSelected(DownloadItemType.PLUGIN) },
                label = { Text(Strings.filter_plugins.str()) }
            )
        }
        item {
            FilterChip(
                selected = selectedType == DownloadItemType.PACKAGE,
                onClick = { onTypeSelected(DownloadItemType.PACKAGE) },
                label = { Text(Strings.filter_packages.str()) }
            )
        }
        item {
            FilterChip(
                selected = selectedType == DownloadItemType.SNIPPET,
                onClick = { onTypeSelected(DownloadItemType.SNIPPET) },
                label = { Text(Strings.filter_snippets.str()) }
            )
        }
    }
}

@Composable
private fun DownloadHistoryCard(download: DownloadHistoryItem) {
    var showDetailDialog by remember { mutableStateOf(false) }

    // 根据 itemType 字符串解析图标
    val icon = when (download.itemType.lowercase()) {
        "plugin" -> Icons.Default.Extension
        "package" -> Icons.Default.ShoppingCart
        "snippet" -> Icons.Default.Code
        else -> Icons.Default.Download
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val downloadTime = remember(download.downloadedAt) {
        try {
            // 尝试解析时间戳
            val timestamp = download.downloadedAt.toLongOrNull()
            if (timestamp != null) {
                dateFormat.format(Date(timestamp))
            } else {
                download.downloadedAt
            }
        } catch (e: Exception) {
            download.downloadedAt
        }
    }

    val sizeText = download.fileSize?.let { size ->
        when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetailDialog = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.itemId,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = buildString {
                        append(downloadTime)
                        if (download.version != null) {
                            append(" • v${download.version}")
                        }
                        if (sizeText != null) {
                            append(" • $sizeText")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDetailDialog) {
        DownloadDetailDialog(
            download = download,
            downloadTime = downloadTime,
            sizeText = sizeText,
            onDismiss = { showDetailDialog = false }
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = onRetry) {
                Text(Strings.action_retry.str())
            }
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(Strings.download_history_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DownloadDetailDialog(
    download: DownloadHistoryItem,
    downloadTime: String,
    sizeText: String?,
    onDismiss: () -> Unit
) {
    TinaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            TinaDialogTitleText(stringResource(Strings.download_history_detail_title))
        },
        text = {
            TinaDialogContentColumn {
                TinaDialogCard(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DetailRow(
                            label = stringResource(Strings.download_history_detail_id),
                            value = download.itemId
                        )
                        DetailRow(
                            label = stringResource(Strings.download_history_detail_type),
                            value = download.itemType
                        )
                        download.version?.let {
                            DetailRow(
                                label = stringResource(Strings.download_history_detail_version),
                                value = it
                            )
                        }
                        DetailRow(
                            label = stringResource(Strings.download_history_detail_time),
                            value = downloadTime
                        )
                        sizeText?.let {
                            DetailRow(
                                label = stringResource(Strings.download_history_detail_size),
                                value = it
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TinaTextButton(
                text = stringResource(Strings.btn_close),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
