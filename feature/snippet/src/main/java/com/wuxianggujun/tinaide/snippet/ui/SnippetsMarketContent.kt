package com.wuxianggujun.tinaide.snippet.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.snippet.viewmodel.SnippetDetailState
import com.wuxianggujun.tinaide.snippet.viewmodel.SnippetMarketViewModel
import com.wuxianggujun.tinaide.ui.compose.components.PluginCardSkeleton
import com.wuxianggujun.tinaide.ui.compose.components.TinaAlertDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogCard
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogContentColumn
import com.wuxianggujun.tinaide.ui.compose.components.TinaPrimaryButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaPullToRefreshBox
import com.wuxianggujun.tinaide.ui.compose.components.TinaTextButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogTitleText
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * 代码片段市场内容（作为 MarketScreen 的一个 Tab）
 */
@Composable
fun SnippetsMarketContent(
    modifier: Modifier = Modifier,
    viewModel: SnippetMarketViewModel = koinViewModel()
) {
    val listState by viewModel.listState.collectAsState()
    val detailState by viewModel.detailState.collectAsState()
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(query) {
        delay(350)
        viewModel.loadSnippets(search = query.trim().takeIf { it.isNotEmpty() })
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 搜索框
        SnippetSearchTextField(
            query = query,
            onQueryChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // 列表
        when {
            listState.isLoading && listState.snippets.isEmpty() -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(5) { PluginCardSkeleton() }
                }
            }

            listState.error != null && listState.snippets.isEmpty() -> {
                SnippetErrorState(
                    errorMessage = listState.error!!,
                    onRetry = { viewModel.loadSnippets(search = query.trim().takeIf { it.isNotEmpty() }) }
                )
            }

            listState.snippets.isEmpty() -> {
                SnippetEmptyState(
                    title = stringResource(Strings.market_empty_snippets_title),
                    description = stringResource(Strings.market_empty_snippets_desc)
                )
            }

            else -> {
                TinaPullToRefreshBox(
                    isRefreshing = listState.isLoading,
                    onRefresh = { viewModel.loadSnippets(search = query.trim().takeIf { it.isNotEmpty() }) },
                    enableHapticFeedback = true,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(listState.snippets, key = { it.id }) { snippet ->
                            SnippetCard(
                                snippet = snippet,
                                onClick = { viewModel.loadSnippetDetail(snippet.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // 详情弹窗
    if (detailState.isLoading || detailState.snippet != null || detailState.error != null) {
        SnippetDetailDialog(
            detailState = detailState,
            onDismiss = viewModel::clearSnippetDetail,
            onCopy = {
                detailState.snippet?.let { detail ->
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                        ClipData.newPlainText(detail.title, detail.codeContent)
                    )
                    viewModel.recordSnippetCopy()
                    viewModel.clearSnippetDetail()
                }
            },
            onToggleFavorite = viewModel::toggleSnippetFavorite,
            onRate = viewModel::rateSnippet
        )
    }
}

// ── 内部组件 ──

@Composable
private fun SnippetDetailDialog(
    detailState: SnippetDetailState,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRate: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    val detail = detailState.snippet

    TinaAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (detail != null) {
                TinaPrimaryButton(
                    text = stringResource(Strings.action_copy),
                    onClick = onCopy
                )
            } else {
                TinaTextButton(
                    text = stringResource(Strings.btn_confirm),
                    onClick = onDismiss
                )
            }
        },
        dismissButton = {
            TinaTextButton(
                text = stringResource(Strings.btn_cancel),
                onClick = onDismiss
            )
        },
        title = {
            TinaDialogTitleText(detail?.title ?: stringResource(Strings.market_tab_snippets))
        },
        text = {
            when {
                detailState.isLoading -> {
                    TinaDialogContentColumn {
                        TinaDialogCard {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
                detailState.error != null -> {
                    TinaDialogContentColumn {
                        TinaDialogCard(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.36f)
                        ) {
                            Text(
                                text = detailState.error,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                detail != null -> {
                    TinaDialogContentColumn(
                        modifier = Modifier.verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TinaDialogCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(onClick = onToggleFavorite) {
                                    Text(
                                        text = if (detail.isFavorited) {
                                            stringResource(Strings.snippet_unfavorite)
                                        } else {
                                            stringResource(Strings.snippet_favorite)
                                        }
                                    )
                                }
                                RatingStars(
                                    rating = detail.myRating ?: 0,
                                    onRate = onRate
                                )
                            }
                        }

                        if (!detail.description.isNullOrBlank()) {
                            TinaDialogCard {
                                Text(
                                    text = detail.description!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        TinaDialogCard(
                            contentPadding = PaddingValues(0.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            SelectionContainer {
                                Text(
                                    text = detail.codeContent,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun SnippetSearchTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(Strings.market_search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun SnippetEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.wrapContentSize().padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(48.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SnippetErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.wrapContentSize().padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(48.dp))
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(Strings.market_error_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(
                onClick = onRetry,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(Strings.market_error_retry))
            }
        }
    }
}
