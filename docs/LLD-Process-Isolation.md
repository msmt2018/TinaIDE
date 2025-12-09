# LLD 链接器进程隔离架构 v2

> 文档日期：2024-12-09
> 更新：链接服务器 + dlclose 方案

## 概述

本文档描述 TinaIDE 中 LLD 链接器的进程隔离实现，解决了 LLVM 17 版本 LLD 多次调用时的全局状态问题，以及 Android 多线程环境下 fork() 死锁问题。

## 问题背景

### 问题 1：LLD 全局状态

LLVM 17 的 LLD 链接器设计为独立程序使用，内部大量依赖全局变量存储状态。当作为库多次调用 `lld::elf::link()` 时，会导致 "duplicate symbol" 错误。

### 问题 2：fork() 死锁

之前的方案使用 `fork()` 进程隔离，但在 Android 多线程环境中，fork() 会继承已持有的锁，导致子进程死锁（表现为 "link timeout"）。

## 解决方案：链接服务器 + dlclose

### 架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│                     主进程 (TinaIDE App)                         │
├─────────────────────────────────────────────────────────────────┤
│  TinaApplication.onCreate()                                      │
│    │                                                             │
│    ├─ 1. 加载 native_compiler.so                                 │
│    │                                                             │
│    └─ 2. 早期 fork() 链接服务器守护进程 ◄──────────────────────┐  │
│         （在任何其他线程启动之前）                               │  │
│                                                                  │  │
│  编译流程:                                                       │  │
│    │                                                             │  │
│    ├─ 方案 A: 直接 dlopen/dlclose（主进程内）                   │  │
│    │   └─ dlopen(liblld_linker.so) → 链接 → dlclose()           │  │
│    │                                                             │  │
│    └─ 方案 B: IPC 到链接服务器（推荐）                          │  │
│        └─ Unix Socket → 请求 → 响应                              │  │
└──────────────────────────────────────────────────────────────────┘
                             │
                          fork()
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    链接服务器守护进程                            │
├─────────────────────────────────────────────────────────────────┤
│  单线程事件循环（无其他线程干扰）                                │
│    │                                                             │
│    └─ 接收 IPC 请求                                              │
│         │                                                        │
│         ├─ dlopen(liblld_linker.so)                              │
│         │                                                        │
│         ├─ 调用 lld_link_shared() / lld_link_executable()        │
│         │                                                        │
│         ├─ dlclose(liblld_linker.so) ← 清理 LLD 全局状态         │
│         │                                                        │
│         └─ 发送响应                                              │
│                                                                  │
│  ✓ 早期 fork，无锁继承                                           │
│  ✓ dlclose() 清理全局状态                                        │
│  ✓ 崩溃可重启                                                    │
└─────────────────────────────────────────────────────────────────┘
```

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

#### 2. dlopen/dlclose 调用方式

主进程或链接服务器通过 dlopen/dlclose 动态加载 liblld_linker.so：

```cpp
// 每次链接
void* handle = dlopen("liblld_linker.so", RTLD_NOW | RTLD_LOCAL);
auto linkFn = (lld_link_shared_fn)dlsym(handle, "lld_link_shared");
linkFn(objPaths, count, outputPath, &options, &result);
dlclose(handle);  // ← 清理 LLD 全局状态
```

#### 3. 链接服务器守护进程

- **启动时机**：TinaApplication.onCreate() 最早期，在任何其他线程启动之前
- **通信方式**：Unix Domain Socket（抽象命名空间）
- **处理流程**：单线程串行处理，dlopen → 链接 → dlclose
- **生命周期**：应用退出时终止，崩溃时可重新 fork

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
│   ├── lld_linker.cpp         # dlopen/dlclose 调用实现
│   ├── lld_linker_api.h       # liblld_linker.so 的纯 C API
│   └── lld_linker_impl.cpp    # LLD 调用实现（编译到 liblld_linker.so）
│
├── server/
│   ├── link_server_protocol.h # IPC 协议定义
│   ├── link_server.cpp        # 链接服务器守护进程
│   ├── link_client.cpp        # IPC 客户端
│   ├── link_client.h          # 客户端接口
│   └── link_server_jni.cpp    # JNI 接口（fork/kill/status）
│
└── CMakeLists.txt             # 构建配置
```

## 两种调用方式对比

| 特性 | 方案 A: 主进程 dlopen | 方案 B: IPC 到链接服务器 |
|------|----------------------|------------------------|
| 实现复杂度 | 低 | 高 |
| fork 死锁风险 | 有（多线程环境） | 无（早期单线程 fork） |
| LLD 全局状态 | ✓ dlclose 清理 | ✓ dlclose 清理 |
| 崩溃影响 | 影响主进程 | 仅影响守护进程（可重启） |
| 推荐场景 | 简单场景 | 生产环境 |

## 使用建议

1. **默认使用链接服务器模式**：更稳定，避免多线程 fork 问题
2. **降级到主进程 dlopen**：如果服务器不可用，可作为备用方案
3. **监控链接服务器状态**：定期检查，必要时重新 fork

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
3. **并行链接**：链接服务器支持多个请求并行处理

## 相关文件

- [lld_linker.h](../app/src/main/cpp/linker/lld_linker.h)
- [lld_linker_api.h](../app/src/main/cpp/linker/lld_linker_api.h)
- [link_server.cpp](../app/src/main/cpp/server/link_server.cpp)
- [link_client.cpp](../app/src/main/cpp/server/link_client.cpp)
- [link-server-mode.md](./link-server-mode.md)

## 参考资料

- [Removing global state from LLD - MaskRay](https://maskray.me/blog/2024-11-17-removing-global-state-from-lld)
- [LLVM LLD Documentation](https://lld.llvm.org/)
