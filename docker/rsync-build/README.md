# TinaIDE Rsync Docker Builder

使用 Docker 编译支持 16KB 页面对齐的 Android rsync 二进制文件。

## 特性

- ✅ **16KB 页面对齐** - 支持 Android 15+ 设备
- ✅ **多架构支持** - arm64-v8a, armeabi-v7a, x86_64, x86
- ✅ **Docker 隔离** - 不污染本地环境
- ✅ **源码持久化** - 使用 Docker volume 缓存源码
- ✅ **自动化构建** - 一键构建所有架构

## 快速开始

### 前置要求

- Docker Desktop（Windows/macOS）或 Docker Engine（Linux）
- PowerShell（Windows）或 Bash（Linux/macOS）

### 构建单个架构

```powershell
# 构建 ARM64 版本（推荐）
.\docker\rsync-build\build-rsync.ps1 -Architecture arm64-v8a

# 构建其他架构
.\docker\rsync-build\build-rsync.ps1 -Architecture armeabi-v7a
.\docker\rsync-build\build-rsync.ps1 -Architecture x86_64
.\docker\rsync-build\build-rsync.ps1 -Architecture x86
```

### 构建所有架构

```powershell
.\docker\rsync-build\build-rsync.ps1 -AllArchitectures
```

### 清理构建产物

```powershell
.\docker\rsync-build\build-rsync.ps1 -Clean
```

## 参数说明

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `-Architecture` | 目标架构 | `arm64-v8a` |
| `-AllArchitectures` | 构建所有架构 | - |
| `-Clean` | 清理所有构建产物 | - |
| `-NoBuildCache` | 不使用 Docker 缓存 | - |
| `-RsyncVersion` | Rsync 版本标签 | `v3.4.0` |

## 输出位置

构建完成后，二进制文件会自动复制到：

```
app/src/main/jniLibs/
├── arm64-v8a/
│   └── librsync.so
├── armeabi-v7a/
│   └── librsync.so
├── x86_64/
│   └── librsync.so
└── x86/
    └── librsync.so
```

## 验证 16KB 对齐

构建脚本会自动验证对齐，你也可以手动检查：

```bash
# 在 WSL 或 Linux 中
readelf -l app/src/main/jniLibs/arm64-v8a/librsync.so | grep LOAD
```

期望输出（注意 `0x4000`）：
```
LOAD  0x000000 0x0000000000000000 0x0000000000000000 0x0abcd 0x0abcd R E 0x4000
```

## 工作原理

### Docker 镜像层次

1. **基础层** - Ubuntu 24.04 + 基础工具
2. **NDK 层** - Android NDK r27c（最大的文件，缓存优化）
3. **依赖层** - 构建工具（git, gcc, make 等）
4. **脚本层** - 构建脚本

### 构建流程

1. 构建 Docker 镜像（首次较慢，后续使用缓存）
2. 创建 Docker volume 存储源码（避免重复克隆）
3. 运行容器，执行构建脚本：
   - 克隆或更新 rsync 源码
   - 配置编译选项（添加 16KB 对齐标志）
   - 编译并 strip 符号
   - 验证对齐
   - 复制到输出目录

### 16KB 对齐实现

在 `build-rsync.sh` 中添加链接器标志：

```bash
export LDFLAGS="-Wl,-z,max-page-size=16384"

./configure \
    --host="$TARGET" \
    ... \
    LDFLAGS="$LDFLAGS"
```

## 集成到项目

### 方式 1：移除 Maven 依赖（推荐）

在 `app/build.gradle.kts` 中：

```kotlin
// 注释掉或删除
// implementation("com.nerdoftheherd:android-rsync:3.4.1")
```

本地 `jniLibs` 中的文件会被自动打包到 APK。

### 方式 2：保留 Maven 依赖作为后备

```kotlin
// 保留依赖
implementation("com.nerdoftheherd:android-rsync:3.4.1")

// 本地 jniLibs 中的文件会覆盖 AAR 中的文件
```

## 高级用法

### 指定 Rsync 版本

```powershell
.\docker\rsync-build\build-rsync.ps1 -RsyncVersion v3.3.0
```

### 重新构建 Docker 镜像

```powershell
.\docker\rsync-build\build-rsync.ps1 -NoBuildCache
```

### 手动运行 Docker 容器

```bash
# 构建镜像
docker build -t tinaide-rsync-builder docker/rsync-build

# 运行容器
docker run --rm \
  -v tinaide-rsync-src:/build/src \
  -v $(pwd)/app/src/main/jniLibs/arm64-v8a:/output/arm64-v8a \
  -e TARGET_ARCH=aarch64-linux-android \
  -e RSYNC_VERSION=v3.4.0 \
  tinaide-rsync-builder
```

## 故障排除

### Docker 镜像构建失败

**问题**: 网络超时或下载失败

**解决**:
```powershell
# 使用国内镜像源（已在 Dockerfile 中配置）
# 或者重试构建
.\docker\rsync-build\build-rsync.ps1 -NoBuildCache
```

### 容器运行失败

**问题**: 权限错误或 volume 问题

**解决**:
```powershell
# 清理并重新构建
.\docker\rsync-build\build-rsync.ps1 -Clean
.\docker\rsync-build\build-rsync.ps1
```

### 输出文件不存在

**问题**: 构建成功但找不到文件

**解决**:
```powershell
# 检查输出目录
ls app\src\main\jniLibs\arm64-v8a\

# 查看 Docker 日志
docker logs tinaide-rsync-build-arm64-v8a
```

## 性能优化

### 首次构建

- 下载 NDK（~1GB）：5-10 分钟
- 构建镜像：2-3 分钟
- 编译 rsync：1-2 分钟
- **总计**：约 10-15 分钟

### 后续构建

- 使用缓存的镜像和源码
- 仅编译时间：1-2 分钟
- **总计**：约 1-2 分钟

### 多架构构建

- 并行构建（如果 Docker 资源充足）
- 源码共享（使用 volume）
- **总计**：约 5-8 分钟

## 与 WSL 方案对比

| 特性 | Docker | WSL |
|------|--------|-----|
| 环境隔离 | ✅ 完全隔离 | ❌ 共享环境 |
| 依赖管理 | ✅ 自动化 | ❌ 手动安装 |
| 可重复性 | ✅ 高 | ⚠️ 中等 |
| 首次设置 | ⚠️ 需要 Docker | ✅ 快速 |
| 构建速度 | ⚠️ 稍慢 | ✅ 快 |
| 跨平台 | ✅ 是 | ❌ 仅 Windows |

## 参考资料

- [16KB 对齐修复方案](../../docs/design/16KB-Page-Alignment-Fix.md)
- [Android 16KB 页面大小指南](https://developer.android.com/guide/practices/page-sizes)
- [Rsync 官方仓库](https://github.com/RsyncProject/rsync)
- [android-rsync 项目](https://github.com/ribbons/android-rsync)

## 许可证

与上游项目保持一致：GPL-3.0-or-later
