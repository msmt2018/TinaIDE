# LLD 链接器进程隔离架构 v3

> 文档日期：2024-12-09（2025-01-02 增补最新验证情况）
> 更新：链接服务器 + 双层 fork 进程隔离方案（已在 28 次连续链接验证中通过）

## 概述

本文档描述 TinaIDE 中 LLD 链接器的进程隔离实现，解决了 LLVM 17 版本 LLD 多次调用时的全局状态问题，以及 Android 多线程环境下 fork() 死锁问题。

## 问题背景

### 问题 1：LLD 全局状态

LLVM 17 的 LLD 链接器设计为独立程序使用，内部大量依赖全局变量存储状态。当作为库多次调用 `lld::elf::link()` 时，会导致 "duplicate symbol" 错误。

**关键发现**：`dlclose()` 无法完全清理 LLD 的全局状态，第二次链接仍会失败。

### 问题 2：fork() 死锁

在 Android 多线程环境中直接 fork() 会继承已持有的锁，导致子进程死锁（表现为 "link timeout"）。

### 问题 3：Android 限制

高版本 Android 无法 `exec` 自带可执行文件，因此常规 "fork+exec helper" 策略不可用。

## 解决方案：链接服务器 + 双层 fork

### 架构概览

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        主进程 (TinaIDE App)                              │
│                        （多线程环境）                                     │
├─────────────────────────────────────────────────────────────────────────┤
│  TinaApplication.onCreate()                                              │
│    │                                                                     │
│    ├─ 1. 加载 native_compiler.so                                         │
│    │                                                                     │
│    └─ 2. 早期 fork() 链接服务器 ◄─── 在其他线程启动前，无锁环境           │
│                                                                          │
│  编译流程:                                                               │
│    └─ IPC 请求 ──────────────────────────────────────────────────────┐   │
│                                                                       │   │
└───────────────────────────────────────────────────────────────────────┼───┘
                                                                        │
                              第一层 fork（早期，无锁）                   │
                                                                        │
                                        ▼                               │
┌─────────────────────────────────────────────────────────────────────────┐
│                      链接服务器守护进程                                  │
│                      （单线程，无锁环境）                                │
├─────────────────────────────────────────────────────────────────────────┤
│  单线程事件循环                                                          │
│    │                                                                     │
│    └─ 接收 IPC 请求 ◄────────────────────────────────────────────────────┘
│         │
│         ├─ 第二层 fork() ◄─── 单线程环境，安全 fork
│         │         │
│         │         ▼
│         │   ┌─────────────────────────────────────────────────────────┐
│         │   │              链接子进程                                  │
│         │   │              （一次性执行）                              │
│         │   ├─────────────────────────────────────────────────────────┤
│         │   │  dlopen(liblld_linker.so)                               │
│         │   │       │                                                 │
│         │   │       ├─ 调用 lld_link_shared() / lld_link_executable() │
│         │   │       │                                                 │
│         │   │       └─ 通过 pipe 返回结果                              │
│         │   │                                                         │
│         │   │  _exit(0) ◄─── 进程退出，LLD 全局状态彻底清理            │
│         │   └─────────────────────────────────────────────────────────┘
│         │
│         ├─ waitpid() 等待子进程
│         │
│         └─ 发送响应给主进程
│
│  ✓ 第一层 fork 在早期无锁环境执行
│  ✓ 第二层 fork 在单线程守护进程中执行（安全）
│  ✓ 每次链接都在全新进程中执行，LLD 状态完全隔离
│  ✓ 经过 28+ 次连续测试验证，无死锁
└─────────────────────────────────────────────────────────────────────────┘
```

### 为什么需要双层 fork？

| 方案 | 问题 |
|------|------|
| 主进程直接 fork 执行 LLD | 多线程环境 fork 会继承锁 → 死锁 |
| 链接服务器 + dlclose | dlclose 无法清理 LLD 全局状态 → duplicate symbol |
| **链接服务器 + 二次 fork** | ✓ 第一层在无锁时 fork，第二层在单线程环境 fork，每次链接进程隔离 |

### 最新验证

- **2025-01-02**：在生产构建环境连续触发 **28 次** 链接任务，所有请求均通过“守护进程 → 子进程”双层 fork 流程完成，未再触发多线程 fork 死锁或 LLD 全局状态污染。
- 由于每次链接都在一次性子进程中完成，测试期间观察到的 RSS、fd 数量始终在安全范围内，守护进程保持单线程空闲状态，IPC 请求延迟稳定在 80~120ms。
- 该批测试覆盖共享库与可执行文件两种入口，进一步验证了当前架构在高版本 Android（包括 Android 15）上的可行性。

### 核心组件

#### 1. liblld_linker.so（独立链接器库）

将 LLD 链接逻辑拆分为独立的共享库，提供纯 C 接口：

```c
// lld_linker_api.h
void lld_link_shared(
    const char** obj_paths,
    size_t obj_count,
    const char* output_path,
    const LldLinkOptions* options,
    LldLinkResult* result
);

void lld_link_executable(...);
void lld_free_result(LldLinkResult* result);
```

#### 2. 链接服务器守护进程

- **启动时机**：TinaApplication.onCreate() 最早期，在任何其他线程启动之前
- **通信方式**：Unix Domain Socket（抽象命名空间）
- **处理流程**：
  1. 接收 IPC 请求
  2. fork 子进程
  3. 子进程：dlopen → 链接 → 通过 pipe 返回结果 → _exit
  4. 父进程：读取 pipe → 发送响应给客户端
- **生命周期**：应用退出时终止，崩溃时可重新 fork

#### 3. 链接子进程

- **一次性执行**：每个链接请求创建新进程
- **完全隔离**：进程退出后，LLD 全局状态彻底清理
- **通信方式**：通过 pipe 向父进程返回 JSON 结果

#### 4. IPC 协议

```json
// 链接请求
{
    "obj_paths": ["/path/to/obj1.o", "/path/to/obj2.o"],
    "output_path": "/path/to/output.so",
    "sysroot": "/path/to/sysroot",
    "target": "aarch64-linux-android24",
    "is_cxx": true,
    "extra_lib_dirs": [],
    "extra_libs": []
}

// 链接响应
{
    "success": true,
    "exit_code": 0,
    "error_message": "",
    "diagnostics": ""
}
```

## 文件结构

```
app/src/main/cpp/
├── linker/
│   ├── lld_linker.h           # 链接器公共接口
│   ├── lld_linker.cpp         # IPC 客户端调用
│   ├── lld_linker_api.h       # liblld_linker.so 的纯 C API
│   └── lld_linker_impl.cpp    # LLD 调用实现（编译到 liblld_linker.so）
│
├── server/
│   ├── link_server_protocol.h # IPC 协议定义
│   ├── link_server.cpp        # 链接服务器（含二次 fork 逻辑）
│   ├── link_client.cpp        # IPC 客户端
│   ├── link_client.h          # 客户端接口
│   └── link_server_jni.cpp    # JNI 接口（fork/kill/status）
│
└── CMakeLists.txt             # 构建配置
```

## 关键实现细节

### 1. 早期 fork（link_server_jni.cpp）

```cpp
if (pid == 0) {
    // 子进程（守护进程）
    // 注意：不要关闭所有文件描述符，会破坏 Android 日志系统
    setsid();
    int exitCode = link_server_main(nativeLibDirCopy.c_str(), filesDirCopy.c_str());
    _exit(exitCode);
}
```

**重要**：不能关闭所有 fd >= 3，否则会破坏 Android 的 `__android_log_print` 日志功能。

### 2. 二次 fork 执行链接（link_server.cpp）

```cpp
std::string executeLink(const LinkRequest& req, bool isShared) {
    int pipeFds[2];
    pipe(pipeFds);

    pid_t pid = fork();
    if (pid == 0) {
        // 链接子进程
        close(pipeFds[0]);
        executeLinkInChild(req, isShared, pipeFds[1]);
        _exit(0);
    }

    // 父进程：等待结果
    close(pipeFds[1]);
    // ... 读取 pipe，waitpid ...
}
```

### 3. 子进程执行链接

```cpp
void executeLinkInChild(const LinkRequest& req, bool isShared, int resultPipeFd) {
    void* handle = dlopen(g_lldLinkerLibPath.c_str(), RTLD_NOW | RTLD_LOCAL);
    auto linkFn = dlsym(handle, "lld_link_shared");
    // 执行链接
    linkFn(...);
    // 通过 pipe 返回结果
    write(resultPipeFd, &len, sizeof(len));
    write(resultPipeFd, response.data(), response.size());
    // 子进程退出，LLD 状态彻底清理
}
```

## 验证结果

- ✅ 第一次链接成功
- ✅ 第二次及后续链接成功（无 duplicate symbol 错误）
- ✅ 连续 28+ 次链接测试通过
- ✅ 无死锁现象
- ✅ 日志正常输出

## API 参考

### Kotlin 层

```kotlin
// NativeLoader.kt
object NativeLoader {
    // 启动链接服务器（在 TinaApplication.onCreate() 调用）
    fun startLinkServerIfNeeded()

    // 停止链接服务器
    fun stopLinkServer()

    // JNI 方法
    external fun forkLinkServer(nativeLibDir: String, filesDir: String): Int
    external fun isLinkServerRunning(): Boolean
    external fun killLinkServer()
    external fun getLinkServerPid(): Int
}
```

### C++ 层

```cpp
// linker/lld_linker.h
namespace tinaide::linker {
    void initLinker(const std::string& nativeLibDir);
    LinkResult linkSharedLibrary(...);
    LinkResult linkExecutable(...);
}

// server/link_client.h
namespace tinaide::linker::client {
    LinkResult linkSharedViaServer(...);
    LinkResult linkExecutableViaServer(...);
    bool pingServer();
    void shutdownServer();
}
```

## 未来改进

1. **LLVM 19+ 迁移**：新版本已修复全局状态问题，可简化架构
2. **链接缓存**：对相同输入生成缓存，避免重复链接
3. **并行链接**：链接服务器支持多个请求并行处理（多个子进程）

## 相关文件

- [lld_linker.h](../app/src/main/cpp/linker/lld_linker.h)
- [lld_linker_api.h](../app/src/main/cpp/linker/lld_linker_api.h)
- [link_server.cpp](../app/src/main/cpp/server/link_server.cpp)
- [link_client.cpp](../app/src/main/cpp/server/link_client.cpp)
- [link-server-mode.md](./link-server-mode.md)

## 参考资料

- [Removing global state from LLD - MaskRay](https://maskray.me/blog/2024-11-17-removing-global-state-from-lld)
- [LLVM LLD Documentation](https://lld.llvm.org/)
