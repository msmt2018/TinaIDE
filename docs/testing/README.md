# TinaIDE 测试文档

> 更新日期：2026-04-22

本目录只保留当前仍值得固定维护的测试入口说明。

### 内置测试框架

TinaIDE 内置了 LSP 测试框架，可在设置 > 开发者选项中访问：

- **LspTestManager**: 测试管理器，支持补全、跳转、诊断等测试
- **ClangdTestScreen**: Clangd 测试界面，可视化测试结果
- **LspTestTypes**: 测试类型定义（补全测试、跳转测试、诊断测试等）

### Popup 回归固定入口

编辑器 popup 的共享回归建议固定跑下面两组命令：

```bash
./gradlew :core:editor-view:testDebugUnitTest --tests "com.wuxianggujun.tinaide.core.editorview.EditorPopupComposeSmokeTest" --tests "com.wuxianggujun.tinaide.core.editorview.PopupOverlaySharedAnchorIntegrationTest" --tests "com.wuxianggujun.tinaide.core.editorview.EditorOverlaysIntegrationTest"
```

```bash
./gradlew :core:editor-view:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wuxianggujun.tinaide.core.editorview.EditorCompletionPopupInstrumentationTest,com.wuxianggujun.tinaide.core.editorview.EditorSharedPopupInstrumentationTest
```

其中：

- 第一组覆盖 popup 组件 smoke、共享 anchor/layout 回归、`EditorOverlays` 组合场景。
- 第二组覆盖设备侧补全框、签名提示、选择菜单 popup 的稳定 tag 与交互回归。

## 相关指南

- [LSP 调试指南](../guides/LSP-Debug-Guide.md) - LSP 调试方法
- [远程 LSP 使用指南](../guides/Remote-LSP-Guide.md) - 远程 LSP 功能使用

## 相关源码

| 文件 | 说明 |
|------|------|
| [LspTestManager.kt](../../core/lsp/src/main/java/com/wuxianggujun/tinaide/testing/lsp/LspTestManager.kt) | LSP 测试管理器 |
| [LspTestTypes.kt](../../core/lsp/src/main/java/com/wuxianggujun/tinaide/testing/lsp/LspTestTypes.kt) | 测试类型定义 |
| [ClangdTestScreen.kt](../../app/src/main/java/com/wuxianggujun/tinaide/ui/compose/screens/testing/ClangdTestScreen.kt) | Clangd 测试界面 |
| [ClangdTestActivity.kt](../../app/src/main/java/com/wuxianggujun/tinaide/ui/compose/screens/testing/ClangdTestActivity.kt) | 测试 Activity |
