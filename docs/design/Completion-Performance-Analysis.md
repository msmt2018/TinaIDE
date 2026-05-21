# 编辑器补全性能分析与改进方案（2026-03-29）

> 本文档基于代码静态审查，结合两轮分析修正（含第三方 AI 交叉复核），以当前代码为准。

## 1. 问题现象

- C/C++ 源文件（`.c/.cpp/.h`）触发补全后，UI 卡顿或等待较长时间才出现候选列表，头文件越多越明显。
- CMakeLists.txt / `.cmake` 文件补全流畅，几乎无感知延迟。
- 关键：**这是项目形态差异，不是文件类型差异**。

---

## 2. 补全完整链路（从触发到展示）

```
用户输入
  → EditorInteractionController.scheduleTriggerCompletion()
      delay(COMPLETION_TRIGGER_DEBOUNCE_MS)
  → EditorState.requestCompletion(triggerChar)
      → onRequestCompletion(cursorPosition, triggerChar)    [注入的 lambda]
  → DefaultCompletionProvider.requestCompletion()           [core/editor-lsp]
      ├─ async: localProvider()                             [Dispatchers.Default]
      └─ async: lspProvider() + withTimeoutOrNull(500ms)    [Dispatchers.IO]
          → EditorContainerState.requestLspCompletion()
          → LspEditorManager.requestCompletion()
              → session.completion(params).get(6s)          [阻塞 Java call]
  → mergeCompletions(local, lsp, prefix)                   [去重 + 排序 + take(50)]
  → EditorState.filterCompletionItems()                    [take(160)]
  → CompletionUiState.Visible 展示
```

关键文件：
- `core/editor-lsp/src/main/java/com/wuxianggujun/tinaide/core/editorlsp/CompletionProvider.kt`
- `app/src/main/java/com/wuxianggujun/tinaide/ui/compose/state/editor/LspEditorManager.kt`
- `app/src/main/java/com/wuxianggujun/tinaide/ui/compose/components/editor/TinaCodeEditorPage.kt`
- `core/editor-view/src/main/java/com/wuxianggujun/tinaide/core/editorview/EditorState.kt`

---

## 3. 根本原因分析（已修正）

### 3.1 ✅ 主因：compile_commands 复用 vs 生成路径的差异

这是 CMake 项目和 single-file 项目最关键的差异，发生在 clangd **启动前**。

**CMake 项目路径（快）**：

`CompileDatabaseProvider.prepare()` 发现已有 `compile_commands.json`，且 `buildSystem != SINGLE_FILE`，直接复用：

```kotlin
// CompileDatabaseProvider.kt:100
val needsGeneration = existingCompileDir == null || metadata?.buildSystem == ProjectBuildSystem.SINGLE_FILE
val projectType = if (existingCompileDir != null && !needsGeneration) ProjectType.CMAKE_PROJECT else baseProjectType
```

CMake 项目走 `CMAKE_PROJECT` 路径 → `ensureWithResult()` 直接返回已有目录 → clangd 立即 attach。

**Single-file 项目路径（慢）**：

即使已有 `compile_commands.json`，`SINGLE_FILE` 标记会强制 `needsGeneration = true`，走完整生成流程：

1. `CompileDatabaseProvider.ensureWithResult()` 校验 C++ 标准、package fingerprint，决定是否重新生成
2. 生成时调用 `CompileCommandsGenerator`，注入大量编译参数：
   - `--sysroot=<NDK sysroot>`
   - `-resource-dir <clang resource dir>`
   - `-isystem .../libc++/v1`（C++ 标准库头文件）
   - `-isystem <clang builtin includes>`
   - `-I <project includes>` + 包 include
   - `-target <triple>` `-DANDROID` `-D__ANDROID_API__=xx`
3. 生成完毕后 clangd 才 attach
4. clangd 第一次处理这些参数时，需要 **parse 所有 #include preamble**，头文件越多耗时越长

关键位置：
- `core/lsp/src/main/java/com/wuxianggujun/tinaide/core/lsp/CompileDatabaseProvider.kt:97-100`
- `core/lsp/src/main/java/com/wuxianggujun/tinaide/core/lsp/CompileCommandsGenerator.kt:86-150`
- `app/src/main/java/com/wuxianggujun/tinaide/ui/compose/state/editor/LspEditorManager.kt:679`

---

### 3.2 ✅ 次因：两层超时不一致（确认 bug，放大卡感）

```
DefaultCompletionProvider:  withTimeoutOrNull(500ms)  → 仅 cancel 协程
LspEditorManager:           Future.get(6s)            → Java 阻塞调用，cancel 无效
```

当 clangd 冷启动慢时（preamble parse > 500ms），协程被 cancel，但 `Future.get(6s)` 阻塞仍然持续，IO 线程被挂住最长 6s。这放大了 single-file 冷启动时的卡顿感知。

关键位置：
- `app/src/main/java/com/wuxianggujun/tinaide/ui/compose/state/editor/LspEditorManager.kt:356`

---

### 3.3 ℹ️ 辅助因素：local 补全为空影响等待预算

`DefaultCompletionProvider` 根据 local 是否有结果选择不同的 LSP 等待预算：

```kotlin
// CompletionProvider.kt
private val lspMergeWaitBudgetMs: Long = minOf(140L, lspTimeoutMs)  // 有 local → 140ms
val lspWaitMs = if (local.isEmpty()) lspTimeoutMs else lspMergeWaitBudgetMs  // 无 local → 500ms
```

| 文件类型 | local 补全 | LSP 等待预算 |
|---|---|---|
| CMakeLists.txt / .cmake | 丰富（静态表 + AST）| 140ms |
| .c / .cpp / .h | 仅文本 identifier | 500ms |

**注意**：这不是 CMake vs C/C++ 卡顿差异的主因，主因是 3.1。这只是在 clangd 慢时额外多等 360ms 的次要影响。

---

### 3.4 ✅ clangd background-index 配置现状

`ClangdSettings.backgroundIndex = true`（默认开启），`buildCommandArgs()` 会追加 `--background-index`。

background-index 是 clangd 自己的后台索引机制，会在后台扫描项目符号、跨文件补全。这**不需要 app 层自建索引**。

关键位置：
- `core/config/src/main/java/com/wuxianggujun/tinaide/core/config/ClangdSettings.kt:11,73`

---

## 4. 改进方案（按优先级）

### P0：修复 `Future.get()` 阻塞无法被协程 cancel 的问题

**影响**：single-file clangd 冷启动时，IO 线程被挂住最长 6s，放大卡感。

**改法**：

```kotlin
// 当前（LspEditorManager.kt:356）
session.completion(params)?.get(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS)

// 改为（需要 kotlinx-coroutines-jdk8）
import kotlinx.coroutines.future.await
// ...
session.completion(params)?.await()  // 支持协程 cancel，500ms 超时真正生效
```

改动量：5 行，风险低。

---

### P1（新主因修复）：优化 single-file compile_commands 生成与 clangd 冷启动路径

这才是 single-file 卡顿的主因，有几个子方向：

**P1-A：避免不必要的 compile_commands 重新生成**

当前 `SINGLE_FILE` 标记强制每次都走 `needsGeneration = true`，即使 C++ 标准和包 fingerprint 没有变化。
`ensureWithResult()` 已有 fingerprint 校验逻辑（`cppStandard` + `packageFingerprint`），但只在**进入 `ensure` 后**才做校验。

可以在 `prepare()` 阶段提前做轻量校验，命中缓存时直接复用已有目录，跳过 ensure。

**P1-B：文件打开后预热 clangd preamble**

LSP attach 成功后，在后台发送一次静默补全请求（不展示 UI），触发 clangd 提前 parse `#include`，让用户首次手动触发补全时 preamble 已经 warm up。

**注意**：会增加文件打开时的 CPU 占用，低端设备需评估，建议作为可选配置。

---

### P2：给 C/C++ 加静态关键词兜底（降低等待预算 500ms → 140ms）

**目标**：让 `.c/.cpp/.h` 的 local 补全不为空，触发 140ms 快路径。

**改法**：在 `buildLanguageCompletionItems()` 里为 C/C++ 扩展名添加分支，返回内置关键词表（`int/void/return/struct/class/if/while/for` 等约 60-80 个词）。

**注意**：这**不是主因修复**，只能在 clangd 慢时减少 360ms 额外等待，不能解决 preamble 冷启动本身。

---

### P3：验证 background-index 配置生效

**操作**：
1. 设置页确认「后台索引」默认开启。
2. 开启 `devLspClangdStartupLogEnabled`，确认启动参数含 `--background-index`。
3. 重启 clangd 隔几分钟后触发补全，观察跨文件符号是否能补出。

**无需改代码**，仅验证现有配置是否生效。

---

## 5. 不建议做的事

| 方案 | 原因 |
|---|---|
| 在 app 层自建 C/C++ 符号索引 | 需解析 #include 依赖树，维护成本极高，与 clangd 重复且冲突 |
| 加大 LSP 超时（>6s） | 只会让 UI 更卡，不解决根因 |
| 禁用 background-index | 降低跨文件补全质量，方向错误 |
| 把 SINGLE_FILE 改为不做 fingerprint 校验 | 会导致 C++ 标准变更后 clangd 不感知，产生错误补全 |

---

## 6. 优先级与改动影响范围

| 优先级 | 改动 | 文件 | 改动量 | 风险 | 预期收益 |
|---|---|---|---|---|---|
| **P0** | `Future.get()` → `await()` | `LspEditorManager.kt` | ~5 行 | 低 | 解除 IO 线程阻塞，超时真正生效 |
| **P1-A** | prepare 阶段轻量校验跳过 ensure | `CompileDatabaseProvider.kt` | ~20 行 | 中 | 消除不必要的 compile_commands 重生成 |
| **P1-B** | LSP attach 后静默预热 | `LspEditorManager.kt` | ~15 行 | 低 | 首次补全时 preamble 已 warm up |
| **P2** | C/C++ 静态关键词表 | `TinaCodeEditorPage.kt` | ~80 行数据 + 10 行逻辑 | 低 | 减少 360ms 额外等待 |
| **P3** | 验证配置 | 无 | 0 | 无 | 确认 background-index 生效 |

---

## 7. 回归验证建议

1. single-file C/C++ 文件：冷启动后首次触发补全，观察等待时间是否缩短。
2. CMake 项目文件：验证补全流程未受影响。
3. 快速连续输入字符：确认 IO 线程不再长时间阻塞（可通过 Logcat 观察是否有 `lsp_completion_timeout`）。
4. C++ 标准变更（设置页修改）后重启 clangd：确认 compile_commands 正确重生成。
5. 开启后台索引日志：确认 `--background-index` 参数出现在 clangd 启动命令中。
