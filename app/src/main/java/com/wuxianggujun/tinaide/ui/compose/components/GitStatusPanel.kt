package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.git.FileStatus
import com.wuxianggujun.tinaide.core.git.GitFileStatus
import com.wuxianggujun.tinaide.core.git.GitStatus
import com.wuxianggujun.tinaide.core.i18n.Strings

/**
 * Git 状态面板
 */
@Composable
fun GitStatusPanel(
    status: GitStatus,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onStageFile: (String) -> Unit,
    onUnstageFile: (String) -> Unit,
    onStageAll: () -> Unit,
    onCommit: () -> Unit,
    onFileClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        GitToolbar(
            branch = status.branch,
            hasChanges = status.hasChanges,
            hasStagedChanges = status.staged.isNotEmpty(),
            isLoading = isLoading,
            onRefresh = onRefresh,
            onStageAll = onStageAll,
            onCommit = onCommit
        )

        if (!status.isRepository) {
            NotARepositoryMessage()
        } else if (!status.hasChanges) {
            NoChangesMessage()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (status.staged.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(Strings.git_section_staged),
                            count = status.staged.size
                        )
                    }
                    items(status.staged, key = { "staged_${it.path}" }) { file ->
                        GitFileItem(
                            file = file,
                            isStaged = true,
                            onAction = { onUnstageFile(file.path) },
                            onClick = { onFileClick(file.path) }
                        )
                    }
                }

                if (status.unstaged.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(Strings.git_section_changes),
                            count = status.unstaged.size
                        )
                    }
                    items(status.unstaged, key = { "unstaged_${it.path}" }) { file ->
                        GitFileItem(
                            file = file,
                            isStaged = false,
                            onAction = { onStageFile(file.path) },
                            onClick = { onFileClick(file.path) }
                        )
                    }
                }

                if (status.untracked.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(Strings.git_section_untracked),
                            count = status.untracked.size
                        )
                    }
                    items(status.untracked, key = { "untracked_$it" }) { path ->
                        GitFileItem(
                            file = GitFileStatus(path, FileStatus.UNTRACKED),
                            isStaged = false,
                            onAction = { onStageFile(path) },
                            onClick = { onFileClick(path) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GitToolbar(
    branch: String?,
    hasChanges: Boolean,
    hasStagedChanges: Boolean,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onStageAll: () -> Unit,
    onCommit: () -> Unit
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
            if (!branch.isNullOrBlank()) {
                Text(
                    text = branch,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.width(8.dp))

            GitToolbarActionButton(
                onClick = onRefresh,
                enabled = !isLoading,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(Strings.menu_refresh),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            GitToolbarActionButton(
                onClick = onStageAll,
                enabled = hasChanges && !isLoading,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Strings.content_desc_stage_all),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            GitToolbarActionButton(
                onClick = onCommit,
                enabled = hasStagedChanges && !isLoading,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(Strings.git_commit),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int
) {
    TinaOverlayPanelSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "($count)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GitToolbarActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier.size(32.dp),
    minHeight: Dp = 32.dp,
    content: @Composable BoxScope.() -> Unit
) {
    TinaPanelSegmentButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        minHeight = minHeight,
        shape = MaterialTheme.shapes.small,
        color = color,
        contentPadding = PaddingValues(0.dp),
        content = content
    )
}

@Composable
private fun GitStatusEmptyState(
    title: String,
    subtitle: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        TinaOverlayPanelSurface(
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                subtitle?.takeIf(String::isNotBlank)?.let { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GitFileItem(
    file: GitFileStatus,
    isStaged: Boolean,
    onAction: () -> Unit,
    onClick: () -> Unit
) {
    val actionDescription = if (isStaged) {
        stringResource(Strings.content_desc_unstage)
    } else {
        stringResource(Strings.content_desc_stage)
    }

    TinaDialogSelectableCard(
        selected = false,
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        unselectedColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
        unselectedBorder = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = file.status.symbol,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = getStatusColor(file.status),
                modifier = Modifier.width(24.dp)
            )

            Text(
                text = file.path,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            GitToolbarActionButton(
                onClick = onAction,
                enabled = true,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                modifier = Modifier.size(30.dp),
                minHeight = 30.dp
            ) {
                Icon(
                    imageVector = if (isStaged) Icons.Default.Delete else Icons.Default.Add,
                    contentDescription = actionDescription,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun NotARepositoryMessage() {
    GitStatusEmptyState(
        title = stringResource(Strings.git_not_a_repo),
        subtitle = stringResource(Strings.git_init_hint)
    )
}

@Composable
private fun NoChangesMessage() {
    GitStatusEmptyState(title = stringResource(Strings.git_no_changes))
}

private fun getStatusColor(status: FileStatus): Color = when (status) {
    FileStatus.MODIFIED -> Color(0xFFE2A832)
    FileStatus.ADDED -> Color(0xFF4CAF50)
    FileStatus.DELETED -> Color(0xFFF44336)
    FileStatus.RENAMED -> Color(0xFF2196F3)
    FileStatus.COPIED -> Color(0xFF9C27B0)
    FileStatus.UNTRACKED -> Color(0xFF9E9E9E)
    FileStatus.IGNORED -> Color(0xFF757575)
}
