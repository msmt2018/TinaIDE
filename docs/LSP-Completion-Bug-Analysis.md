# LSP 补全功能 Bug 分析报告

## 问题描述

当用户在 C++ 编辑器中输入字符 `s` 时：
- **第一次输入**：能够立即收到 clangd 的补全结果（例如 `std`）并正常展示。
- **删除后再次输入**：UI 不再出现补全面板，需要等待 5 秒后才会超时，完全没有结果返回。

## 问题时间线

### 成功案例（第一次输入 `s`）
```
07:39:16.350 - Sent to clangd: request=7 (completion, position 4:2)
07:39:16.431 - Received from clangd: id=7 (返回 100 个补全项)
07:39:16.449 - UI 显示补全结果 ✅
```

### 失败案例（删除后再次输入 `s`）
```
07:45:49.332 - Sent to clangd: request=58 (completion)
07:45:49.429 - Sent to clangd: request=59 (hover)
…… 等待 5 秒 ……
（没有任何 “Received from clangd” 日志）
07:45:54.332 - completion 超时 ❌
```

## 根本原因分析

### 核心问题：clangd 停止响应
日志表明 **clangd 在短时间内收到大量 `$/cancelRequest` 之后进入卡死状态**，随后所有补全与 hover 请求都得不到响应。

### 问题链路
1. 用户快速输入/删除，IDE 每次键入都会发出新的 completion 与 hover。
2. 只要有新请求，就会立即取消旧请求并向 clangd 发送 `$/cancelRequest`。
3. 大量的 `$/cancelRequest` 叠加导致 clangd 的请求队列混乱，最终停止响应。
4. IDE 继续等待旧请求结果，直至超时，补全体验崩溃。

### 日志证据
```
07:45:49.331 - Sent $/cancelRequest to clangd for id=52
07:45:49.332 - Sent to clangd: request=58 (completion)
07:45:49.429 - Sent $/cancelRequest to clangd for id=55
07:45:49.429 - Sent to clangd: request=59 (hover)
…… 后续没有任何 clangd 响应 ……
```

## 当前代码架构

1. **Kotlin 层（CppNativeCompletionDispatcher）**
   - 每次输入都会取消所有旧的补全协程。
   - 随后重新 launch，调用 `requestCompletionAsync()`.

2. **C++ NativeLspClient**
   - `cancelPendingRequestsForFile` 根据文件/方法枚举未完成的请求，将状态标记为 `CANCELLED` 并发送 `$/cancelRequest` 给 clangd。
   - 生成新的 requestId 放入队列。

3. **LspRequestManager**
   - `dequeue()` 会跳过已取消的请求，只发送仍然有效的条目。

4. **ClangdControlBridge**
   - `handleCancelRequest` 负责向 clangd 转发取消消息，并维护 `pending_requests_`。

5. **clangd**
   - 接收大量 `$/cancelRequest` 后停止响应，IDE 端随之卡住。

## 已尝试的修改

### 1. Kotlin 侧清理旧协程（`CppTreeSitterLanguageProvider.kt`）
```kotlin
completionJobs.values.forEach { it.cancel() }
completionJobs.clear()
completionJobs[key] = scope.launch { … }
```
- 目的：避免 UI 等待已经过期的 requestId。
- 效果：✅ UI 不再收到旧请求的回调。

### 2. C++ 侧取消逻辑（`native_lsp_client.cpp`）
```cpp
void NativeLspClient::cancelPendingRequestsForFile(Method method, uint32_t file_id) {
    // 1. 查找同文件同方法的未完成请求
    // 2. 在 request_manager_ 中标记为 CANCELLED
    // 3. 给 clangd 发送 $/cancelRequest
}
```
- 目的：减少 clangd 的无效工作。
- 效果：❌ cancel 洪泛会直接拖死 clangd。

### 3. `lsp_request_manager.cpp`
```cpp
while (true) {
    RequestEntry entry = pending_queue_.top();
    pending_queue_.pop();
    if (it == request_map_.end() || it->second.status == CANCELLED) {
        continue;
    }
    return entry;
}
```
- 目的：确保被取消的请求不会再被发送。
- 效果：✅ 请求队列只包含有效项。

### 4. `clangd_control_bridge.cpp`
```cpp
void ClangdControlBridge::handleCancelRequest(uint64_t request_id) {
    std::lock_guard<std::mutex> lock(pending_mutex_);
    pending_requests_.erase(request_id);
    sendCancellationToClangd(request_id);
}
```
- 效果：⚠️ 进一步放大 `$/cancelRequest` 风暴，成为根因之一。

## 可能的解决方案

1. **减少向 clangd 发送取消**：只在本地标记取消，不必把所有请求都发到 clangd（但会浪费 clangd 计算）。
2. **引入防抖**：在 Kotlin 层给输入添加 300 ms 防抖，再触发 completion，以减少请求数量。
3. **仅取消排队请求**：`cancelPendingRequestsForFile` 只处理 `PENDING` 状态，不取消已经发出的请求。
4. **健康检查**：对 clangd 增加心跳，检测不响应后自动重启实例。

## 下一步调试建议

1. **确认 clangd 是否崩溃**：检查本地日志或 `stderr`。
2. **临时禁用取消**：观察是否还能复现卡死，从而验证 cancel 洪泛就是根因。
3. **加强日志**：在 `readClangdMessage` 中打印更多诊断信息，定位卡死瞬间。
4. **独立运行 clangd**：手动复现请求/取消流程，剥离 IDE 其他因素。

## 相关文件

- `app/src/main/cpp/lsp/native_client/core/native_lsp_client.cpp`
- `app/src/main/cpp/lsp/native_client/core/clangd_control_bridge.cpp`
- `app/src/main/cpp/lsp/native_client/core/lsp_request_manager.cpp`
- `app/src/main/java/com/wuxianggujun/tinaide/editor/language/cpp/CppTreeSitterLanguageProvider.kt`

## 更新日志

- **2025-12-06**：初步定位为 `$/cancelRequest` 洪泛导致 clangd 卡死。
- **2025-12-06**：尝试方案 1，修改 `handleCancelRequest`，仅在本地移除 pending，不再向 clangd 发送取消。
- **2025-12-07**：继续排查“第三次输入无补全、删除字符后候选仍存在”的问题，发现缓存与请求洪泛的组合造成 UI 状态失真。

## 2025-12-07 补全不稳定的最新根因

### 问题回放
```
2025-12-07 02:43:49.955 CppNativeCompletion  Completion request -> … prefix=''
2025-12-07 02:43:50.207 CppNativeCompletion  Completion result [cache] -> … items=8 preview= short, signed…
2025-12-07 02:43:54.564 SimpleLspClient     Reader: 100 empty reads, 23 pending requests, buffer size=0
2025-12-07 02:43:55.216 SimpleLspService    Request 45 timed out
```

### 根因 1：`NativeLspResultCache` TTL 过长且前缀为空
- 缓存按“文件 + 行 + 列”建索引，TTL 60 秒。输入 `s → shor → ss` 时光标几乎没移动，始终命中旧缓存。
- 当前缀算法得到 `prefix=''` 时，我们选择跳过新请求，却仍然把缓存中的旧结果推给 UI，所以删除 `s` 后候选仍在，新的 `shor` 也只能看到那 8 个旧条目。

### 根因 2：缺少前缀过滤导致列表不收敛
- 缓存返回的 `CompletionResult.items` 直接透传到 `CppTreeSitterLanguageProvider`，并未按最新前缀过滤或重排。
- 用户输入更多字符时，候选列表无法收敛，看上去就像“补全不会筛选”。

### 根因 3：hover/completion 洪泛把 pending 队列撑爆
- 每次键入都会触发 hover 与 completion，并立即取消上一个 hover，`$/cancelRequest` 与新请求交错涌向 clangd。
- 日志 `Reader: 100 empty reads, 23 pending requests` 说明请求队列被淹没，真实的第三次补全迟迟得不到响应，只能一直 timeout。

### 已采取的改进方向
1. **在 `CppNativeCompletion` 中做真实的前缀过滤**：只有满足当前前缀的缓存项才返回，并且当前缀为空时直接隐藏补全面板，避免 UI 留下陈旧候选。（KISS / DRY）
2. **缩短缓存 TTL 并携带前缀校验**：将 TTL 降到 2~3 秒，缓存命中时必须校验 `prefix`，避免旧快照污染新上下文。（YAGNI，避免过度缓存）
3. **串行化请求调度**：在 `NativeLspRequestBridge.scheduleWorker` 把 hover/completion 串行调度，并在 Kotlin 侧合并短时间输入事件，压制 `$/cancelRequest` 洪泛，保持 clangd pending 队列可控。（SOLID 中的 SRP/OCP）

通过这些措施，补全列表能够随前缀实时收敛，在快速输入/删除场景下也不会再出现“第三次必定失败”或“候选不刷新”的情况。
