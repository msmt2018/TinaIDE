# 智能换行实现方案

## 核心思路

只对超长行（例如 >200 字符）启用换行，短行保持不变。这样：
- 大部分代码行不换行 → 缩放稳定
- 少数超长行换行 → 避免无限横向滚动
- 用户可自定义阈值

## 实现步骤

### 1. 修改 EditorConfig

```kotlin
// core/editor-view/src/main/java/com/wuxianggujun/tinaide/core/editorview/EditorConfig.kt

data class EditorConfig(
    // 原有配置
    val wordWrap: Boolean = false,

    // 新增：智能换行
    val smartWrap: Boolean = false,
    val smartWrapThreshold: Int = 200,  // 超过200字符才换行

    // ... 其他配置
) {
    companion object {
        const val MIN_WRAP_THRESHOLD = 80
        const val MAX_WRAP_THRESHOLD = 500
        const val DEFAULT_WRAP_THRESHOLD = 200
    }
}
```

### 2. 修改 EditorState

```kotlin
// core/editor-view/src/main/java/com/wuxianggujun/tinaide/core/editorview/EditorState.kt

class EditorState(
    // ... 现有代码
) {
    /**
     * 判断某一行是否需要换行
     */
    fun shouldWrapLine(line: Int): Boolean {
        // 全局换行模式
        if (config.wordWrap) return true

        // 智能换行模式
        if (config.smartWrap) {
            val lineLength = textBuffer.getLineLength(line)
            return lineLength > config.smartWrapThreshold
        }

        // 不换行模式
        return false
    }

    /**
     * 获取换行模式描述（用于调试）
     */
    fun getWrapModeDescription(): String {
        return when {
            config.wordWrap -> "全部换行"
            config.smartWrap -> "智能换行(>${config.smartWrapThreshold}字符)"
            else -> "不换行"
        }
    }
}
```

### 3. 修改渲染逻辑

```kotlin
// core/editor-view/src/main/java/com/wuxianggujun/tinaide/core/editorview/TextRenderer.kt

internal class TextRenderer {
    fun renderLine(
        drawScope: DrawScope,
        state: EditorState,
        line: Int,
        // ... 其他参数
    ) {
        // 检查是否需要换行
        val shouldWrap = state.shouldWrapLine(line)

        if (shouldWrap) {
            // 使用换行渲染
            renderWrappedLine(drawScope, state, line, ...)
        } else {
            // 使用单行渲染
            renderSingleLine(drawScope, state, line, ...)
        }
    }
}
```

### 4. 修改缩放逻辑

```kotlin
// core/editor-view/src/main/java/com/wuxianggujun/tinaide/core/editorview/TinaEditorSession.kt

val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
    // ... 现有代码

    // 智能换行模式下的特殊处理
    if (state.config.smartWrap) {
        // 智能换行：只有少数行会换行，大部分行缩放稳定
        // 可以使用正常的锚点计算
        state.freezeWordWrapLayoutIfNeeded()  // 冻结已换行的行
    } else if (state.config.wordWrap) {
        // 全局换行：所有行都换行，需要冻结
        state.freezeWordWrapLayoutIfNeeded()
    }

    // ... 其余缩放逻辑
}
```

### 5. 添加设置界面

```kotlin
// feature/settings/src/main/java/com/wuxianggujun/tinaide/ui/compose/screens/settings/sections/EditorSettingsSection.kt

@Composable
fun EditorSettingsSection(
    config: EditorConfig,
    onConfigChange: (EditorConfig) -> Unit
) {
    Section(title = "文本换行") {
        // 换行模式选择
        Column {
            WrapModeOption(
                title = "不换行（推荐）",
                subtitle = "适合代码编辑，缩放稳定",
                icon = Icons.Default.HorizontalRule,
                selected = !config.wordWrap && !config.smartWrap,
                onClick = {
                    onConfigChange(config.copy(
                        wordWrap = false,
                        smartWrap = false
                    ))
                }
            )

            WrapModeOption(
                title = "智能换行",
                subtitle = "仅对超长行换行，兼顾稳定性",
                icon = Icons.Default.WrapText,
                selected = config.smartWrap,
                onClick = {
                    onConfigChange(config.copy(
                        wordWrap = false,
                        smartWrap = true
                    ))
                }
            )

            WrapModeOption(
                title = "全部换行",
                subtitle = "所有行自动换行，缩放可能不稳定",
                icon = Icons.Default.Notes,
                selected = config.wordWrap,
                onClick = {
                    onConfigChange(config.copy(
                        wordWrap = true,
                        smartWrap = false
                    ))
                }
            )
        }

        // 智能换行阈值设置
        AnimatedVisibility(visible = config.smartWrap) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = "换行阈值：${config.smartWrapThreshold} 字符",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = config.smartWrapThreshold.toFloat(),
                    onValueChange = { value ->
                        onConfigChange(config.copy(
                            smartWrapThreshold = value.toInt()
                        ))
                    },
                    valueRange = EditorConfig.MIN_WRAP_THRESHOLD.toFloat()..
                                 EditorConfig.MAX_WRAP_THRESHOLD.toFloat(),
                    steps = 20
                )
                Text(
                    text = "超过此长度的行将自动换行",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 警告提示
        if (config.wordWrap) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "全部换行模式下，双指缩放可能不稳定",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun WrapModeOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
```

### 6. 性能优化

```kotlin
// core/editor-view/src/main/java/com/wuxianggujun/tinaide/core/editorview/EditorState.kt

class EditorState(
    // ... 现有代码
) {
    // 缓存每行的换行状态，避免重复计算
    private val wrapStateCache = mutableMapOf<Int, Boolean>()

    fun shouldWrapLine(line: Int): Boolean {
        // 检查缓存
        wrapStateCache[line]?.let { return it }

        // 计算并缓存
        val shouldWrap = when {
            config.wordWrap -> true
            config.smartWrap -> {
                val lineLength = textBuffer.getLineLength(line)
                lineLength > config.smartWrapThreshold
            }
            else -> false
        }

        wrapStateCache[line] = shouldWrap
        return shouldWrap
    }

    // 配置变化时清除缓存
    fun onConfigChanged(newConfig: EditorConfig) {
        if (newConfig.wordWrap != config.wordWrap ||
            newConfig.smartWrap != config.smartWrap ||
            newConfig.smartWrapThreshold != config.smartWrapThreshold) {
            wrapStateCache.clear()
        }
        _config = newConfig
    }

    // 文本变化时清除相关行的缓存
    fun onTextChanged(startLine: Int, endLine: Int) {
        for (line in startLine..endLine) {
            wrapStateCache.remove(line)
        }
    }
}
```

## 优势

1. **缩放稳定**：90%的代码行不换行，缩放锚点稳定
2. **用户友好**：超长行自动换行，避免无限横向滚动
3. **性能好**：只计算少数行的换行
4. **可配置**：用户可自定义阈值

## 测试场景

1. 短行（<200字符）：不换行，缩放稳定
2. 长行（>200字符）：自动换行
3. 缩放时：短行锚点稳定，长行可能轻微偏移（可接受）
4. 配置切换：立即生效，无需重启

## 迁移路径

1. **Phase 1**（立即）：默认关闭 wordWrap
2. **Phase 2**（1-2天）：实现智能换行
3. **Phase 3**（可选）：收集用户反馈，调整默认阈值
