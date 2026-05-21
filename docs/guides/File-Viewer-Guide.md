# 文件预览指南

> 更新日期：2026-01-18

TinaIDE 内置了一组“文件预览器”，用于在不打开编辑器的情况下快速查看常见文件格式（例如 Markdown、JSON、图片与二进制文件）。

## 如何打开

1. 在左侧文件树中点击文件。
2. TinaIDE 会根据文件类型自动选择合适的预览方式：
   - 可编辑文本：进入编辑器
   - 可预览格式：进入对应预览器

如果你希望交给系统应用处理，可以在文件树的上下文菜单中选择“用其他应用打开/分享”。

## 支持的预览类型

### Markdown

- 用途：快速阅读 `README.md`、说明文档等。
- 常见扩展名：`.md`
- 相关实现：`feature/viewer/src/main/java/.../ui/compose/viewer/MarkdownViewerScreen.kt`

### JSON

- 用途：格式化查看 JSON 内容。
- 常见扩展名：`.json`
- 相关实现：`feature/viewer/src/main/java/.../ui/compose/viewer/JsonViewerScreen.kt`

### 图片

- 用途：预览项目内图片资源。
- 常见扩展名：`.png` / `.jpg` / `.jpeg` / `.webp` / `.gif`
- 相关实现：`feature/viewer/src/main/java/.../ui/compose/viewer/ImagePreviewScreen.kt`

### Hex（二进制）

- 用途：查看二进制文件的十六进制内容。
- 常见扩展名：任意（通常用于 `.so` / `.bin` 等）
- 相关实现：`feature/viewer/src/main/java/.../ui/compose/viewer/HexViewerScreen.kt`

## 注意事项

- 大文件预览可能较慢；建议优先在编辑器中按需打开与搜索。
- 二进制文件默认只适合“查看”，不建议直接编辑。
