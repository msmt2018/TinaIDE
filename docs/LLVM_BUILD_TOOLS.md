嵌入式 NDK 工具链方案（设备端 Clang/LLD，最小集）

【2025-11 更新：统一“全动态 + sysroot.zip”装载策略】
- 不再将 libc++_shared / libLLVM-17 / libclang-cpp 打入 jniLibs（默认）。
- 改为全部随 sysroot.zip 打包到 APK 的 assets，应用首启后台解压到 files/sysroot 后加载。
- jniLibs 仅保留 JNI 桥库（native_compiler）。
- 设备端链接/运行均从 files/sysroot 搜索依赖（-L + LD_LIBRARY_PATH + rpath=$ORIGIN）。

目标
- 首选“库模式”（in‑process）：仅随 APK 打包 `.so`（libclang-cpp、libLLVM）与最小 sysroot。
- 可选“可执行模式”（exec）：按需提供 `clang/ld.lld` 可执行，但默认不集成到 APK assets（体积与合规风险）。
- 统一 API Level 为 24，控制 APK 体积并提升一致性。

最小内嵌集合（库模式，APK 内）：
- 仅 assets 中的 `sysroot.zip`（高压缩，默认排除所有 `*.a`）
- sysroot 布局（关键路径）：
  - 链接期：`usr/lib/<triple>/<api>/`（含 crt、`libc.so`、`libm.so`、`liblog.so`、`libandroid.so`、`libc++_shared.so`）
  - 运行期：`usr/lib/<triple>/runtime/`（含 `libLLVM-17.so`、`libclang-cpp.so` 等 IDE 运行库）
  - 头文件：`usr/include`、`usr/include/c++/v1`、`lib/clang/17/include`

可执行模式（可选，不随 APK 集成）：
- `clang/clang++/ld.lld/llvm-ar/llvm-ranlib/llvm-strip` 打包为 `assets/toolchains/<abi>/bin` 仅在开发/特定环境使用。
- 高版本 Android 可能禁止直接执行，需先解压到内部目录并 `chmod 0700`，不保证兼容性。

打包与解压（库模式）
- 资产：`app/src/main/assets/sysroot.zip`（默认使用 `tools/sync-llvm-build.ps1 -SysrootMode zip` 生成）
- 首次启动后台解压：`SysrootInstaller.ensureInstalled()` 仅支持 zip 解压到 `files/sysroot/`
- 解压后修正可执行权限：`usr/bin/*`（例如 ninja/cmake）自动 `+x`

调用示例（设备端，库模式）
```sh
<files>/sysroot/usr/bin/ninja   # 若已打包编译器工具

# in-process 路径示例略。链接时 -L 已指向 <files>/sysroot/usr/lib/<triple>/<api> 与 <triple> 根
```

选择策略
- 按 `Build.SUPPORTED_ABIS` 选择 arm64/x86_64 套件
- 统一“全动态”：不打包 C++ 静态库，链接 -lc++ → `libc++_shared.so`
- 运行时搜索：LD_LIBRARY_PATH 指向 `<files>/sysroot/usr/lib/<triple>/<api>`、`<files>/sysroot/usr/lib/<triple>/runtime`、`<files>/sysroot/usr/lib/<triple>`；并保留 `-Wl,-rpath,$ORIGIN` 兜底（仅从 sysroot 加载，不再回退 jniLibs）

体积控制
- sysroot.zip 默认排除 `*.a`（静态库），只保留动态库
- 构建使用 `MinSizeRel`，关闭 tests/examples/tools/terminfo/libedit/curses/zlib/zstd/libxml2/ARCMT/StaticAnalyzer/Assertions
- 运行库置于 sysroot/runtime，统一来源，避免 jniLibs 冗余

许可证合规
- 附带 LLVM/Clang 许可（Apache 2.0 with LLVM exceptions）与 NDK 头文件许可
- 在 `NOTICE` 中标注所含开源组件

错误处理与回退
- sysroot.zip 缺失或损坏：`SysrootInstaller` 直接抛错，安装失败早暴露
- sysroot 不完整/链接失败：链接前预检（crt/系统桩库/`libc++_shared.so`）
- 运行期 .so 缺失：NativeLoader 仅从 sysroot 绝对路径加载；缺失即报错（不再回退 jniLibs）

原生链接实现细节（重要）
- CMake 为 `native_compiler` 追加 `-Wl,--allow-shlib-undefined`，允许由运行期共享库解析剩余符号（避免 -z defs 约束）
- LLD 使用静态库（`liblldCommon.a`,`liblldELF.a`）链接进 `native_compiler.so`（只在构建期使用，不打包进 APK）
- 构建 LLD 时启用 `-femulated-tls`，避免对 `__tls_get_addr` 的依赖，提升在 JNI 共享库中的可链接性

IDE 编译/链接流程
- 单文件：`emitObj()` → `linkExe()` → 运行
- 多文件：枚举源 → 逐个 `emitObj()` → `linkExeMany()` 一次性链接 → 运行

增量计划
1. 稳定库模式（in‑process clang -cc1 + LLD 库）
2. 可选接入 `clangd`（体积较大，后置）
3. 可选 `cmake+ninja`（Android 可执行，后置且不随 APK 集成）

与现有分支关系
- 本分支聚焦“设备端嵌入式工具链”，与 `feat/integrate-termux-app` 并行推进。
- 终端能力可不依赖 Termux；仍可保留作为备用交互界面。

附录：脚本与参数（2025-11）

- 构建 LLVM/Clang（共享库）+ LLD（静态库，构建期使用）
  - `./docker/llvm-build/build-local.ps1 -Abi <arm64-v8a|x86_64> -ApiLevel <26|28> [-Mode incremental|reconfigure|clean]`
  - 输出：`build-output/<abi>/libs/<abi>/libLLVM-17.so, libclang-cpp.so, liblldCommon.a, liblldELF.a` 与 `build-output/<abi>/sysroot/...`
  - 说明：
    - 默认 `-Mode incremental` 复用缓存，速度最快
    - `-Mode reconfigure` 仅清 CMakeCache 强制重配
    - `-Mode clean` 全量清理构建目录后重建

- 构建 Ninja（可执行，禁用 posix_spawn 以兼容 API 26/27）
  - `./docker/llvm-build/build-tools.ps1 -Abi <abi> -ApiLevel 26`
  - 关键：脚本内使用 `-DHAVE_POSIX_SPAWN=0`，避免 API 28+ 的符号依赖
  - 输出：`build-output/<abi>/tools/bin/ninja`

- 同步/打包到应用
  - `./tools/sync-llvm-build.ps1 -Abi <abi> [-SysrootMode zip|mirror] [-ToolBinSource <dir>] [-CopyLibcxxToJni $false] [-CopyLlvmToJni $false]`
  - 默认：`-SysrootMode zip`，在 `app/src/main/assets/sysroot.zip` 生成压缩包（排除 `*.a`）
  - 自动注入：若未指定 `-ToolBinSource`，脚本尝试使用 `build-output/<abi>/tools/bin` 注入到 `sysroot/usr/bin`
  - jniLibs：默认只保留 JNI 桥库；会清理 `libc++_shared.so`、`libLLVM*.so`、`libclang-cpp*.so`、所有 `*.a`

- 应用首次启动
  - `SysrootInstaller.ensureInstalled()` 仅解压 `assets/sysroot.zip` → `files/sysroot`，并对 `usr/bin/*` 赋予可执行权限
  - `NativeLoader`：先 `System.load(<files>/sysroot/.../libc++_shared.so)`，再加载 `libLLVM-17.so`、`libclang-cpp.so`（仅 sysroot，若缺失直接报错）
