package com.wuxianggujun.tinaide.ui.compose.viewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.ui.compose.components.MarkdownViewer
import java.io.File

/**
 * Markdown 查看器
 */
@Composable
fun MarkdownViewerScreen(
    file: File,
    modifier: Modifier = Modifier,
) {
    var isPreviewMode by remember { mutableStateOf(true) }
    val errorMessage = stringResource(Strings.error_read_file_failed, "")
    val content = remember(file) {
        try {
            file.readText()
        } catch (e: Exception) {
            errorMessage.replace("%1\$s", e.message ?: "")
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { isPreviewMode = !isPreviewMode }) {
                Icon(
                    imageVector = if (isPreviewMode) Icons.Default.Edit else Icons.Default.Visibility,
                    contentDescription = stringResource(
                        if (isPreviewMode) Strings.content_desc_view_source else Strings.content_desc_preview,
                    ),
                )
            }
        }

        HorizontalDivider()

        // 内容区域
        if (isPreviewMode) {
            SelectionContainer {
                MarkdownViewer(
                    markdown = content,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                )
            }
        } else {
            MarkdownSource(
                content = content,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Markdown 源码视图
 */
@Composable
private fun MarkdownSource(
    content: String,
    modifier: Modifier = Modifier,
) {
    SelectionContainer {
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            ),
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
        )
    }
}
