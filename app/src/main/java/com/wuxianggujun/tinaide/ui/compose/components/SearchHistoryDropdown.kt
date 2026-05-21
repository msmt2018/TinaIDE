package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.search.history.SearchHistoryEntry
import java.text.SimpleDateFormat
import java.util.*

/**
 * 搜索历史下拉组件
 */
@Composable
fun SearchHistoryDropdown(
    history: List<SearchHistoryEntry>,
    favorites: List<SearchHistoryEntry>,
    onSelectEntry: (SearchHistoryEntry) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onClearHistory: () -> Unit,
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    TinaDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.widthIn(min = 300.dp, max = 400.dp)
    ) {
        // 收藏区域
        if (favorites.isNotEmpty()) {
            TinaDropdownMenuSectionHeader {
                TinaDropdownMenuSectionTitle(
                    text = stringResource(Strings.search_history_favorites),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            favorites.forEach { entry ->
                SearchHistoryItem(
                    entry = entry,
                    onSelect = { onSelectEntry(entry) },
                    onToggleFavorite = { onToggleFavorite(entry.id) },
                    onDelete = { onDeleteEntry(entry.id) }
                )
            }
            TinaDropdownMenuDivider()
        }

        // 最近搜索区域
        val recentHistory = history.filter { !it.isFavorite }.take(10)
        if (recentHistory.isNotEmpty()) {
            TinaDropdownMenuSectionHeader(
                trailingContent = {
                    TextButton(
                        onClick = onClearHistory,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = stringResource(Strings.search_history_clear),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            ) {
                TinaDropdownMenuSectionTitle(
                    text = stringResource(Strings.search_history_recent)
                )
            }
            recentHistory.forEach { entry ->
                SearchHistoryItem(
                    entry = entry,
                    onSelect = { onSelectEntry(entry) },
                    onToggleFavorite = { onToggleFavorite(entry.id) },
                    onDelete = { onDeleteEntry(entry.id) }
                )
            }
        }

        // 空状态
        if (history.isEmpty()) {
            TinaDropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(Strings.search_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {},
                enabled = false
            )
        }
    }
}

@Composable
private fun SearchHistoryItem(
    entry: SearchHistoryEntry,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    TinaDropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.query,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!entry.replacement.isNullOrEmpty()) {
                        Text(
                            text = "→ ${entry.replacement}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // 显示搜索选项标签
                    val optionTags = buildList {
                        if (entry.options.caseSensitive) add("Aa")
                        if (entry.options.useRegex) add(".*")
                        if (entry.options.wholeWord) add("W")
                    }
                    if (optionTags.isNotEmpty()) {
                        Text(
                            text = optionTags.joinToString(" "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (entry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = stringResource(
                            if (entry.isFavorite) {
                                Strings.search_history_unfavorite
                            } else {
                                Strings.search_history_favorite
                            }
                        ),
                        modifier = Modifier.size(16.dp),
                        tint = if (entry.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(Strings.search_history_delete),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        onClick = onSelect
    )
}

/**
 * 搜索历史对话框（全屏版本，用于更多历史记录）
 */
@Composable
fun SearchHistoryDialog(
    history: List<SearchHistoryEntry>,
    onSelectEntry: (SearchHistoryEntry) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    TinaAlertDialog(
        onDismissRequest = onDismiss,
        title = { TinaDialogTitleText(stringResource(Strings.search_history_title)) },
        text = {
            TinaDialogContentColumn {
                TinaDialogCard(
                    contentPadding = PaddingValues(0.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (history.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(Strings.search_history_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            // 收藏
                            val favorites = history.filter { it.isFavorite }
                            if (favorites.isNotEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(Strings.search_history_favorites),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                items(favorites, key = { it.id }) { entry ->
                                    SearchHistoryListItem(
                                        entry = entry,
                                        onSelect = {
                                            onSelectEntry(entry)
                                            onDismiss()
                                        },
                                        onToggleFavorite = { onToggleFavorite(entry.id) },
                                        onDelete = { onDeleteEntry(entry.id) }
                                    )
                                }
                                item {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }

                            // 最近
                            val recent = history.filter { !it.isFavorite }
                            if (recent.isNotEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(Strings.search_history_recent),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                items(recent, key = { it.id }) { entry ->
                                    SearchHistoryListItem(
                                        entry = entry,
                                        onSelect = {
                                            onSelectEntry(entry)
                                            onDismiss()
                                        },
                                        onToggleFavorite = { onToggleFavorite(entry.id) },
                                        onDelete = { onDeleteEntry(entry.id) }
                                    )
                                }
                            }
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
        },
        dismissButton = {
            if (history.any { !it.isFavorite }) {
                TinaTextButton(
                    text = stringResource(Strings.search_history_clear),
                    onClick = onClearHistory
                )
            }
        }
    )
}

@Composable
private fun SearchHistoryListItem(
    entry: SearchHistoryEntry,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.query,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.replacement.isNullOrEmpty()) {
                Text(
                    text = "→ ${entry.replacement}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dateFormat.format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                val optionTags = buildList {
                    if (entry.options.caseSensitive) add("Aa")
                    if (entry.options.useRegex) add(".*")
                    if (entry.options.wholeWord) add("W")
                }
                if (optionTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = optionTags.joinToString(" "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (entry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = if (entry.isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
