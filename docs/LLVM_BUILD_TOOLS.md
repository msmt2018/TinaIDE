嵌入式 NDK 工具链方案（设备端 Clang/LLD，最小集）

目标
- 首选“库模式”（in‑process）：仅随 APK 打包 `.so`（libclang-cpp、libLLVM）与最小 sysroot。
- 可选“可执行模式”（exec）：按需提供 `clang/ld.lld` 可执行，但默认不集成到 APK assets（体积与合规风险）。
- 统一 API Level 为 24，控制 APK 体积并提升一致性。

最小内嵌集合（库模式，APK 内）：
- 动态库：`libclang-cpp.so`、`libLLVM-17.so`、`libc++_shared.so`
- 头文件/库（最小 sysroot）：
  - NDK headers（`usr/include`）
  - libc++ headers + 共享库路径占位
  - 目标三元组 lib 目录（`usr/lib/<triple>/24`）含必要 crt 对象（`crtbegin_so.o` 等）

可执行模式（可选，不随 APK 集成）：
- `clang/clang++/ld.lld/llvm-ar/llvm-ranlib/llvm-strip` 打包为 `assets/toolchains/<abi>/bin` 仅在开发/特定环境使用。
- 高版本 Android 可能禁止直接执行，需先解压到内部目录并 `chmod 0700`，不保证兼容性。

打包与解压（库模式）
- 目录：
  - `app/src/main/jniLibs/<abi>/*.so`
  - `app/src/main/assets/sysroot/{usr/include,usr/lib/<triple>/24}`
- 首次启动解压 sysroot 到内部目录 `files/sysroot/` 由 `SysrootInstaller` 负责（如需要）。
 - 同步脚本在复制前会仅清理我们托管的库文件，避免残留且不影响其它三方库。

调用示例（设备端，库模式）
```sh
<files>/toolchains/arm64/bin/clang \
  --target=aarch64-linux-android21 \
  --sysroot=<files>/sysroot \
  -fuse-ld=lld \
  -O2 main.c -o <out>/app

<files>/toolchains/arm64/bin/clang++ \
  --target=aarch64-linux-android21 \
  --sysroot=<files>/sysroot \
  -stdlib=libc++ -fuse-ld=lld \
  -O2 main.cpp -o <out>/app
```

选择策略
- 按 `Build.SUPPORTED_ABIS` 选择 arm64/x86_64 套件
- 优先库模式；仅在开发环境需要时考虑可执行模式

体积控制
- 构建使用 `MinSizeRel`，关闭 tests/examples/tools/terminfo/libedit/curses/zlib/zstd/libxml2/ARCMT/StaticAnalyzer/Assertions
- 仅构建 `.so` 并在容器内 `llvm-strip -S` 后再同步到 APK
- 压缩与校验：MANIFEST + SHA256（保留在 external 产物目录）

许可证合规
- 附带 LLVM/Clang 许可（Apache 2.0 with LLVM exceptions）与 NDK 头文件许可
- 在 `NOTICE` 中标注所含开源组件

错误处理与回退
- 可执行不可运行（机型安全策略特殊）：
  - 友好提示 + 文档引导（切换设备/镜像或使用远程构建）
- sysroot 不完整/链接失败：
  - 标准化错误输出与引导（检查 `--sysroot`、`--target` 与 API level）

增量计划
1. 稳定库模式（in‑process clang -cc1 + LLD 库）
2. 可选接入 `clangd`（体积较大，后置）
3. 可选 `cmake+ninja`（Android 可执行，后置且不随 APK 集成）

与现有分支关系
- 本分支聚焦“设备端嵌入式工具链”，与 `feat/integrate-termux-app` 并行推进。
- 终端能力可不依赖 Termux；仍可保留作为备用交互界面。
