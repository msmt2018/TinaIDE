# 编辑器补全系统架构设计

> 文档版本：1.0
> 创建日期：2026-03-07
> 最后更新：2026-03-07

## 1. 背景与问题

TinaIDE 的编辑器补全系统在日常使用中暴露了以下架构层面的问题：

### 1.1 当前架构的快速修复（2026-03-07 已落地）

| 问题 | 快速修复 | 局限性 |
|------|----------|--------|
| 空列表崩溃 `coerceIn(0, -1)` | `maxOf(0, lastIndex)` 防御 | 仅防崩溃，未从根本上把 loading/empty/ready 三态分离 |
| 每个按键重新请求 LSP | 缓存结果 + 前缀扩展时做客户端 re-filter | 缓存无版本管理，无 invalidation 策略，退格/跨词无重请求 |
| include snippet 无法区分 | detail 为空时 fallback 到 insertText 预览 | 应由 provider 层产出完整 detail，而非 UI 层兜底 |
| LSP 与 local 项重复 | merge 阶段按 label 去重 | 策略过于简单，KEYWORD 类被误去重的可能性存在 |
| 无 snippet 支持 | MVP snippet parser + session（Tab 跳转） | 无同步编辑、无占位符高亮渲染、无光标安全区校验 |

### 1.2 根本问题

1. **补全状态机缺失**：`showCompletion`/`isCompletionLoading`/`completionItems` 是独立 state 变量，缺少统一的状态机驱动（Idle → Requesting → Ready → Filtering → Dismissed），导致中间状态组合爆炸。
2. **请求策略与 UI 耦合**：`EditorInteractionController.onImeTextInserted()` 直接判断是否请求/过滤/关闭，逻辑散落在控制器中，难以测试和扩展。
3. **provider 层粒度不够**：`DefaultCompletionProvider` 一次性返回合并结果，没有区分"需要网络的 LSP 请求"和"纯本地计算"的不同生命周期。
4. **snippet engine 未与编辑器深度集成**：snippet session 的光标/选区管理不感知编辑器的 undo/redo 栈，不感知 folding、word-wrap 等视觉层。

---

## 2. 目标架构

### 2.1 架构分层

```
┌─────────────────────────────────────────────────┐
│                  UI 层（Compose）                 │
│  EditorCompletionPopup · EditorOverlays          │
│  SnippetPlaceholderRenderer                      │
├─────────────────────────────────────────────────┤
│             状态机层（CompletionStateMachine）      │
│  ┌───────────────────────────────────────────┐  │
│  │  Idle → Requesting → Ready → Filtering    │  │
│  │    ↑         ↓          ↓        ↓        │  │
│  │    └── Dismissed ←──────┴────────┘        │  │
│  └───────────────────────────────────────────┘  │
│  输入：按键事件、触发条件                          │
│  输出：completionItems、selectedIndex、loading    │
├─────────────────────────────────────────────────┤
│         Provider 层（CompletionOrchestrator）     │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │
│  │ LocalProv│  │ SnippetP │  │  LspProvider  │  │
│  │ (同步)   │  │ (同步)   │  │ (异步,可取消) │  │
│  └──────────┘  └──────────┘  └──────────────┘  │
│  合并策略：LSP 优先，相同 label 去重 local TEXT    │
├─────────────────────────────────────────────────┤
│           Snippet Engine 层                      │
│  SnippetParser → SnippetSession                  │
│  PlaceholderTracker（跟踪编辑操作对 offset 的影响）│
│  与 EditorState undo/redo 栈集成                  │
└─────────────────────────────────────────────────┘
```

### 2.2 核心类职责

| 类 | 职责 | 位置 |
|---|------|------|
| `CompletionStateMachine` | 管理补全生命周期状态转换，驱动 UI | `core/editor-view` |
| `CompletionOrchestrator` | 协调 local/snippet/LSP 三路 provider，管理缓存和 invalidation | `core/editor-view` |
| `CompletionCache` | 缓存 provider 原始结果 + 缓存版本号 + invalidation 策略 | `core/editor-view` |
| `SnippetParser` | 解析 snippet 文本为结构化 AST | `core/editor-view`（已有 MVP） |
| `SnippetSession` | 跟踪活跃 snippet 的 tab-stop 跳转状态 | `core/editor-view`（已有 MVP） |
| `PlaceholderTracker` | 监听文本编辑事件，更新 snippet placeholder 的 offset | `core/editor-view`（待实现） |

---

## 3. 补全状态机详细设计

### 3.1 状态定义

```kotlin
sealed class CompletionState {
    /** 无补全活动 */
    object Idle : CompletionState()

    /** 正在请求 provider（可能带有上一次的缓存结果用于过渡显示） */
    data class Requesting(
        val staleItems: List<EditorCompletionItem>,
        val query: String
    ) : CompletionState()

    /** 结果已到达，正在展示 */
    data class Ready(
        val items: List<EditorCompletionItem>,
        val rawResults: List<EditorCompletionItem>,
        val query: String,
        val selectedIndex: Int,
        val originPrefix: String
    ) : CompletionState()

    /** 在 Ready 基础上做客户端增量过滤 */
    data class Filtering(
        val rawResults: List<EditorCompletionItem>,
        val items: List<EditorCompletionItem>,
        val query: String,
        val selectedIndex: Int,
        val originPrefix: String
    ) : CompletionState()
}
```

### 3.2 状态转换

```
              输入字符(非trigger)
Idle ──────────────────────────────── Idle
  │
  │ 输入字符(trigger/letter)
  ▼
Requesting ──── provider返回 ────→ Ready
  │                                  │
  │ 取消(Escape/非字符)              │ 输入字符(前缀扩展)
  ▼                                  ▼
Idle                              Filtering
                                     │
                                     │ 输入trigger字符 / 前缀不匹配 / 退格回退超出originPrefix
                                     ▼
                                  Requesting (重新请求)
                                     │
                                     │ 过滤结果为空
                                     ▼
                                   Idle (自动关闭)
```

### 3.3 关键行为

| 用户操作 | 当前状态 | 行为 | 目标状态 |
|---------|---------|------|---------|
| 输入普通字符 | Idle | 满足 trigger 条件 → 发请求 | Requesting |
| 输入普通字符 | Ready/Filtering | 前缀扩展 → 客户端 re-filter | Filtering |
| 输入 trigger 字符 | 任意 | 取消旧请求，发新请求 | Requesting |
| 退格删字符 | Filtering | 新前缀仍以 originPrefix 开头 → re-filter；否则 → 重新请求 | Filtering 或 Requesting |
| 退格删字符 | Ready | 重新请求 | Requesting |
| Escape | 任意非 Idle | 取消所有 | Idle |
| 选择补全项 | Ready/Filtering | apply completion → dismiss | Idle（或 snippet session） |

### 3.4 缓存 invalidation

缓存应在以下条件下失效，触发全量重新请求：

1. **trigger 字符输入**：`.`、`->`、`::`
2. **前缀回退到 originPrefix 之前**：退格导致当前 query 不再以 originPrefix 开头
3. **文本版本不一致**：外部编辑（如 LSP code action、格式化）改变了文本
4. **超时**：缓存存活超过一定时间（如 30 秒）后自动失效
5. **光标跨行移动**：光标移到不同行时缓存应失效

---

## 4. Provider 层重构

### 4.1 当前问题

`DefaultCompletionProvider` 把 local 和 LSP 合并后一次性返回，导致：
- LSP 慢时整体被阻塞，用户看不到快速的本地结果
- 取消 LSP 请求时 local 结果也一起丢弃

### 4.2 目标：渐进式结果

```kotlin
interface CompletionOrchestrator {
    /**
     * 启动补全请求。
     *
     * @return Flow 按到达顺序发射部分结果：
     *   - 第一发：local + snippet（通常 <5ms）
     *   - 第二发：local + snippet + LSP 合并结果
     */
    fun requestCompletion(
        fileUri: String,
        position: Position,
        triggerChar: Char?
    ): Flow<CompletionResult>
}

data class CompletionResult(
    val items: List<CompletionItem>,
    val isComplete: Boolean  // true = LSP 已返回
)
```

### 4.3 合并策略

合并发生在 `CompletionOrchestrator` 内部，规则：

1. **LSP 结果到达后**：按 label（忽略大小写）去重 local TEXT 项，保留 local SNIPPET 项
2. **排序**：LSP 项优先 → 前缀匹配优先 → 按 label 字母序
3. **限制数量**：最终不超过 160 项

### 4.4 detail 产出

detail 的产出应在 provider 层而非 UI 层：

```kotlin
// CompletionOrchestrator 在 merge 之后做 detail 补全
private fun enrichDetail(item: CompletionItem): CompletionItem {
    if (!item.detail.isNullOrBlank()) return item
    val insertText = item.insertText ?: return item
    if (insertText == item.label) return item
    // 从 insertText 生成预览
    val preview = if (insertText.length <= 60) insertText else "${insertText.take(57)}..."
    return item.copy(detail = "→ $preview")
}
```

---

## 5. Snippet Engine 完整设计

### 5.1 当前 MVP 状态（2026-03-07 已落地）

| 能力 | 状态 |
|------|------|
| 解析 `${1:name}`、`${1}`、`$1`、`$0`、转义 | ✅ 已实现 |
| Tab 跳转到下一个占位符 | ✅ 已实现 |
| Escape 退出 snippet 模式 | ✅ 已实现 |
| 选中占位符默认值 | ✅ 已实现 |
| 占位符高亮渲染 | ❌ 未实现 |
| 同步编辑（多个 `$1` 同时修改） | ❌ 未实现 |
| 编辑操作跟踪（PlaceholderTracker） | ❌ 未实现 |
| Shift+Tab 后退 | ❌ 未实现 |
| Choice snippet `${1\|a,b\|}` | ❌ 未实现 |
| 变量替换 `$TM_FILENAME` | ❌ 未实现 |
| 与 undo/redo 栈集成 | ❌ 未实现 |

### 5.2 PlaceholderTracker（下一阶段核心）

当前 MVP 的最大隐患：snippet session 记录的是**绝对 offset**，但用户在占位符内输入文本后 offset 会变化，导致后续占位符的 offset 全部错误。

```kotlin
/**
 * 监听文本变更事件，动态调整 snippet 中各占位符的 offset。
 */
class PlaceholderTracker(
    private val session: SnippetSession
) {
    /**
     * 当文本在 [changeStart, changeEnd) 范围被替换为 newLength 长度的文本时，
     * 更新所有占位符的 offset。
     */
    fun onTextChanged(changeStart: Int, changeEnd: Int, newLength: Int): SnippetSession {
        val delta = newLength - (changeEnd - changeStart)
        val updatedPlaceholders = session.parsed.placeholders.map { p ->
            val absOffset = session.baseOffset + p.offsetInText
            when {
                changeStart >= absOffset + p.length -> p  // 变更在此占位符之后，不影响
                changeEnd <= absOffset -> {
                    // 变更在此占位符之前，整体平移
                    p.copy(offsetInText = p.offsetInText + delta)
                }
                else -> {
                    // 变更在此占位符内部（用户正在编辑）
                    val newLength = p.length + delta
                    p.copy(length = newLength.coerceAtLeast(0))
                }
            }
        }
        return session.copy(
            parsed = session.parsed.copy(
                placeholders = updatedPlaceholders,
                expandedText = "" // 展开文本已过时，但仅用于初始化
            )
        )
    }
}
```

### 5.3 占位符高亮渲染

在 `EditorOverlays.kt` 或独立的 `SnippetPlaceholderRenderer.kt` 中：

```kotlin
@Composable
internal fun SnippetPlaceholderOverlay(session: TinaEditorSession) {
    val snippetSession = session.state.activeSnippetSession ?: return
    // 遍历所有占位符，绘制背景高亮
    // 当前占位符：主题色 alpha 0.3
    // 其他占位符：灰色 alpha 0.15
    // 使用 Canvas 在对应 offset 范围绘制矩形
}
```

### 5.4 同步编辑（Phase 2）

当 snippet 中多个节点共享同一 tabstopIndex（如两个 `$1`），编辑其中一个时需同步更新另一个：

```kotlin
// SnippetSession 扩展
fun placeholdersForTabstop(index: Int): List<SnippetPlaceholderInfo> {
    return placeholders.filter { it.tabstopIndex == index }
}

// 编辑器在文本变更后
fun onPlaceholderEdited(editedPlaceholder: SnippetPlaceholderInfo, newText: String) {
    val siblings = placeholdersForTabstop(editedPlaceholder.tabstopIndex)
    siblings.filter { it !== editedPlaceholder }.forEach { sibling ->
        // 替换兄弟占位符的内容为 newText
        textBuffer.replace(sibling.absoluteOffset, sibling.absoluteOffset + sibling.length, newText)
    }
}
```

### 5.5 实施路线

| 阶段 | 内容 | 优先级 | 依赖 |
|------|------|--------|------|
| **Phase 0** | MVP（已完成）：解析、Tab 跳转、Escape 退出 | P0 | — |
| **Phase 1** | PlaceholderTracker：文本变更后更新 offset | P0 | Phase 0 |
| **Phase 2** | 占位符高亮渲染 | P1 | Phase 0 |
| **Phase 3** | 同步编辑（多个相同 tabstop 联动） | P1 | Phase 1 |
| **Phase 4** | Shift+Tab 后退、与 undo/redo 集成 | P2 | Phase 1 |
| **Phase 5** | Choice snippet、变量替换 | P2 | Phase 3 |

---

## 6. 补全 UI 优化

### 6.1 loading 状态分离

当前：`isCompletionLoading = true` 时如果 items 为空，popup 只显示 loading bar。
目标：三态 UI：

| 状态 | UI 表现 |
|------|---------|
| Requesting + 无旧结果 | 显示 loading bar，不显示列表 |
| Requesting + 有旧结果 | 显示旧列表 + loading bar（顶部） |
| Ready / Filtering | 显示列表，无 loading bar |

### 6.2 补全项来源标记

在补全列表中显示来源标记（类似 VS Code 的 "LSP" / "Local" 小标签），帮助用户理解结果来源：

```
┌────────────────────────────────┐
│ [S] include      → #include <>│
│ [S] include      → #include ""│
│ [Fn] printf         LSP       │
│ [V]  argc           LSP       │
│ [Tx] include         Local    │
└────────────────────────────────┘
```

### 6.3 documentation 预览

当用户在补全列表中选中一项时，可展示该项的 documentation（来自 clangd 的函数签名、注释等）。实现为可选的侧面板或底部区域。

---

## 7. 现有文件与影响范围

### 7.1 当前快速修复涉及的文件

| 文件 | 变更内容 |
|------|----------|
| `core/editor-view/.../HighlightModels.kt` | `EditorCompletionItem` 加 `snippetText`、`isLsp` |
| `core/editor-view/.../EditorCompletionUtils.kt` | 新增 `isTriggerCharacter`、`isCompletionPrefixExtension` |
| `core/editor-view/.../EditorInteractionController.kt` | 增量过滤分支 |
| `core/editor-view/.../EditorState.kt` | 缓存字段、`refilterCompletion()`、snippet session 方法 |
| `core/editor-view/.../EditorStateEditOperations.kt` | `applySnippetCompletion()` |
| `core/editor-view/.../EditorKeyboardShortcuts.kt` | Tab/Escape 处理 snippet |
| `core/editor-view/.../EditorCompletionPopup.kt` | detail fallback 显示 |
| `core/editor-view/.../EditorOverlays.kt` | `coerceIn` 空列表修复 |
| `core/editor-view/.../SnippetParser.kt` | **新文件** — snippet 解析器 |
| `core/editor-view/.../SnippetSession.kt` | **新文件** — snippet 会话 |
| `core/editor-lsp/.../CompletionProvider.kt` | `CompletionItem` 加 `snippetText`；`mergeCompletions` LSP 去重 |
| `app/.../LspEditorManager.kt` | 提取原始 snippet 文本 |
| `app/.../EditorContainerState.kt` | snippet 补全传递原始文本 |
| `app/.../TinaCodeEditorPage.kt` | 映射加 `snippetText`、`isLsp` |

### 7.2 目标架构需要新增/重构的文件

| 文件 | 说明 |
|------|------|
| `core/editor-view/.../CompletionStateMachine.kt` | 补全状态机（替代散落的 state 变量） |
| `core/editor-view/.../CompletionCache.kt` | 缓存管理（版本号、invalidation） |
| `core/editor-view/.../PlaceholderTracker.kt` | snippet 占位符 offset 跟踪 |
| `core/editor-view/.../SnippetPlaceholderRenderer.kt` | 占位符高亮渲染 |

---

## 8. 相关文档

- [LSP-Snippet-Placeholder-Handling.md](LSP-Snippet-Placeholder-Handling.md) — snippet 占位符处理机制
- [TinaEditor-Highlight-Pipeline-Review.md](TinaEditor-Highlight-Pipeline-Review.md) — 编辑器渲染 / 高亮链路现状
- [../架构概览.md](../架构概览.md) — 当前编辑器与语言服务的主入口

## 9. 更新日志

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-03-07 | 1.0 | 初始版本：记录快速修复与目标架构设计 |
