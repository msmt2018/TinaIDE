# Native 工具链双包对照实验方案

> 更新日期：2026-04-26

## 实施进度

> 最近更新：2026-04-27 01:44（Asia/Shanghai）

已确认的方向：

1. **继续只维护两个工具链包位**
   - 包 A：`patched`，也就是当前原来的修改后工具链，继续作为稳定基线。
   - 包 B：`upstream-exechook`，作为 `tina-exec` hook 实验包位。
   - 不新增第三个 `upstream-pathfix-exechook` 包位，避免资产、UI、诊断和回归矩阵无意义膨胀。
2. **第二个包位不再宣称“完全原版 clang”**
   - `upstream-exechook` 的准确定位改为：`tina-exec hook + 随测试进行的小范围二进制修复`。
   - 允许对 clang/工具链二进制做最小必要修改，例如修复 `Path.inc` 自定位。
   - 不允许重新引入“全部 exec 强制 linker64”的大范围源码逻辑。
3. **当前日志结论已经收敛**
   - `patched` 在 `log/log.txt:73` 输出正确 `InstalledDir`。
   - `patched` 在 `log/log.txt:75` 显示 `Path.inc` 检测到 `/proc/self/exe=/apex/.../linker64` 后回退真实 `argv0`。
   - 当前纯 upstream 的 `upstream-exechook` 在 `log/log.txt:657-668` 虽然启用了 `tina-exec`，但 `InstalledDir` 错到 `/apex/com.android.runtime/bin`。
   - 当前纯 upstream 的 `upstream-exechook` 在 `log/log.txt:700-704` 出现 `size_t` unresolved，根因是 resource dir 跟着 `InstalledDir` 错到 `/apex/.../lib/clang/22`。
   - 当前纯 upstream 的 `upstream-exechook` 在 `log/log.txt:921-923` 把 cc1 拼成 `"/apex/.../linker64" -cc1`，最终报 `expected absolute path: "-cc1"`。
   - 结论：**第二包需要最小 `Path.inc` 自定位修复；不能继续拿完全未改 LLVM 的二进制当可用候选。**
4. **构建脚本已支持小范围修复开关**
   - 已把原来的 LLVM 补丁拆成两个独立文件：
     - `tools/toolchain-patches/llvm-android-linker-pathfix.patch`
     - `tools/toolchain-patches/llvm-android-linker-execwrap.patch`
   - `scripts/build-and-package-android-toolchain.sh` 已新增：
     - `APPLY_LLVM_ANDROID_PATH_FIX_PATCH=0|1`
     - `APPLY_LLVM_ANDROID_EXEC_WRAP_PATCH=0|1`
   - 旧的 `APPLY_LLVM_ANDROID_EXEC_PATCH=0|1` 保留为兼容 umbrella 开关。
   - 构建脚本已按 `LLVM_PATCH_PROFILE=pathfix<0|1>-execwrap<0|1>` 隔离 LLVM 源码目录。
   - 构建脚本已按 `PACKAGE_VARIANT` 隔离 Android target build 目录，避免两个包位复用已打补丁构建缓存。

当前 assets sha256：

- `patched.tar.xz`：`da32720642a0d85ce410a54b70da1c26b818acc358451aa730d62bd91e3fa73f`
- `upstream-exechook.tar.xz`：`bf225fda6be604f75155d5c4707795821c9af0564500835fb36cdc3fb70e07a9`

注意：当前 assets 里的 `upstream-exechook` 已覆盖为快速热补丁验证包。它复用旧包内容，只修改 `bin/clang-22` 的 `/proc/self/exe` 自定位字符串以强制回退 `argv0`，用于快速验证日志中的 `InstalledDir`/resource dir 问题是否消失；正式源码级 `Path.inc` 重建产物仍需后续替换。

进行中：

- 把第二包从“纯 upstream 对照”调整为“exec hook 实验包位”。
- 第二包允许小范围二进制修复，但每一次修复都必须记录原因、影响范围和真机验证结论。
- 2026-04-26 14:02：已停止默认全工具/全包构建路径，当前改为只跑 Android target 的 `clang` 单目标；由于可复用的完整 target build cache 已被清理，冷缓存下仍需补齐 `clang` 依赖图（dry-run 约 2845 步），这不是默认全包目标，也不是理想增量状态。
- 2026-04-26 14:28：冷编译验证因耗时过长已中止，另生成一个不覆盖 assets 的快速热补丁包：复用旧 `upstream-exechook` 包内容，仅将 `bin/clang-22` 中 `/proc/self/exe` 字符串替换为不存在的 `/proc/self/ex_`，强制 LLVM 回退 `argv0` 推导路径；产物位置 `build/tina-toolchain/hotpatch-release/`，sha256 `bf225fda6be604f75155d5c4707795821c9af0564500835fb36cdc3fb70e07a9`。该包仅用于快速真机验证，不等同于最终源码级 `Path.inc` 重建产物。
- 2026-04-26 14:40：已按用户要求将上述快速热补丁包覆盖到 `app/src/arm64/assets/tina-toolchain/`，同步覆盖 tar 与 sha256；当前 assets 中 `upstream-exechook` sha256 为 `bf225fda6be604f75155d5c4707795821c9af0564500835fb36cdc3fb70e07a9`。
- 2026-04-26 15:11：用户卸载 APK 后重新安装并复测 `log/log.txt`。快速热补丁包已被安装并生效，`clang++ --version` 的 `InstalledDir` 指向 `builtin-0.2.3-upstream-exechook/bin`；旧的 `/apex/com.android.runtime/lib/clang`、`expected absolute path`、`unable to execute command`、`clang frontend command failed`、`fatal error:` 均未复现。单文件编译产物生成成功，`Single-file build finished: exitCode=0`。
- 2026-04-26 15:20：新日志剩余失败点不是 clang 编译失败，而是 clangd smoke 的补全断言失败；clangd 自身通过 `upstream-exechook/bin/clangd` 启动，但加载的 `compile_commands.json` 仍指向默认 `builtin-0.2.3/bin/clang++` 和默认 resource dir。结论：正式源码级 `Path.inc` 重建前，必须先修应用侧 `compile_commands` 的 `toolchainId` 贯通与归一化逻辑。
- 2026-04-26 16:17：已修复应用侧 `CompileDatabaseProvider` 的 `toolchainId` 贯通：生成、复用、归一化 `compile_commands.json` 时都使用所选工具链；项目 smoke 的 CMake/native clangd 路径也传入所选 `toolchainId`。本地验证 `:core:lsp:compileDebugKotlin :app:compileArm64DebugKotlin` 通过，`:app:assembleArm64Debug` 通过。测试 APK：`app/build/outputs/apk/arm64/debug/app-arm64-v8a-debug.apk`，sha256 `3e6ee59e3cf4a9459739244076c6fb6ca7093e7f9ae9413d8f655d800b38efcf`。
- 2026-04-26 18:27：清理 arm64 assets 中未被任何当前 spec 引用的历史默认包：删除 `tinaide-toolchain-aarch64-v0.2.3.tar.xz` 与 `tinaide-toolchain-aarch64-v0.2.3.sha256`。当前 arm64 assets 只保留两个包位：`patched` 与 `upstream-exechook`。已执行 `:app:verifyTinaToolchainAssets --rerun-tasks`，结果通过。
- 2026-04-26 18:36：虽然用户随后表示无需重新打包，但此前已启动的 `:app:assembleArm64Debug` 重试最终成功。新 APK 为 `app/build/outputs/apk/arm64/debug/app-arm64-v8a-debug.apk`，大小约 `415.86 MB`，sha256 `1a798c2321e4859765dba8f1562d6ed9ba075245d6df2c99c5974e0b19227a72`；包内 `assets/tina-toolchain/` 已确认只包含 `patched` 与 `upstream-exechook` 两个 tar.xz。
- 2026-04-26 19:45：重新阅读用户重新编译、安装、运行后生成的 `log/log.txt`（约 `287,936 bytes`，`913` 行，日志时间 `19:27:49` 到 `19:28:28`）。旧根因没有回归：`/apex/com.android.runtime/lib/clang`、`expected absolute path`、`unable to execute command`、`clang frontend command failed`、`resource-dir=/apex`、`fatal error:`、`error:` 计数均为 `0`；`compile_commands.json` 已指向 `builtin-0.2.3-upstream-exechook/bin/clang++` 和 `builtin-0.2.3-upstream-exechook/lib/clang/22`；单文件编译两次均 `exitCode=0`。当前剩余失败收敛为两类：`single_file_std_headers` 的 clangd 补全断言未命中（clangd 实际返回 `3` 个 Sema 补全，需要打印/校准实际 labels 与补全位置），以及 `cmake_std_headers`/`sdl3_std_headers` 的 CMake 构建失败（configure 成功，ninja 编译阶段通过 `/system/bin/sh <toolchain-shims/.../clang++>` 调用后子命令退出 `1`，日志未展开真实 clang stderr）。结论：现在不应马上做正式源码级 `Path.inc` 重建，先修 CMake shim/exec 调用链和 smoke 补全断言可观测性。
- 2026-04-26 20:32：继续排查 CMake 构建阶段是否为 `shell shim` 与 `tina-exec` 冲突。代码确认当前失败链路同时启用了两层机制：外层 CMake build 进程 `tinaExec=enabled`、`shimEnv=enabled`，而 `build.ninja` 内部编译命令又是 `/system/bin/sh <toolchain-shims/.../clang++>`，shim 再 `exec /system/bin/linker64 <real clang++>`。为避免继续靠猜，已加入受控诊断：shim 脚本支持 `TINAIDE_TOOLCHAIN_SHIM_TRACE=1` 时输出 `LD_PRELOAD`、`TINA_EXEC__SYSTEM_LINKER_EXEC__MODE`、`TINA_EXEC__PROC_SELF_EXE`、`TINAIDE_LLVM_WRAP_EXEC_LINKER64`；项目 smoke 在启用 `tina-exec` 时自动打开该 trace；若 CMake+ninja 通过 shell shim 静默失败且没有真实 `fatal error:`/`error:`/`permission denied` 等编译器诊断，会自动清理 `LD_PRELOAD`/`TINA_EXEC` 后以 `shim-only` 方式重试一次，并把两轮输出合并进报告。判定规则：如果 `shim-only` 重试通过，基本确认是 shim + `tina-exec` 组合冲突；如果仍失败，新日志会带出 shim 环境与真实失败阶段。另已把 smoke 的 `failureDetail` 写入 logcat，下一次日志应能看到 clangd 实际补全 labels 或 CMake retry 详情。定向验证 `:core:compile:testDebugUnitTest --tests ...NativeCMakeBuildExecutorConfigTest :core:compile:compileDebugKotlin :app:compileArm64DebugKotlin` 通过。
- 2026-04-26 20:38：为包含上述诊断逻辑重新执行 `:app:assembleArm64Debug`，构建成功。新 APK：`app/build/outputs/apk/arm64/debug/app-arm64-v8a-debug.apk`，大小约 `415.87 MB`，sha256 `1a883b943b91b36d16f0de8da6d1eeaff81218a0f3a83badea9c59ab12643f41`；包内 `assets/tina-toolchain/` 再次确认只有 `patched` 与 `upstream-exechook` 两个 `.tar.xz`。
- 2026-04-26 20:45：补充本轮排查交付记录：这次没有再修改工具链二进制，也没有恢复第三包；只在 App 侧加入“可判定冲突”的诊断闭环。核心改动包括 `ToolchainLinker64ShimManager` 的 `TINAIDE_TOOLCHAIN_SHIM_TRACE`、`NativeCMakeBuildExecutor` 的 `tina-exec` 关闭重试、项目 smoke 自动启用 shim trace、以及 `CompilerDiagnostics` 将 `failureDetail` 写入 logcat。下一次真机复测需要重点搜索 `TinaIDE shim env`、`CMake build retry without tina-exec`、`Smoke failure detail` 三类日志，用来判断 CMake 失败是否确认为 `shell shim + tina-exec/LD_PRELOAD` 组合冲突。
- 2026-04-27 00:26：重新阅读用户最新运行后的 `log/log.txt`（约 `231,555 bytes`、`683` 行，文件写入时间 `2026-04-26 23:46:03`）。旧问题没有回归：`/apex/com.android.runtime/lib/clang`、`expected absolute path`、`unable to execute command`、`clang frontend command failed`、`resource-dir=/apex`、`fatal error:` 计数均为 `0`。新日志把剩余问题收敛为两点：一是 CMake build 首轮仍可触发 `shell shim + tina-exec` 静默失败判定，但关闭 `tina-exec` 后的重试失败变成 `Permission denied`，并明确显示 `Make command was: .../builtin-0.2.3-upstream-exechook/bin/ninja -j 1`，说明根因已不是 clang `Path.inc`，而是 `cmake --build` 内部直接 exec app 私有目录真实 `ninja`；二是 clangd 实际返回了 ` emplace`、` emplace_back`、` empty`，只是 label 带前导空格导致 smoke 精确匹配误判。应用侧修复策略：Ninja 生成器的 build/clean 阶段不再走 `cmake --build`，改由 `NativeExecutableRunner` 直接启动 `ninja -C <buildDir> -j <jobs> [target]`，让真实 `ninja` 也走外层 `linker64` 包装；Makefiles 仍保留 `cmake --build`。同时 smoke 对 completion labels 先 `trim()`/去重后匹配，并保留原始 labels 与归一化 labels 日志，方便继续排查。
- 2026-04-27 00:39：已基于 direct ninja 与 completion label 归一化修复重新执行 `:app:assembleArm64Debug`，构建成功（`BUILD SUCCESSFUL in 6m 37s`）。新 APK：`app/build/outputs/apk/arm64/debug/app-arm64-v8a-debug.apk`，大小 `436,106,945 bytes`，sha256 `e90d6d562f55de0eeb701a1fb228533cf7b82c33093e9487cf39565b431bdb06`。包内 `assets/tina-toolchain/` 已确认只有两个 `.tar.xz`：`patched` 与 `upstream-exechook`，没有恢复第三包或历史默认包。
- 2026-04-27 01:44：重新阅读用户最新 `log/log.txt`（约 `277,396 bytes`，写入时间 `2026-04-27 01:21:58`）。结论继续收敛：旧 `/apex`/`resource-dir`/`expected absolute path` 没有回归，`Permission denied` 与 `Make command was` 也已变为 `0`，说明 direct ninja 已生效；日志明确出现 `Building CMake project in: build (direct ninja)` 与 `/system/bin/linker64 .../bin/ninja -C ... -j 1`。剩余 CMake 失败发生在链接阶段：`[3/3] Linking CXX shared library` 后，shell shim 输出 `LD_PRELOAD=<unset>`、`TINA_EXEC...=<unset>`、`TINAIDE_LLVM_WRAP_EXEC_LINKER64=1`，随后 clang 报 `unable to execute command: No such file or directory`。这说明在 `upstream-exechook` 模式下继续把 CMake 编译器写成 `/system/bin/sh <toolchain-shims/.../clang++>` 会绕开 `tina-exec`，而该实验包又没有 LLVM `Program.inc` execwrap，所以 clang 内部执行 `ld.lld` 仍失败。修复策略调整为：启用 `useRecommendedTinaExec` 时，CMake 配置直接使用真实 `clang/clang++/llvm-ar/llvm-ranlib` 路径，不再写入 shell shim，不再 patch ninja 中的工具命令，也关闭 build 进程的 shim env；由 `tina-exec` 负责 ninja -> clang -> ld.lld 子进程链路。单文件 smoke 也补齐 completion label 归一化，修复前一次只改到 CMake smoke、漏掉 single-file smoke 的问题。`2026-04-27 01:33` 已验证 `:core:compile:compileDebugKotlin :app:compileArm64DebugKotlin` 通过。

待完成：

1. 源码级重建 `upstream-exechook`：开启 `Path.inc + Program.inc execwrap`，关闭 CMake exec/linker64 源码补丁。
2. 覆盖 assets 中 `upstream-exechook` tar/sha256，保持 arm64 Debug APK 只有 `patched`、`upstream-exechook` 两个包位。
3. 真机复测 CMake/SDL3 smoke，重点确认链接阶段不再出现 `clang++: error: unable to execute command`。
4. 观察是否出现 `[TinaIDE][Program.inc] wrap exec via linker64`，并确认 `single_file_std_headers` 仍保持成功。
5. 真机重新测试 `patched` 与新的 `upstream-exechook` 两份报告。

## 背景

当前 TinaIDE 默认使用 Android 原生工具链：

- App 首次安装/初始化时，从 assets 解压 `tina-toolchain`。
- `android-sysroot` 单独作为 ABI 资产安装。
- 编译、格式化、clangd、CMake/Ninja/Make 等工具直接在 Android 设备上运行。

历史方案对 LLVM/CMake 做了源码补丁，核心是让 `clang -> cc1/lld/llvm-*` 以及 CMake
内部子进程在需要时通过系统 linker/linker64 启动。

本轮日志证明：完全不改 LLVM 的 clang 被 linker64 启动时，会从 `/proc/self/exe` 推导安装目录，
导致 `InstalledDir`、resource dir、cc1 路径全部错到 `/apex/com.android.runtime`。

因此现在不再追求“第二包完全原版”。新的策略是：

```text
[patched]
  └── 原来的修改后工具链，稳定基线

[upstream-exechook]
  └── tina-exec hook 实验包位，可叠加小范围二进制修复
```

## 实验目标

1. 保留当前已验证的 `patched` 工具链，作为稳定基线。
2. 第二包继续使用 `upstream-exechook` 包位，避免新增第三套资产和 UI 分支。
3. 第二包通过 `tina-exec` 覆盖内部子进程启动链路。
4. 第二包允许最小必要二进制修改，优先解决当前真机日志暴露的问题。
5. 每次小修都要用诊断报告验证，确认是否提升兼容性，而不是扩大硬编码范围。

## 当前实验范围

本阶段只做 **arm64-v8a / aarch64**。

暂不处理：

- x86_64 工具链包。
- 多 ABI 对照矩阵。
- 第三个工具链包位。
- 发布渠道切换。
- 自动灰度与线上实验配置。

原因：当前目标是验证兼容性修复是否有效。两个包位已经足够：一个稳定基线，一个实验槽。

## 双包模型

### 包 A：patched-native

定位：当前稳定方案。

特征：

- 使用当前原来的修改后工具链。
- 可以包含 LLVM `Path.inc` 自定位修复。
- 可以包含 LLVM `Program.inc` 内部 exec 包装。
- 可以包含 CMake Android exec/linker64 源码补丁。
- 作为所有对照测试的稳定基线。

包名：

```text
tinaide-toolchain-aarch64-v<version>-patched.tar.xz
```

### 包 B：upstream-exechook

定位：`tina-exec` 实验包位，而不是绝对纯原版包。

特征：

- 使用 `tina-exec` 注入 so，验证内部 `exec*`/子进程 hook 链路。
- 允许随真机测试进行小范围二进制修复。
- 当前需要加入 LLVM `Path.inc` 自定位修复与 `Program.inc` 内部 execwrap。
- `Program.inc` execwrap 只用于 LLVM 内部子进程，且受 `TINAIDE_LLVM_WRAP_EXEC_LINKER64` 控制。
- 不开启 CMake exec/linker64 源码补丁。
- 不在 SDK 28 上重新写死“全部 exec 强制 linker64”。

包名保持不变：

```text
tinaide-toolchain-aarch64-v<version>-upstream-exechook.tar.xz
```

说明：

- 名字里的 `upstream` 保留为历史包位名称，不再表示“100% 未修改 LLVM”。
- 后续如果再出现小修，也继续落在这个包位，并在本文档记录修复原因。

## 构建命令建议

### patched 基线

```bash
PACKAGE_VARIANT=patched \
APPLY_LLVM_ANDROID_EXEC_PATCH=1 \
APPLY_CMAKE_ANDROID_EXEC_PATCH=1 \
bash scripts/build-and-package-android-toolchain.sh
```

### upstream-exechook 实验包位：Path.inc + Program.inc execwrap + tina-exec

```bash
PACKAGE_VARIANT=upstream-exechook \
TOOLCHAIN_VERSION=0.2.3 \
APPLY_LLVM_ANDROID_EXEC_PATCH=0 \
APPLY_LLVM_ANDROID_PATH_FIX_PATCH=1 \
APPLY_LLVM_ANDROID_EXEC_WRAP_PATCH=1 \
APPLY_CMAKE_ANDROID_EXEC_PATCH=0 \
ANDROID_TOOLS_ROOT=/workspace/build/tina-toolchain/android-tools-upstream-exechook \
bash scripts/build-and-package-android-toolchain.sh
```

说明：

- `APPLY_LLVM_ANDROID_EXEC_PATCH=0` 关闭旧 umbrella，避免误开完整 LLVM 补丁。
- `APPLY_LLVM_ANDROID_PATH_FIX_PATCH=1` 打开 `Path.inc` 自定位修复。
- `APPLY_LLVM_ANDROID_EXEC_WRAP_PATCH=1` 打开 `Program.inc` 内部 execwrap，解决 `clang++ -> ld.lld` 链路。
- `APPLY_CMAKE_ANDROID_EXEC_PATCH=0` 明确关闭 CMake 源码级 linker64 包装。
- `ANDROID_TOOLS_ROOT` 对实验包独立，避免 CMake/Ninja/Make 复用旧源码补丁缓存。

### 增量构建纪律

- 有完整 `TARGET_BUILD` 缓存时，`Path.inc`/`Program.inc` 级别小修只应触发相关对象重编和受影响二进制重链接；禁止无理由开启 `FORCE_RECONFIGURE=1` 或换干净 `ROOT` 触发冷构建。
- 没有完整 `TARGET_BUILD` 缓存时，第一次 `ninja clang` 仍需要补齐 `clang` 依赖图；这只能称为“单目标冷构建”，不能误报成真正增量。
- 快速验证 `Program.inc` 时优先使用 `SKIP_STAGE_AND_PACKAGE=1 NINJA_TARGETS="clang,lld"`，或直接在已配置的 Android target build 目录执行 `ninja -C <TARGET_BUILD> clang lld`。
- 正式覆盖 assets 前必须由脚本重新 stage/package 第二包，记录 marker、sha256 与包内仍只有 `patched`/`upstream-exechook` 两个 tar。

## 资产登记

继续保持两个 variant spec：

```text
assets/tina-toolchain/
├── current.properties
├── variants/
│   ├── patched.properties
│   └── upstream-exechook.properties
├── tinaide-toolchain-aarch64-v0.2.3-patched.tar.xz
├── tinaide-toolchain-aarch64-v0.2.3-patched.sha256
├── tinaide-toolchain-aarch64-v0.2.3-upstream-exechook.tar.xz
└── tinaide-toolchain-aarch64-v0.2.3-upstream-exechook.sha256
```

`upstream-exechook.properties` 保持不变：

```properties
version=0.2.3
arch=aarch64
variant=upstream-exechook
full=tinaide-toolchain-aarch64-v0.2.3-upstream-exechook.tar.xz
sha256=tinaide-toolchain-aarch64-v0.2.3-upstream-exechook.sha256
```

## 预期对照点

两套包需要跑同一批测试，重点比较：

- `clang --version`、`clang++ --version` 是否可启动。
- `InstalledDir` 是否指向工具链自己的 `bin` 目录。
- `clang -print-resource-dir` 是否指向工具链自己的 `lib/clang/22`。
- `clang++` 编译单文件是否成功。
- `clang++` 链接可执行文件是否成功。
- `-###` 输出里的 cc1 是否不再是 `/apex/.../linker64 -cc1`。
- `ld.lld` 是否由工具链目录解析。
- `clangd` 是否可启动并正确解析 resource/sysroot。
- `clang-format` 是否可启动。
- CMake configure 是否成功。
- CMake + Ninja 构建是否成功。
- Make 构建是否成功。
- 失败时 stderr 是否能明确区分：外层 linker64、`Path.inc` 自定位、`Program.inc` 包装、`tina-exec` hook。

## upstream-exechook 当前验收标准

重建后的第二包必须满足：

1. `clang++ --version` 成功，且 `InstalledDir` 是工具链目录，不是 `/apex/com.android.runtime/bin`。
2. `clang -print-resource-dir` 指向工具链自己的 `lib/clang/22`。
3. `strings clang-22` 可以包含 `[TinaIDE][Path.inc]` 与 `[TinaIDE][Program.inc]`。
4. `strings clang-22` 应包含 `TINAIDE_LLVM_WRAP_EXEC_LINKER64`，用于受控启用 LLVM 内部 execwrap。
5. 诊断日志可以出现 `[TinaIDE][Path.inc]`，用于确认 linker64 启动下的 clang 自定位。
6. 链接阶段应出现或允许出现 `[TinaIDE][Program.inc] wrap exec via linker64`，用于确认 `clang++ -> ld.lld` 被包装。
7. `-###` 输出中 cc1 不应再是 `/apex/.../linker64 -cc1`。
8. 仅编译测试不应再出现 `using ::size_t _LIBCPP_USING_IF_EXISTS` unresolved。
9. 完整编译 + 链接应生成目标可执行文件。
10. 如果 CMake/Ninja 失败，先区分 App 侧 shell shim、LLVM `Program.inc` execwrap、`tina-exec` hook 三条链路，不再回到 SDK 判断硬编码。

## 小范围修复记录规则

第二包允许继续小修，但必须满足：

- 每次只解决一个清晰的真机日志问题。
- 每次修复都要写明触发日志、根因、改动范围、验证结论。
- 优先修自定位、路径推导、hook 兼容这类边界问题。
- 禁止重新引入“SDK 28 全部使用 linker64”的硬编码策略。
- 禁止把第二包逐步改成与 `patched` 完全一样，否则实验失去意义。

当前第 1 个小修（已验证但不充分）：

- 问题：linker64 启动 clang 时，原版 LLVM 用 `/proc/self/exe` 推导 `InstalledDir`，导致路径错到 `/apex/com.android.runtime/bin`。
- 修复：打 `tools/toolchain-patches/llvm-android-linker-pathfix.patch`。
- 验证：`2026-04-27 10:48` 日志确认单文件编译已成功，旧 `/apex`、resource-dir、`expected absolute path` 问题没有回归。

当前第 2 个小修（最新日志确认必须做）：

- 问题：CMake/Ninja 经 `/system/bin/sh + toolchain-shims/clang++` 后，对象编译可继续推进；但链接阶段 `clang++ -> ld.lld` 仍是 LLVM `Program.inc` 内部执行链路，当前快速热补丁包没有源码级 execwrap，导致 `clang++: error: unable to execute command: No such file or directory`。
- 修复：重建 `upstream-exechook` 时同时打 `tools/toolchain-patches/llvm-android-linker-pathfix.patch` 与 `tools/toolchain-patches/llvm-android-linker-execwrap.patch`。
- 不做：不启用旧的大 CMake exec/linker64 补丁，不新增第三个工具链包，不把第二包改成 `patched` 的全量硬编码方案。
- 验证：真机日志需要看到链接阶段不再出现 `unable to execute command`，并优先观察 `[TinaIDE][Program.inc] wrap exec via linker64`。

## tina-exec 实验约束

`upstream-exechook` 仍然是第二包实验位，但当前策略改为“exec hook + 小范围源码修复”组合：

- `NativeExecutableRunner` 外层启动策略仍然生效。
- SDK 28 不应默认强制所有 exec 走 linker64。
- clang 自定位由最小 `Path.inc` 修复负责。
- `clang++ -> ld.lld` 这类 LLVM 内部子进程由 `Program.inc` execwrap 负责，并通过 `TINAIDE_LLVM_WRAP_EXEC_LINKER64` 受控启用。
- CMake/Ninja 生成的编译器入口继续走 `/system/bin/sh + toolchain-shims/*`，避免 `ninja` 直接 spawn app-private clang。
- CMake Ninja build 阶段不再继承 `tina-exec` 的 `LD_PRELOAD`，避免系统 shell shim 与 preload 组合出现静默失败。
- 对 shell 脚本、系统路径 `/system`、`/apex`、linker/linker64 本身不能误 hook。
- 日志需要能区分：外层 linker64、Path 自定位、LLVM `Program.inc` 内部包装、`tina-exec` hook。

## 不做什么

本实验阶段明确不做：

- 不新增第三个工具链包位。
- 不把 `upstream-exechook` 设为默认工具链。
- 不删除现有 `patched` 基线。
- 不继续把“完全不改 LLVM 的 upstream-exechook”包装成可用候选。
- 不在 SDK 28 上重新写死“全部 exec 强制 linker64”。
- 不承诺 x86_64 同步支持。
- 不改变普通用户的工具链安装流程。
- 不把实验变体暴露到正式设置页。

## 风险与回滚

主要风险：

- `LD_PRELOAD`/hook 在不同 Android 版本或 ROM 上行为不同。
- hook 过宽可能影响系统工具、shell 脚本或 linker 本身。
- `Path.inc` 只修 clang 自定位，不保证 CMake/Ninja 的所有子进程链路。
- CMake/Ninja 子进程链路比 clang 更复杂，可能需要额外白名单或 hook 规则。

回滚策略：

- 默认工具链始终保留 `patched`。
- `upstream-exechook` 只在开发者选项中暴露。
- 若实验包不稳定，直接隐藏切换项或禁用该变体。
- 不删除现有源码补丁构建脚本，直到实验数据证明 hook 方案可覆盖核心链路。

## 后续实施顺序

1. [x] 支持 `patched` 与 `upstream-exechook` 两个 arm64 包位。
2. [x] 扩展资产 spec 和安装 metadata，支持同版本不同 variant 共存。
3. [x] 在编译器诊断测试页面增加工具链变体选择。
4. [x] 为 `upstream-exechook` 变体开启 `tina-exec` 注入环境。
5. [x] 读取 `log/log.txt`，确认当前纯 upstream 二进制失败根因是 clang 自定位错误。
6. [x] 拆分 LLVM 补丁为 `pathfix` 与 `execwrap` 两个独立补丁。
7. [x] 构建脚本新增第二包小修所需开关与构建目录隔离。
8. [x] 已完成 `Path.inc` 快速热补丁验证包；日志证明它只能解决自定位/单文件问题，不能解决链接阶段内部 exec。
9. [x] 覆盖 assets 中的 `upstream-exechook` tar/sha256（当前为快速热补丁验证包）。
10. [x] 读取卸载重装后的 `log/log.txt`，确认旧 `/apex`/`-cc1` 编译失败已经消失。
11. [x] 修复应用侧 `compile_commands` 生成、复用和归一化时的 `toolchainId` 贯通，避免 clangd 继续吃默认工具链路径。
12. [x] 清理 arm64 assets 中未引用的历史默认包，仅保留 `patched` 与 `upstream-exechook` 两个包位，并通过资产校验。
13. [x] 重新阅读重新编译安装后的 `log/log.txt`，确认旧 `/apex`/`-cc1`/resource-dir 问题没有回归，并记录当前剩余失败点。
14. [x] 为 CMake/Ninja shell shim 子命令失败加入冲突判定机制：shim trace、`tina-exec` 关闭重试、两轮输出合并。
15. [x] 把 smoke `failureDetail` 写入 logcat，确保下一次日志能看到 clangd 实际 completion labels 与 CMake retry 详情。
16. [x] 读取 `2026-04-26 23:46` 真机日志，确认 CMake 剩余根因不是旧 `/apex`，而是 `cmake --build` 内部 direct exec 真实 `ninja`。
17. [x] 将 Ninja build/clean 改为 App 侧直接启动 `ninja -C`，避免 CMake 内部绕过 `NativeExecutableRunner`。
18. [x] 将 clangd smoke 的 completion label 匹配改为 `trim()` 后归一化匹配，避免前导空格误判。
19. [x] 重新生成包含 direct ninja 与 labels 归一化修复的 arm64 Debug APK，并确认包内仍只有两个工具链包。
20. [x] 读取 `2026-04-27 01:21` 真机日志，确认 direct ninja 已解决 CMake 内部 direct exec `ninja` 的 `Permission denied`。
21. [x] 在 `upstream-exechook` + `tina-exec` 模式下保留 App 侧 direct ninja，但 CMake 编译器入口恢复为 `/system/bin/sh + toolchain-shims/*`，避免 `ninja` 直接 spawn app-private `clang++`。
22. [x] 补齐 single-file clangd completion label 归一化，避免前导空格误判。
23. [x] 用户已重新编译安装并生成 `2026-04-27 10:48` 真机日志，包内策略仍维持两个工具链包位。
24. [x] 已重新读取 `2026-04-27 10:48` 日志，确认单文件成功、CMake 剩余失败推进到链接阶段。
25. [ ] 源码级重建 `upstream-exechook`：同时启用 `Path.inc + Program.inc execwrap`，替换快速热补丁包。
26. [x] App 侧 CMake Ninja build 阶段禁用 `tina-exec`，避免 `LD_PRELOAD + /system/bin/sh shim` 静默失败。
27. [ ] 覆盖 assets 中的 `upstream-exechook` tar/sha256 后重新打 arm64 Debug APK.
28. [ ] 真机复测 CMake smoke：确认 `[TinaIDE][Program.inc] wrap exec via linker64` 出现且 `unable to execute command` 消失。


---

## 2026-04-27 02:17 最新日志结论与 App 侧修复

日志文件：`log/log.txt`，更新时间 `2026-04-27 02:17:27`，大小 `89818 bytes`。

本轮结论：

- 旧问题未回归：未出现 `/apex/com.android.runtime/lib/clang`、`resource-dir=/apex`、`expected absolute path`、`Permission denied`、`Make command was`。
- `single_file_std_headers` 已通过，单文件编译与 clangd completion 均正常。
- CMake configure 已通过，direct ninja 已生效：日志出现 `Building CMake project in: build (direct ninja)`。
- 当前失败发生在 direct ninja 的第一个编译子命令：`ninja -> clang++`，日志只剩 `FAILED: ... main.cpp.o` 与 `ninja: build stopped: subcommand failed`，没有 clang 自身诊断。
- 工具链文件存在且可执行，日志中 `clang++`、`clang-22`、`ld.lld/lld`、`ninja` probe 均为 `exists=true, canExec=true`。
- 反向判断：这不是 Path.inc/resource-dir 问题，也不是 CMake 内部 direct exec `ninja` 的旧问题；更像是 `ninja` 用 `posix_spawn`/直接子进程启动真实 app-private `clang++`，没有经过 `NativeExecutableRunner` 包装。

本轮修复：

- `upstream-exechook + tina-exec` 模式下，CMake 的 `CMAKE_C_COMPILER` / `CMAKE_CXX_COMPILER` 重新走 `/system/bin/sh + toolchain-shims/*`。
- 保持 build 阶段仍由 App 侧 direct ninja 启动真实 `ninja`，避免回退到 CMake 内部 `cmake --build`。
- 目的：避免 `ninja` 直接 spawn app 私有目录真实 `clang++`；让 `ninja` 子命令先进入系统 `/system/bin/sh`，再由 shim 执行 `/system/bin/linker64 <real clang++>`。
- 这不是恢复旧的“所有 exec 都硬编码 linker64”，而是只对 CMake 生成的编译器入口做最小 App 侧修复。

本地验证：

- 已通过：`./gradlew.bat :core:compile:testDebugUnitTest --tests "com.wuxianggujun.tinaide.core.compile.cmake.NativeCMakeBuildExecutorConfigTest"`
- 额外编译验证：`./gradlew.bat :core:compile:compileDebugKotlin :app:compileArm64DebugKotlin` 在 `:tina-android-tree-sitter:android-tree-sitter:generateDebugRFile` 因 Windows/Gradle 本地缓存 `R.jar` 删除失败中断；该失败与本次 CMake 执行链路修改无关，未擅自删除 build 中间产物。

下一步：

- 待本地 Gradle 文件锁释放后重新打 `arm64 Debug APK`。
- 真机复测时重点确认 CMake failure detail 中是否变成 `/system/bin/sh .../toolchain-shims/.../clang++`，且不再出现裸真实 `.../bin/clang++` 作为 ninja 子命令入口。

补充验证：

- 已重试：`./gradlew.bat :app:assembleArm64Debug --no-build-cache`
- 结果：仍在 `:tina-android-tree-sitter:android-tree-sitter:generateDebugRFile` 失败。
- 失败原因：`external/tina-android-tree-sitter/android-tree-sitter/build/intermediates/compile_r_class_jar/debug/generateDebugRFile/R.jar` 被其它进程占用，Windows 返回文件正在使用，无法访问。
- 结论：当前 APK 未完成重新打包；这是 Windows 文件锁/本地构建环境问题，不是本轮 CMake/tina-exec 代码修复导致。

---

## 2026-04-27 10:48 最新日志结论与修复方向

日志文件：`log/log.txt`，更新时间 `2026-04-27 10:48:48`，大小 `131089 bytes`。

本轮结论：

- 单文件链路已经成功：日志出现 `single_file_std_headers success=true` 与 `Single-file build finished: exitCode=0`。
- 旧问题没有回归：未出现 `/apex/com.android.runtime/lib/clang`、`resource-dir=/apex`、`expected absolute path`、`Permission denied`、`Make command was`。
- CMake 配置阶段已经走到正确入口：`cmakeC=/system/bin/sh`、`cmakeCxx=/system/bin/sh`，`CMAKE_CXX_COMPILER_ARG1` 指向 `toolchain-shims/.../clang++`。
- 第一轮 CMake build 带 `LD_PRELOAD` 时仍在对象编译阶段静默失败，没有 clang 诊断；清理 `tina-exec` 后重试，对象编译可以继续。
- 当前真正剩余失败已经推进到链接阶段：`[3/3] Linking CXX shared library` 后出现 `clang++: error: unable to execute command: No such file or directory` 与 `linker command failed due to signal`。
- 链接失败时 shim 环境为 `LD_PRELOAD=<unset>`，说明这不是 `tina-exec` preload 当前轮导致，而是 `clang++` 内部执行 `ld.lld` 仍没有被 LLVM `Program.inc` 包装。

本轮 App 侧修复：

- CMake Ninja build 阶段如果已经使用 shell shim/linker64 编译器入口，则主动禁用 `tina-exec`，并提前执行 Ninja 路径 patch。
- 目的：直接跳过日志中已证实的 `LD_PRELOAD + /system/bin/sh toolchain-shim` 静默失败，不再依赖“先失败再 retry”。
- 保留 `TINAIDE_LLVM_WRAP_EXEC_LINKER64=1`，供后续源码级 `Program.inc` execwrap 二进制读取。
- 这只能解决第一轮静默失败；链接阶段仍必须靠新的 `upstream-exechook` 源码级重建产物解决。

下一步工具链动作：

```bash
APPLY_LLVM_ANDROID_EXEC_PATCH=0 \
APPLY_LLVM_ANDROID_PATH_FIX_PATCH=1 \
APPLY_LLVM_ANDROID_EXEC_WRAP_PATCH=1 \
APPLY_CMAKE_ANDROID_EXEC_PATCH=0 \
PACKAGE_VARIANT=upstream-exechook \
TOOLCHAIN_VERSION=0.2.3 \
NINJA_TARGETS="clang,lld" \
bash scripts/build-and-package-android-toolchain.sh
```

注意：这是第二包内部的小范围源码修复，不新增第三包；产物仍覆盖 `upstream-exechook` 的 tar/sha256。首次 `pathfix1-execwrap1` 源码构建可能需要补齐包装所需目标，后续同 profile 才会走增量。
