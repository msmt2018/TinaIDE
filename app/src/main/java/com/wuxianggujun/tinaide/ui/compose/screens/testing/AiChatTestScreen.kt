package com.wuxianggujun.tinaide.ui.compose.screens.testing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import com.wuxianggujun.tinaide.ui.compose.components.ChatMessageBubble
import com.wuxianggujun.tinaide.ui.compose.components.TinaBackHandlers
import com.wuxianggujun.tinaide.ui.compose.components.tinaBackAction

private val testScenarios = AiChatTestScreenSupport.buildScenarios()

/**
 * AI 对话测试界面
 * 用于测试 Markdown 渲染、工具调用等功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatTestScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var selectedScenario by remember { mutableStateOf<AiChatTestScenario?>(null) }

    val handleBack = {
        if (selectedScenario != null) {
            selectedScenario = null
        } else {
            onNavigateBack()
        }
    }

    // 处理返回：如果在场景详情页，先返回到场景列表
    TinaBackHandlers(
        tinaBackAction(enabled = selectedScenario != null) {
            selectedScenario = null
        }
    )

    // 复制到剪贴板的辅助函数
    val copyToClipboard: (String) -> Unit = remember {
        { text ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("code", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, Strings.ai_code_copied.str(), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Strings.dev_options_ai_chat_test)) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Strings.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedScenario == null) {
                // 场景选择列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(testScenarios) { scenario ->
                        ScenarioCard(
                            scenario = scenario,
                            onClick = { selectedScenario = scenario }
                        )
                    }
                }
            } else {
                // 显示选中的场景
                selectedScenario?.let { scenario ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 场景标题
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = scenario.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = scenario.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                TextButton(onClick = { selectedScenario = null }) {
                                    Text(stringResource(Strings.back))
                                }
                            }
                        }

                        HorizontalDivider()

                        // 消息列表
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(scenario.messages, key = { it.id }) { message ->
                                ChatMessageBubble(
                                    message = message,
                                    onCopyCode = copyToClipboard,
                                    onInsertCode = { /* 测试界面不支持插入 */ }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenarioCard(
    scenario: AiChatTestScenario,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = scenario.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = scenario.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    Strings.dev_options_ai_chat_message_count,
                    scenario.messages.size
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
