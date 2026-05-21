package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuxianggujun.tinaide.core.git.GitBranch
import com.wuxianggujun.tinaide.core.git.GitCommit
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.ui.compose.icons.rememberTinaPainter

/**
 * 底部面板 Git 日志视图（显示分支和提交历史）
 */
@Composable
fun GitLogPanel(
    currentBranch: String?,
    branches: List<GitBranch>,
    commits: List<GitCommit>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onBranchSelect: (String) -> Unit,
    onCommitClick: (GitCommit) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBranchMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        GitLogToolbar(
            currentBranch = currentBranch,
            branches = branches,
            showBranchMenu = showBranchMenu,
            onShowBranchMenu = { showBranchMenu = true },
            onDismissBranchMenu = { showBranchMenu = false },
            onBranchSelect = { branch ->
                showBranchMenu = false
                onBranchSelect(branch)
            },
            isLoading = isLoading,
            onRefresh = onRefresh
        )

        // 提交历史列表
        if (commits.isEmpty() && !isLoading) {
            EmptyCommitHistory()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(commits, key = { it.hash }) { commit ->
                    CommitItem(
                        commit = commit,
                        onClick = { onCommitClick(commit) }
                    )
                }

                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitLogToolbar(
    currentBranch: String?,
    branches: List<GitBranch>,
    showBranchMenu: Boolean,
    onShowBranchMenu: () -> Unit,
    onDismissBranchMenu: () -> Unit,
    onBranchSelect: (String) -> Unit,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    TinaOverlayPanelSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.small,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                TinaPanelSegmentButton(
                    onClick = onShowBranchMenu,
                    modifier = Modifier.height(28.dp),
                    minHeight = 28.dp,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentBranch ?: stringResource(Strings.git_no_branch),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                TinaDropdownMenu(
                    expanded = showBranchMenu,
                    onDismissRequest = onDismissBranchMenu
                ) {
                    val localBranches = branches.filter { !it.isRemote }
                    val remoteBranches = branches.filter { it.isRemote }

                    if (localBranches.isNotEmpty()) {
                        TinaDropdownMenuSectionHeader {
                            TinaDropdownMenuSectionTitle(
                                text = stringResource(Strings.git_local_branches),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        localBranches.forEach { branch ->
                            TinaDropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (branch.isCurrent) {
                                            Icon(
                                                painter = rememberTinaPainter(com.wuxianggujun.tinaide.R.drawable.ic_checkmark_small),
                                                contentDescription = stringResource(Strings.git_current_branch),
                                                modifier = Modifier.size(16.dp).padding(end = 4.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(branch.name)
                                    }
                                },
                                onClick = { onBranchSelect(branch.name) }
                            )
                        }
                    }

                    if (remoteBranches.isNotEmpty()) {
                        TinaDropdownMenuDivider()
                        TinaDropdownMenuSectionHeader {
                            TinaDropdownMenuSectionTitle(
                                text = stringResource(Strings.git_remote_branches),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        remoteBranches.forEach { branch ->
                            TinaDropdownMenuItem(
                                text = { Text(branch.name) },
                                onClick = { onBranchSelect(branch.name) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            TinaPanelSegmentButton(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier.size(32.dp),
                minHeight = 32.dp,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
                contentPadding = PaddingValues(0.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(Strings.menu_refresh),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CommitItem(
    commit: GitCommit,
    onClick: () -> Unit
) {
    TinaDialogSelectableCard(
        selected = false,
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        unselectedColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f)
    ) {
        Text(
            text = commit.message,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        // 作者和时间
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 短 hash
            Text(
                text = commit.shortHash,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 作者
            Text(
                text = commit.author,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // 时间
            Text(
                text = formatCommitDate(commit.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun EmptyCommitHistory() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TinaOverlayPanelSurface(
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Text(
                text = stringResource(Strings.git_no_commits),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

/**
 * 格式化提交日期（简化显示）
 */
private fun formatCommitDate(date: String): String {
    // 输入格式: 2025-12-22 10:30:00 +0800
    // 输出格式: 12-22 10:30
    return try {
        val parts = date.split(" ")
        if (parts.size >= 2) {
            val datePart = parts[0].substring(5) // 去掉年份
            val timePart = parts[1].substring(0, 5) // 只保留时:分
            "$datePart $timePart"
        } else {
            date.take(16)
        }
    } catch (e: Exception) {
        date.take(16)
    }
}
