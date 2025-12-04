# 底部面板使用指南

## 功能概览

底部面板是一个**可拖动展开的日志面板**，类似 MT 管理器或 Android Studio 的设计。

### 特性

- ✅ 可拖动展开/收起（三种状态）
- ✅ 美化的工具栏
- ✅ 实时显示编译日志
- ✅ 实时显示 LSP 调试日志
- ✅ LSP 状态指示器
- ✅ 快捷操作按钮

## 使用方法

### 1. 展开/收起面板

**三种状态：**

1. **收起状态**（默认）
   - 只显示工具栏
   - 高度：56dp

2. **半展开状态**
   - 显示一半日志内容
   - 高度：屏幕高度的 50%

3. **完全展开状态**
   - 显示全部日志内容
   - 高度：屏幕高度

**操作方式：**

- 点击拖动手柄（顶部灰色横条）切换状态
- 向上拖动展开，向下拖动收起
- 点击编译按钮自动展开

### 2. 工具栏按钮

| 按钮 | 功能 | 说明 |
|------|------|------|
| ▶️ 编译 | 编译项目 | 点击后自动展开面板显示日志 |
| ⏹️ 停止 | 停止编译 | 中断当前编译任务 |
| ❌ 清空 | 清空日志 | 清除所有日志内容 |
| 💾 保存 | 保存日志 | 将日志保存到文件 |

### 3. LSP 状态指示器

右侧显示 LSP 连接状态：

- 🟢 **绿色**：LSP 已连接
- 🔴 **红色**：LSP 未连接
- 🟡 **黄色**：LSP 初始化中

## 日志类型

### 编译日志

编译项目时自动显示：

```
[INFO] 开始编译项目...
[DEBUG] 编译命令: clang++ -o main main.cpp
[SUCCESS] 编译成功
```

### LSP 调试日志

光标移动时自动显示：

```
[14:23:45.123] 🎯 Hover 触发: /path/to/file.cpp:10:5
[14:23:45.125] 📤 Hover 请求已发送: ID=12345
[14:23:45.350] ✅ Hover 结果: ID=12345, 内容="int main()"
```

## 集成到你的代码

### 在 Activity 中使用

```kotlin
class MainActivity : BaseActivity<ActivityMainBinding>() {
    
    private lateinit var bottomLogPanel: BottomLogPanel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化底部面板
        bottomLogPanel = BottomLogPanel(
            container = binding.bottomPanelContainer,
            onCompile = { compileProject() },
            onStop = { stopCompile() }
        )
    }
    
    private fun compileProject() {
        bottomLogPanel.clearLog()
        bottomLogPanel.expand()
        // 执行编译...
    }
}
```

### 输出日志

```kotlin
// 方式 1: 通过 BottomLogPanel
bottomLogPanel.appendLog(LogLevel.INFO, "编译开始")

// 方式 2: 通过 OutputManager（全局）
OutputManager.appendLog(LogLevel.ERROR, "编译失败")

// 方式 3: 通过 LspDebugPanel（LSP 调试）
LspDebugPanel.onHoverTriggered(filePath, line, column)
```

### 更新 LSP 状态

```kotlin
bottomLogPanel.updateLspStatus(
    connected = true,
    message = "LSP 已连接"
)
```

## 自定义样式

### 修改颜色

编辑 `res/values/colors.xml`：

```xml
<color name="lsp_status_connected">#4CAF50</color>
<color name="lsp_status_disconnected">#F44336</color>
```

### 修改高度

编辑 `BottomLogPanel.kt`：

```kotlin
bottomSheetBehavior.apply {
    peekHeight = 56.dpToPx()  // 收起时高度
    halfExpandedRatio = 0.5f  // 半展开比例
}
```

### 添加更多按钮

编辑 `res/layout/bottom_sheet_log_panel.xml`，在工具栏中添加：

```xml
<com.google.android.material.button.MaterialButton
    android:id="@+id/btn_custom"
    style="@style/Widget.Material3.Button.IconButton"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    app:icon="@drawable/ic_custom" />
```

## 性能优化

### 日志数量限制

LogView 自动限制日志行数，避免内存溢出。

### 防抖机制

LSP 调试日志使用防抖，避免频繁输出。

### 异步输出

所有日志输出都在后台线程处理，不阻塞 UI。

## 常见问题

### Q: 面板无法拖动？

A: 确保布局中使用了 `CoordinatorLayout` 和 `BottomSheetBehavior`。

### Q: 日志不显示？

A: 检查是否调用了 `OutputManager.setLogView(logView)`。

### Q: LSP 状态不更新？

A: 确保在 `setupLspStatus()` 中添加了健康监听器。

## 下一步优化

1. **添加日志过滤**：按等级过滤日志
2. **添加搜索功能**：在日志中搜索关键词
3. **添加导出功能**：导出日志到文件
4. **添加主题切换**：支持亮色/暗色主题
