package com.wuxianggujun.tinaide.snippet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.snippet.model.SnippetSummary
import com.wuxianggujun.tinaide.ui.compose.components.TinaCard
import com.wuxianggujun.tinaide.ui.compose.components.TinaAlertDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogCard
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogContentColumn
import com.wuxianggujun.tinaide.ui.compose.components.TinaPrimaryButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaTextButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaTextField
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogTitleText

/**
 * 代码片段卡片
 */
@Composable
fun SnippetCard(
    snippet: SnippetSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TinaCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (snippet.status == "draft") {
                        TagChip(text = stringResource(Strings.snippet_status_draft))
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = snippet.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(Strings.snippet_by_author, snippet.author.displayName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = snippet.favoriteCount.toString(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = snippet.description.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    snippet.tags.take(2).forEach { tag ->
                        TagChip(text = tag)
                    }
                }
                Text(
                    text = stringResource(Strings.market_copy_count, snippet.copyCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 标签芯片
 */
@Composable
fun TagChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 评分星星
 */
@Composable
fun RatingStars(
    rating: Int,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            TextButton(onClick = { onRate(i) }) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = if (i <= rating) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * 创建代码片段对话框
 */
@Composable
fun CreateSnippetDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String?, language: String, code: String, isDraft: Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("kotlin") }
    var code by remember { mutableStateOf("") }
    var isDraft by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    TinaAlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { TinaDialogTitleText(stringResource(Strings.snippet_create_title)) },
        text = {
            TinaDialogContentColumn(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (error != null) {
                    TinaDialogCard(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.36f)
                    ) {
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                TinaDialogCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(Strings.snippet_save_as_draft))
                        Switch(
                            checked = isDraft,
                            onCheckedChange = { isDraft = it },
                            enabled = !isSubmitting
                        )
                    }
                }

                TinaTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(Strings.snippet_field_title),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                )

                TinaTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = stringResource(Strings.snippet_field_language),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                )

                TinaTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = stringResource(Strings.snippet_field_description),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                )

                TinaTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = stringResource(Strings.snippet_field_code),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    enabled = !isSubmitting,
                    singleLine = false,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    )
                )

                if (isSubmitting) {
                    TinaDialogCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Strings.snippet_publishing))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TinaPrimaryButton(
                text = stringResource(
                    if (isDraft) Strings.snippet_save_draft else Strings.snippet_publish
                ),
                onClick = {
                    isSubmitting = true
                    error = null
                    onCreate(title, description, language, code, isDraft)
                },
                enabled = !isSubmitting
            )
        },
        dismissButton = {
            TinaTextButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                enabled = !isSubmitting
            )
        }
    )
}
