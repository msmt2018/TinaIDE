# Clang Android 执行权限问题修复方案

## 当前结论

TinaIDE 当前采用的是 **Android-hosted LLVM 工具链 + linker64 启动 + LLVM Support 层 patch + CMake shim** 的组合方案。

这不是传统 NDK 交叉编译工具链，也不是完整照搬 Termux 包管理体系。它借鉴了 Termux “在 Android 设备本机运行 clang/cmake/ninja/clangd” 的方向，但分发、安装和执行策略更适合 TinaIDE：工具链从 APK assets 内置分发，安装在 App 私有目录，并通过 App 统一启动器和 LLVM patch 规避 Android 10+、Android 15+ 的 app-private ELF 执行限制。

当前最新验证结果：

- `builtin-0.2.4-patched` 安装校验已匹配 `version + sha256`。
- `clang++ --version` 正常返回 `clang version 22.1.0-rc3`。
- 单文件编译、CMake 配置、Ninja 构建、SDL3 smoke、clangd 启动均通过。
- `Toolchain version mismatch` 已消失。
- 日志中的 `avc: granted { execute }` 是 SELinux 审计，不是拒绝。

## 问题背景

在 Android 10+，尤其 Android 15+ 上，App 私有目录内的原生 ELF 直接执行容易遇到 ROM、SELinux、W^X 相关限制。典型现象包括：

- `error=13, Permission denied`
- `clang++: error: unable to execute command: No such file or directory`
- 外层 clang 能启动，但内部 `cc1`、`lld`、`llvm-*` 再次 `exec()` 时失败
- 通过 `/system/bin/linker64 <elf>` 启动后，LLVM 推导 `InstalledDir` 错误，导致资源目录或工具路径异常

Clang 工作流不是单一进程：

1. App 启动 `clang++` driver。
2. Driver 调用 `cc1` 前端。
3. 链接阶段调用 `lld` / `ld.lld`。
4. CMake/Ninja/clangd 还会间接调用更多工具链二进制。

因此只在 App 最外层包一层 `linker64` 不够，必须同时处理 LLVM 内部子进程执行和 `InstalledDir` 推导。

## 关键约束

### API 28 不能默认 linker64

Android 9 / API 28 不能默认走：

```text
/system/bin/linker64 <elf>
```

OPPO PBBT00 等设备会只打印 linker helper 文本而不执行目标 ELF，最终表现为“命令看起来成功但产物缺失”。

所以 TinaIDE 的统一策略是：

- **API 29+**：默认优先 `linker64` 启动 App 私有目录 ELF。
- **API 28 及以下**：默认保持 direct exec。
- **LLVM 内部 exec**：通过 `TINAIDE_LLVM_WRAP_EXEC_LINKER64=0/1` 与 App 顶层策略同步。
- **诊断 trace**：默认关闭，通过 `TINAIDE_LLVM_EXEC_TRACE=1` 临时开启。

这个门槛必须同时应用在：

- App 顶层 `NativeExecutableRunner`
- CMake/Ninja/clangd 启动链路
- LLVM `Program.inc` 内部执行包装

## 当前落地架构

### 1. App 顶层启动器

文件：`core/common/src/main/java/com/wuxianggujun/tinaide/core/util/NativeExecutableRunner.kt`

职责：

- 统一判断是否应该使用 `linker64`。
- 识别 ELF 与 shebang 脚本，避免把脚本直接交给 linker64。
- 注入工具链环境变量：`PATH`、`COMPILER_PATH`、`TMPDIR`、`HOME`。
- 注入 LLVM patch 控制变量：
  - `TINAIDE_LLVM_WRAP_EXEC_LINKER64`
  - `TINAIDE_LLVM_EXEC_TRACE`
- 输出执行诊断，便于定位 ENOENT、EACCES、路径推导错误。

当前策略：

```kotlin
sdk >= 29 -> linker64
sdk < 29  -> direct exec
```

### 2. LLVM Support 层 patch

当前使用两个 patch：

- `tools/toolchain-patches/llvm-android-linker-execwrap.patch`
- `tools/toolchain-patches/llvm-android-linker-pathfix.patch`

`Program.inc` patch 负责：

- 在 Android 下拦截 LLVM 内部 `ExecuteAndWait` / `exec` 链路。
- API 29+ 或 `TINAIDE_LLVM_WRAP_EXEC_LINKER64=1` 时，将绝对路径 ELF 包装为：

```text
/system/bin/linker64 <real-elf> <args...>
```

- 避免重复包装已经是 `linker64 <elf>` 的命令。
- 设置 `TINA_EXEC__PROC_SELF_EXE=<real-elf>`，供 Path.inc 修正真实可执行路径。

`Path.inc` patch 负责：

- 检测 `/proc/self/exe` 是否指向 `linker` / `linker64`。
- 优先读取 `TINA_EXEC__PROC_SELF_EXE`。
- 修复 clang/clangd 通过 linker64 启动后的 `InstalledDir` 推导。

### 3. CMake / Ninja shim

文件：`core/compile/src/main/java/com/wuxianggujun/tinaide/core/compile/toolchain/ToolchainLinker64ShimManager.kt`

CMake 生成的 compiler launcher、ar、ranlib、ninja 等路径不能总是直接写真实 ELF，否则 CMake/Ninja 内部再启动时可能绕过 App 的启动器。

因此 TinaIDE 生成 shim：

```sh
#!/system/bin/sh
REAL_BIN='<toolchain-bin>'
LINKER64='<system-linker64>'
export TINA_EXEC__PROC_SELF_EXE="$REAL_BIN"
exec "$LINKER64" "$REAL_BIN" "$@"
```

这样 CMake/Ninja 看到的是可执行脚本，脚本再稳定进入 `linker64 -> real ELF`。

### 4. CMake 构建链路

文件：`core/compile/src/main/java/com/wuxianggujun/tinaide/core/compile/cmake/NativeCMakeBuildExecutor.kt`

职责：

- API 29+ 时准备工具链 shim。
- 为 CMake 指定编译器、ar、ranlib、make/ninja 路径。
- 在 shim/linker64 场景下禁用不合适的 tina-exec preload，避免双重拦截。
- 对 CMake try_compile 的 Android-hosted 假阴性做策略处理。

### 5. clangd 链路

文件：`core/lsp/src/main/java/com/wuxianggujun/tinaide/core/lsp/NativeClangdConnectionProvider.kt`

职责：

- clangd 使用与编译链一致的 `NativeExecutableRunner.configureEnvironment()`。
- API 29+ 时通过 `linker64` 启动。
- 同步 `TINAIDE_LLVM_WRAP_EXEC_LINKER64`，让 clangd 内部调用 clang 时不绕过策略。

### 6. 工具链安装校验

文件：`core/ndk/src/main/java/com/wuxianggujun/tinaide/core/ndk/AndroidNativeToolchainManager.kt`

安装是否有效不只看目录是否存在，而是校验：

- `VERSION` 中的 `Toolchain Version`
- assets 中声明的包名
- `.sha256` 中声明的 hash
- `install-metadata.properties` 中记录的安装指纹

这可以避免“包名升版本但包内 VERSION 还是旧值”的问题。

## 与 Termux 的区别

### 相似点

- 都是在 Android 设备本机运行 clang/cmake/ninja/clangd。
- 都需要维护 `PATH`、sysroot、clang resource dir、pkg-config、可执行权限。
- 都会遇到 Android 动态链接器与私有目录执行策略问题。

### 不同点

- Termux 是包管理器生态，工具链安装在 Termux prefix 中。
- TinaIDE 是 APK assets 内置分发，工具链安装在 App 私有目录。
- Termux 可以通过自身 prefix、patched packages、termux-exec 等机制管理环境。
- TinaIDE 必须适配普通 Android App 沙箱，因此需要 `linker64 shim + LLVM Support patch + install metadata`。

### 当前定位

TinaIDE 当前方案不是“复制 Termux”，而是“借鉴 Android-hosted 工具链方向，并针对 App 沙箱做专用执行修复”。这条路是合理的。

## 构建与打包原则

### 必须使用 Docker 构建入口

不要裸跑 WSL。当前工具链构建应使用项目已有 Docker 编译环境：

```powershell
.\tools\run-toolchain-builder.ps1
```

容器内再运行：

```bash
bash scripts/build-and-package-android-toolchain.sh
```

当前关键构建脚本：

- `scripts/build-and-package-android-toolchain.sh`
- `scripts/repack-toolchain-from-existing-package.sh`
- `tools/sync-tina-toolchain-assets.ps1`

### Patch 应可重复应用

构建脚本应先使用：

```bash
git apply --check
git apply --reverse --check
```

再决定应用或跳过 patch，避免 `patch --forward` 半应用污染源码缓存。

### 版本必须一致

以下位置必须一致：

- `app/src/<abi>/assets/tina-toolchain/current.properties`
- `.sha256` 文件第二列包名
- 包内 `VERSION` 的 `Toolchain Version`
- 包名中的 `v<version>`
- 安装后的 `install-metadata.properties`

## 校验命令

### Gradle assets 校验

```powershell
.\gradlew.bat :app:verifyTinaToolchainAssets -Ptina.devAbi=arm64
```

### 工具链包内容校验

```powershell
.\tools\verify-tina-toolchain-package.ps1 -Abi arm64
```

校验目标：

- `current.properties` 格式正确。
- archive 和 sha 文件存在。
- `.sha256` 包含当前 archive 文件名。
- 实际 SHA-256 与声明一致。
- 包内 `VERSION` 与 spec version 一致。
- `bin/clang`、`bin/clang++`、`bin/lld`、`bin/ld.lld`、`clang resource dir` 存在。
- `clang-<major>` 和 `lld` 中包含 TinaIDE patch marker。

### 真机 smoke 测试

建议至少覆盖：

```bash
clang --version
clang -print-resource-dir
clang++ hello.cpp -o hello
./hello
clangd --version
```

TinaIDE 内部诊断页还应覆盖：

- 单文件 C/C++ 编译
- CMake 标准库头文件
- SDL3 CMake 项目
- clangd 补全与诊断

## 当前已知非阻塞项

### SELinux granted execute 日志

日志形态：

```text
avc: granted { execute }
```

这是审计日志，不是拒绝。只要不是 `denied`，不构成工具链失败。

### `-fuse-ld` compile-only warning

如果在 `-c` 编译阶段传入 `-fuse-ld`，clang 会提示 unused argument。功能不受影响，但应该后续优化为只在链接阶段注入。

### 诊断页主线程卡顿

日志形态：

```text
Skipped xxx frames
Davey!
```

这说明诊断页日志刷新或 smoke 执行时 UI 压力偏大。它不是工具链失败，但后续应优化。

## 后续推进顺序

### P0：保持当前工具链链路稳定

- 不再把 API 28 默认切到 linker64。
- 不再绕过 `NativeExecutableRunner` 自行拼启动命令。
- 不再用裸 WSL 改工具链构建路径。

### P1：补测试矩阵

至少验证：

- API 28：默认 direct exec。
- API 29：默认 linker64。
- API 35：clang/cmake/ninja/clangd 全链路通过。

### P2：降低噪声与卡顿

- `-fuse-ld` 只在链接阶段传。
- 降低诊断页面日志刷新频率。
- 将 smoke 测试结果与大段日志分离，减少主线程压力。

## 总结

当前 TinaIDE 工具链方案已经达到可继续推进的状态：

1. App 外层启动策略统一。
2. LLVM 内部 exec 包装已落地。
3. linker64 下 InstalledDir 修复已落地。
4. CMake/Ninja/clangd shim 链路已跑通。
5. assets 安装指纹与 sha 校验已收口。

下一阶段重点不是继续大改工具链，而是补齐验证矩阵、清理日志噪声、降低诊断页卡顿，并把包校验脚本纳入每次重包后的固定流程。

---

文档创建日期：2026-02-18
最后更新：2026-04-27
