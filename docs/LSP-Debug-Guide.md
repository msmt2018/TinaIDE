# LSP 调试指南

## 问题场景

当你把光标移动到某个关键字时，没有看到任何提示信息，不知道是哪个环节出了问题。

## 解决方案

现在已经添加了 **LspDebugPanel** 调试面板，可以实时追踪每个环节的执行状态。

## 如何使用

### 1. 查看 Logcat 日志

在 Android Studio 中打开 Logcat，过滤标签：

```
LspDebugPanel
```

你会看到类似这样的日志：

```
[14:23:45.123] 🎯 Hover 触发: /path/to/file.cpp:10:5
[14:23:45.125] 📤 Hover 请求已发送: ID=12345
[14:23:45.135] ⏳ Hover 轮询: ID=12345, 尝试=0/500
[14:23:45.245] ⏳ Hover 轮询: ID=12345, 尝试=10/500
[14:23:45.350] ✅ Hover 结果: ID=12345, 内容="int main()"
```

### 2. 查看输出面板

如果你的应用有 OutputManager，日志也会显示在输出面板中。

### 3. 日志说明

#### Hover 请求流程

| 日志 | 说明 | 正常情况 |
|------|------|----------|
| 🎯 Hover 触发 | 光标移动触发 Hover 请求 | 每次光标移动都会触发 |
| 📤 Hover 请求已发送 | 请求已发送到 Native 层 | 立即出现 |
| ⏳ Hover 轮询 | 正在等待结果 | 每 100ms 打印一次 |
| ✅ Hover 结果 | 收到结果 | 通常在 100-500ms 内 |
| ⏰ Hover 超时 | 5 秒内未收到结果 | 不应该出现 |

#### Completion 请求流程

| 日志 | 说明 |
|------|------|
| 🎯 Completion 触发 | 触发代码补全 |
| 📤 Completion 请求已发送 | 请求已发送 |
| ⏳ Completion 轮询 | 等待结果 |
| ✅ Completion 结果 | 收到补全项 |

#### 文档同步流程

| 日志 | 说明 |
|------|------|
| 📂 文档已打开 | 文件打开时同步到 clangd |
| 📝 文档已更新 | 文件内容变化时同步 |
| 📁 文档已关闭 | 文件关闭时通知 clangd |

#### LSP 初始化流程

| 日志 | 说明 |
|------|------|
| 🚀 LSP 初始化中 | 开始初始化 clangd |
| ✅ LSP 初始化成功 | clangd 启动成功 |
| ❌ LSP 初始化失败 | clangd 启动失败 |

## 常见问题诊断

### 问题 1: 没有任何日志

**可能原因：**
- LspDebugPanel.enabled = false（默认是 true）
- 文件不是 C/C++ 文件
- Native LSP 被禁用（LspConfig.useNativeClient = false）

**解决方法：**
```kotlin
// 在代码中确认
LspDebugPanel.enabled = true
```

### 问题 2: 只有 "Hover 触发" 但没有 "请求已发送"

**可能原因：**
- LSP 未初始化
- 文档未同步到 clangd

**查看日志：**
- 是否有 "📂 文档已打开" 日志？
- 是否有 "🚀 LSP 初始化中" 日志？

### 问题 3: 请求已发送但一直轮询超时

**可能原因：**
- clangd 进程崩溃
- 共享内存通信失败
- compile_commands.json 缺失或错误

**查看日志：**
- 查找 "❌" 错误日志
- 查看 Native 层日志（标签：NativeLspJNI, NativeLspClient）

### 问题 4: 收到结果但没有显示

**可能原因：**
- UI 层没有正确处理结果
- Toast 被其他内容覆盖

**解决方法：**
- 查看 EditorFragment 的 showNativeHover 方法
- 考虑使用更明显的 UI 提示（如 Snackbar 或悬浮窗）

## 开关调试日志

如果日志太多影响性能，可以关闭：

```kotlin
LspDebugPanel.enabled = false
```

需要时再打开：

```kotlin
LspDebugPanel.enabled = true
```

## 下一步优化建议

1. **添加悬浮窗显示 Hover 信息**
   - 当前只用 Toast，不够明显
   - 建议使用 PopupWindow 或 Tooltip

2. **添加补全面板**
   - 当前没有补全面板
   - 建议集成到 Sora Editor 的补全系统

3. **添加状态指示器**
   - 在编辑器右下角显示 LSP 状态
   - 例如：🟢 已连接 / 🔴 未连接 / 🟡 初始化中

4. **添加性能监控**
   - 记录每个请求的耗时
   - 统计成功率和失败率
