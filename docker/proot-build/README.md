# TinaIDE PRoot Builder

从 Termux 源码编译 PRoot、proot-loader 和 talloc，生成可在 Android 上运行的二进制文件。

## 特性

- ✅ **源码持久化**: 使用 Docker volume，首次克隆后复用，无需重复下载
- ✅ **增量编译**: 只重新编译修改过的文件
- ✅ **包名无关**: 不做包名替换
- ✅ **多架构支持**: arm64-v8a 和 x86_64
- ✅ **实时进度显示**: 显示源码下载、编译的详细进度信息
- ✅ **国内加速**: 默认使用阿里云/腾讯云镜像，适合中国大陆用户

## 快速开始

```powershell
# 构建所有架构（首次运行会克隆源码，后续复用）
.\docker\proot-build\build-proot.ps1

# 只构建 arm64（真机）
.\docker\proot-build\build-proot.ps1 -Arch arm64

# 将 talloc 静态链接进 libproot.so（运行时不再依赖 libtalloc.so.2）
.\docker\proot-build\build-proot.ps1 -Arch arm64 -TallocLink static

# 构建并复制到项目
.\docker\proot-build\build-proot.ps1 -CopyToJniLibs -CopyToAssets

```

## 产物

```
output/
├── arm64/
│   ├── libproot.so           # PRoot 主程序
│   ├── libproot-loader.so    # ELF loader (64-bit)
│   ├── libproot-loader32.so  # ELF loader (32-bit compat)
│   └── libtalloc.so.2        # talloc 内存库（仅 TallocLink=shared 时输出）
└── x86_64/
    ├── libproot.so
    ├── libproot-loader.so
    └── libtalloc.so.2        # 仅 TallocLink=shared 时输出
```

## 构建模式

| 模式 | 镜像 | 源码 | 说明 |
|------|------|------|------|
| `incremental` | 复用 | 复用 | 默认，最快 |
| `rebuild` | 重建 | 复用 | 更新 NDK 等依赖 |
| `clean` | 重建 | 重建 | 完全从头开始 |

```powershell
# 增量构建（默认）
.\docker\proot-build\build-proot.ps1

# 重建镜像但保留源码
.\docker\proot-build\build-proot.ps1 -Mode rebuild

# 完全清理后重建
.\docker\proot-build\build-proot.ps1 -Mode clean

# 只重新克隆源码（强制从头准备）
.\docker\proot-build\build-proot.ps1 -ResetSource
```

## 项目集成

构建完成后，需要将产物复制到项目中：

```powershell
# 自动复制（推荐）
.\docker\proot-build\build-proot.ps1 -CopyToJniLibs -CopyToAssets

# 如果使用 -TallocLink static，一般不需要 -CopyToAssets（可减少 APK 体积）
.\docker\proot-build\build-proot.ps1 -CopyToJniLibs -TallocLink static

# 手动复制
Copy-Item output\arm64\libproot*.so app\src\prebuiltProot\jniLibs\arm64-v8a\
Copy-Item output\arm64\libtalloc.so.2 app\src\arm64\assets\proot\arm64-v8a\

Copy-Item output\x86_64\libproot*.so app\src\prebuiltProot\jniLibs\x86_64\
Copy-Item output\x86_64\libtalloc.so.2 app\src\x86_64\assets\proot\x86_64\
```

`-CopyToJniLibs` 会写入 `app/src/prebuiltProot/jniLibs/`。TinaIDE 默认会从 `external/termux-proot` 源码直接编译 PRoot；只有传入 `-Ptina.buildProotFromSource=false` 时，Gradle 才会合并这里的 Docker 预编译产物。

## 清理

```powershell
# 删除输出文件
.\docker\proot-build\clean.ps1 -RemoveOutput

# 删除 Docker 镜像
.\docker\proot-build\clean.ps1 -RemoveImages

# 删除源码（下次构建会重新克隆）
.\docker\proot-build\clean.ps1 -RemoveSource

# 全部清理
.\docker\proot-build\clean.ps1 -All
```

## Docker 资源

构建过程使用以下 Docker 资源：

| 资源 | 名称 | 用途 |
|------|------|------|
| 镜像 | `tinaide-proot-builder:arm64` | arm64 构建环境 |
| 镜像 | `tinaide-proot-builder:x86_64` | x86_64 构建环境 |
| Volume | `tinaide-proot-source` | 持久化源码（所有架构共享） |

```powershell
# 查看资源
docker images | Select-String "tinaide-proot"
docker volume ls | Select-String "tinaide-proot"
```

## 技术说明

### 源码来源

| 组件 | 仓库 | 说明 |
|------|------|------|
| proot | [termux/proot](https://github.com/termux/proot) | Termux 官方 Android 优化版 |
| proot-loader | termux/proot（src/loader） | ELF 加载器 |
| talloc | 同上 | 内存分配库 |

### 编译选项

- `PROOT_UNBUNDLE_LOADER=1`: 使用外部 loader
- Android NDK r27c + API 28
- PIE (Position Independent Executable)
- **国内镜像加速**:
  - Ubuntu 包: 阿里云镜像
  - Android NDK: 腾讯云 → 阿里云 → 官方源（自动降级）
  - GitHub 源码: ghproxy 镜像 → 官方源（自动降级）

### Dockerfile 层级优化

为了避免修改依赖包时重新下载 NDK（633MB），Dockerfile 采用了优化的层级结构：

```
第 1 层：基础镜像 + 换源（几乎不变）
第 2 层：基础工具（curl, unzip）（几乎不变）
第 3 层：下载 NDK（最大文件，放前面）← 缓存在这里
第 4 层：其他依赖包（可能调整）
第 5 层：复制脚本（经常变）
```

这样即使修改了依赖包，也不会重新下载 NDK。详见 [DOCKERFILE-OPTIMIZATION.md](DOCKERFILE-OPTIMIZATION.md)

### 为什么需要自己编译？

1. **问题调试**: 添加日志、调试符号
2. **版本控制**: 同步上游修复
3. **Android 兼容性**: 加入 Android 16+ 补丁

## 故障排除

### 构建卡住没有进度显示

默认执行 `build-proot.ps1` 时会直接输出 Docker 实时日志。
如果终端没有输出，请直接查看容器日志定位问题：

```powershell
docker logs -f tinaide-proot-build-arm64
docker logs -f tinaide-proot-build-x86_64
```

### Docker 未运行

```powershell
docker version
# 如果失败，启动 Docker Desktop
```

### NDK 下载慢

脚本会自动尝试腾讯云镜像，如仍然慢，可手动下载 NDK 放到 `/opt/android-ndk`。

### 编译失败

```powershell
# 查看完整日志
docker logs tinaide-proot-build-arm64

# 进入容器调试
docker run -it --rm -v tinaide-proot-source:/build/src tinaide-proot-builder:arm64 /bin/bash
```

## 详细文档

完整的编译指南请参考 [docs/proot-compilation-guide.md](../../docs/proot-compilation-guide.md)
