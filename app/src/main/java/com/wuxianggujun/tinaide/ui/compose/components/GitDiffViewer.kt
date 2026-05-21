package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuxianggujun.tinaide.core.git.DiffLine
import com.wuxianggujun.tinaide.core.git.DiffLineType
import com.wuxianggujun.tinaide.core.git.GitDiff
import com.wuxianggujun.tinaide.core.i18n.Strings

/**
 * Git Diff 查看器对话框
 */
@Composable
fun GitDiffDialog(
    filePath: String,
    diff: GitDiff?,
    rawDiff: String?,
    isLoading: Boolean,
    error: String?,
    isStaged: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayName = remember(filePath) {
        filePath.substringAfterLast('/').substringAfterLast('\\')
    }

    TinaAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.85f),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TinaDialogTitleText(displayName)
                Text(
                    text = stringResource(
                        if (isStaged) Strings.git_staged_changes else Strings.git_unstaged_changes
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            TinaDialogContentColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (displayName != filePath) {
                    CreationTargetSection(
                        title = stringResource(Strings.label_target_path),
                        value = filePath
                    )
                }

                TinaDialogCard(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    GitDiffViewport {
                        when {
                            isLoading -> {
                                GitDiffStateCard(
                                    message = stringResource(Strings.loading),
                                    loading = true
                                )
                            }

                            error != null -> {
                                GitDiffStateCard(
                                    message = error,
                                    isError = true
                                )
                            }

                            diff != null -> {
                                GitDiffContent(diff = diff)
                            }

                            rawDiff != null -> {
                                RawDiffContent(rawDiff = rawDiff)
                            }

                            else -> {
                                GitDiffStateCard(
                                    message = stringResource(Strings.git_no_diff)
                                )
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
        }
    )
}

@Composable
private fun GitDiffViewport(
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp, max = 520.dp),
        content = content
    )
}

@Composable
private fun BoxScope.GitDiffStateCard(
    message: String,
    loading: Boolean = false,
    isError: Boolean = false
) {
    TinaDialogCard(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(16.dp)
            .fillMaxWidth(),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.36f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        },
        border = BorderStroke(
            1.dp,
            if (isError) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 解析后的 Diff 内容显示
 */
@Composable
private fun GitDiffContent(diff: GitDiff) {
    val horizontalScrollState = rememberScrollState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(horizontalScrollState)
    ) {
        diff.hunks.forEachIndexed { hunkIndex, hunk ->
            item(key = "hunk_header_$hunkIndex") {
                HunkHeader(
                    oldStart = hunk.oldStart,
                    oldCount = hunk.oldCount,
                    newStart = hunk.newStart,
                    newCount = hunk.newCount
                )
            }

            itemsIndexed(
                items = hunk.lines,
                key = { lineIndex, _ -> "hunk_${hunkIndex}_line_$lineIndex" }
            ) { _, line ->
                DiffLineRow(line = line)
            }

            if (hunkIndex < diff.hunks.size - 1) {
                item(key = "hunk_separator_$hunkIndex") {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

/**
 * 原始 Diff 文本显示（未解析时使用）
 */
@Composable
private fun RawDiffContent(rawDiff: String) {
    val horizontalScrollState = rememberScrollState()
    val lines = remember(rawDiff) { rawDiff.lines() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(horizontalScrollState)
    ) {
        itemsIndexed(lines, key = { index, _ -> "raw_line_$index" }) { _, line ->
            RawDiffLine(line = line)
        }
    }
}

@Composable
private fun HunkHeader(
    oldStart: Int,
    oldCount: Int,
    newStart: Int,
    newCount: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2D4A6E).copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "@@ -$oldStart,$oldCount +$newStart,$newCount @@",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            ),
            color = Color(0xFF6B9BD2)
        )
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val backgroundColor = when (line.type) {
        DiffLineType.ADDED -> Color(0xFF2D4A2D).copy(alpha = 0.4f)
        DiffLineType.REMOVED -> Color(0xFF4A2D2D).copy(alpha = 0.4f)
        DiffLineType.CONTEXT -> Color.Transparent
    }

    val textColor = when (line.type) {
        DiffLineType.ADDED -> Color(0xFF98C379)
        DiffLineType.REMOVED -> Color(0xFFE06C75)
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
        Text(
            text = line.oldLineNumber?.toString() ?: "",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(36.dp)
        )

        Text(
            text = line.newLineNumber?.toString() ?: "",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(36.dp)
        )

        Text(
            text = prefix,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            color = textColor,
            modifier = Modifier.width(16.dp)
        )

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

@Composable
private fun RawDiffLine(line: String) {
    val backgroundColor = when {
        line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF2D4A2D).copy(alpha = 0.4f)
        line.startsWith("-") && !line.startsWith("---") -> Color(0xFF4A2D2D).copy(alpha = 0.4f)
        line.startsWith("@@") -> Color(0xFF2D4A6E).copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    val textColor = when {
        line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF98C379)
        line.startsWith("-") && !line.startsWith("---") -> Color(0xFFE06C75)
        line.startsWith("@@") -> Color(0xFF6B9BD2)
        line.startsWith("diff ") || line.startsWith("index ") -> Color(0xFFD19A66)
        line.startsWith("+++") || line.startsWith("---") -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 1.dp)
    ) {
        Text(
            text = line,
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
