# TinaIDE Package Builder

通用 Android NDK 原生库构建系统，用于编译各种依赖库的静态/动态版本。

## 特性

- **统一构建环境**：基于 Docker，确保构建可重复
- **NDK 缓存复用**：NDK 层独立缓存，避免重复下载
- **源码持久化**：使用 Docker volume 保存源码，支持增量构建
- **极致压缩**：使用 xz -9e 最大压缩率
- **16KB 页对齐**：满足 Android 15+ 要求
- **多架构支持**：arm64-v8a, armeabi-v7a, x86_64, x86

## 目录结构

```
tinaide-pkg/
├── Dockerfile           # 构建环境镜像
├── build-pkg.ps1        # Windows 编排脚本
├── build.sh             # 容器内主构建脚本
├── build-common.sh      # 公共函数库
├── clean.ps1            # 清理脚本
├── libs/                # 各库的构建脚本
│   ├── build-zlib.sh
│   ├── build-openssl.sh
│   ├── build-curl.sh
│   ├── build-libssh2.sh
│   ├── build-libgit2.sh
│   ├── build-pcre2.sh
│   └── build-sdl3.sh
├── package-for-assets.sh # 打包为 assets 格式
└── output/              # 构建输出
    ├── zlib/
    │   └── arm64-v8a/
    │       └── zlib-arm64-v8a-static.tar.xz
    └── ...
```

## 快速开始

### 前置要求

- Windows 10/11 + PowerShell 5.1+
- Docker Desktop (启用 WSL2 后端)
- **推荐**: 复用现有的 `tinaide-toolchain-builder` 容器（已包含 NDK + CMake）

### 方式 1: 复用现有容器（推荐）

如果你已经有运行中的 `tinaide-toolchain-builder` 容器：

```bash
# 检查容器是否运行
docker ps | grep tinaide-toolchain-builder

# 复制构建脚本到容器
docker cp build-common.sh tinaide-toolchain-builder:/build/
docker cp libs/build-sdl3.sh tinaide-toolchain-builder:/build/libs/

# 在容器中构建
docker exec tinaide-toolchain-builder bash -c "
  export ANDROID_NDK_HOME=/opt/android-ndk-r27
  cd /build/libs
  bash build-sdl3.sh arm64-v8a shared
"

# 复制产物到本地
docker cp tinaide-toolchain-builder:/output/sdl3/arm64-v8a/sdl3-arm64-v8a-shared.tar.xz ./output/sdl3/arm64-v8a/
```

### 方式 2: 使用 PowerShell 脚本

构建 Docker 镜像并编译（首次运行需要下载 NDK）：

```powershell
# 构建 zlib 静态库 (arm64-v8a)
.\build-pkg.ps1 -Library zlib -Arch arm64-v8a -LinkType static

# 构建 openssl 动态库 (x86_64)
.\build-pkg.ps1 -Library openssl -Arch x86_64 -LinkType shared
```

### 构建所有库

```powershell
# 构建所有库的所有架构静态版本
.\build-pkg.ps1 -Library all -Arch all -LinkType static

# 构建所有库的 arm64 静态和动态版本
.\build-pkg.ps1 -Library all -Arch arm64-v8a -LinkType all
```

### 查看可用选项

```powershell
.\build-pkg.ps1 -Help
.\build-pkg.ps1 -List
```

### 清理

```powershell
# 清理所有 (输出 + Docker 资源)
.\clean.ps1 -All

# 只清理输出
.\clean.ps1 -Output

# 只清理 Docker 资源
.\clean.ps1 -Docker
```

## 支持的库

| 库名 | 版本 | 依赖 | 说明 |
|------|------|------|------|
| zlib | 1.3.1 | 无 | 压缩库 |
| openssl | 3.2.1 | 无 | TLS/SSL 库 |
| pcre2 | 10.43 | 无 | 正则表达式 |
| curl | 8.6.0 | openssl (可选) | HTTP 客户端 |
| libssh2 | 1.11.0 | openssl, zlib | SSH2 协议 |
| libgit2 | 1.7.2 | openssl, libssh2, zlib | Git 操作库 |
| sdl3 | 3.1.6 | 无 | 跨平台多媒体库 |

### 依赖关系图

```
zlib ──────────────────────────────┐
                                   │
openssl ───────────────────────────┼──> libssh2 ──> libgit2
                                   │
                                   └──> curl

sdl3 (独立，无依赖)
                                   │         │
pcre2 (独立)                       └─────────┘
                                   │
curl <── openssl (可选)            │
```

### 推荐构建顺序

如需构建 libgit2（完整功能），按以下顺序：

1. zlib
2. openssl
3. libssh2
4. libgit2

## 输出格式

每个库的输出包含：

```
{库名}-{架构}-{链接类型}.tar.xz
```

包内结构：

```
├── include/          # 头文件
│   └── *.h
└── lib/              # 库文件
    ├── *.a           # 静态库
    └── *.so*         # 动态库
```

### 解压使用

```bash
# 解压到项目目录
tar -xJf zlib-arm64-v8a-static.tar.xz -C /path/to/project/libs/arm64-v8a/
```

## 高级用法

### 构建模式

```powershell
# 增量模式 (默认): 复用已有镜像和源码
.\build-pkg.ps1 -Mode incremental

# 重建模式: 重建 Docker 镜像，保留源码
.\build-pkg.ps1 -Mode rebuild

# 清理模式: 完全清理后重新构建
.\build-pkg.ps1 -Mode clean
```

### 进入容器调试

```powershell
docker run -it --rm `
    -v tinaide-pkg-source:/build/src `
    -v ${PWD}/output:/output `
    tinaide-pkg-builder `
    /bin/bash
```

### 手动构建单个库

```bash
# 在容器内
source /build/build-common.sh
setup_toolchain arm64-v8a
bash /build/libs/build-zlib.sh arm64-v8a static
```

## 添加新库

1. 创建 `libs/build-{库名}.sh`
2. 参考现有脚本结构
3. 在 `build.sh` 的 `AVAILABLE_LIBS` 数组中添加库名
4. 在 `build-pkg.ps1` 的 `ValidateSet` 中添加库名

模板：

```bash
#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../build-common.sh"

# 配置
LIB_VERSION="x.y.z"
LIB_URL="https://..."

# 参数
ARCH=${1:-arm64-v8a}
LINK_TYPE=${2:-static}

# 设置工具链
setup_toolchain "$ARCH"

# 下载、配置、编译、打包...
```

## 技术细节

### NDK 配置

- NDK 版本: r27c
- 最低 API: 28（与 TinaIDE app minSdk 保持一致）
- 工具链: LLVM/Clang
- 页对齐: 16KB (`-Wl,-z,max-page-size=16384`)

### 编译标志

```bash
CFLAGS="-O2 -fPIC"
CXXFLAGS="-O2 -fPIC"
LDFLAGS="-Wl,-z,max-page-size=16384"
```

### 压缩策略

使用 xz 极限压缩：

```bash
tar -cf - . | xz -9e --threads=0 > output.tar.xz
```

- `-9e`: 最大压缩级别 + 极限模式
- `--threads=0`: 使用所有 CPU 核心

### Docker 层优化

1. **Layer 1**: 系统配置 + 镜像源
2. **Layer 2**: NDK 下载 (~633 MB, 独立缓存)
3. **Layer 3**: 构建工具
4. **Layer 4**: 脚本复制

## 常见问题

### Q: 首次构建很慢？

A: 首次需要下载 NDK (~633 MB)，后续构建会使用缓存。

### Q: 如何减小包体积？

A:
- 使用静态库时，链接器会只包含用到的符号
- 动态库会自动 strip 调试符号
- xz -9e 已是最大压缩

### Q: 构建失败如何调试？

A: 进入容器手动执行：
```powershell
docker run -it --rm tinaide-pkg-builder /bin/bash
```

### Q: 如何更新库版本？

A: 修改对应 `libs/build-{库名}.sh` 中的版本号和 URL。

---

## SDL3 完整使用示例

### 1. 编译 SDL3

```powershell
# 编译 arm64-v8a 动态库（推荐）
.\build-pkg.ps1 -Library sdl3 -Arch arm64-v8a -LinkType shared

# 编译所有架构
.\build-pkg.ps1 -Library sdl3 -Arch all -LinkType shared
```

### 2. 打包为 Assets

```bash
# 打包单架构（推荐，减小 APK 体积）
# 默认使用 xz 格式（最高压缩率）
./package-for-assets.sh sdl3 arm64-v8a

# 指定压缩格式
./package-for-assets.sh sdl3 arm64-v8a xz     # tar.xz (默认，1.2 MB)
./package-for-assets.sh sdl3 arm64-v8a zstd   # tar.zst (快速)
./package-for-assets.sh sdl3 arm64-v8a gz     # tar.gz (兼容)
./package-for-assets.sh sdl3 arm64-v8a zip    # zip (1.6 MB，不推荐)

# 打包多架构
./package-for-assets.sh sdl3 "arm64-v8a armeabi-v7a" xz
```

**压缩格式对比**：
- `xz`: 最高压缩率（1.2 MB），解压稍慢，**推荐**
- `zstd`: 快速压缩/解压，压缩率略低
- `gz`: 兼容性最好，压缩率中等
- `zip`: Windows 友好，但压缩率最低（1.6 MB）

产物：`app/src/main/assets/bundled_packages/sdl3.tar.xz` (~1.2 MB)

### 3. 应用集成

TinaIDE 已内置自动安装器，支持多种压缩格式：

**支持的格式**：
- `.tar.xz` - 最高压缩率（推荐）
- `.tar.zst` - 快速压缩/解压
- `.tar.gz` - 兼容性最好
- `.zip` - Windows 友好

**工作流程**：
1. 将打包好的文件放到 `app/src/main/assets/bundled_packages/`
2. 应用启动时自动扫描并解压到 `filesDir/installed-packages/sdl3/`
3. 自动解析 `package.json` 元数据并更新安装状态
4. 已安装的包会跳过（幂等性）

SDL3 的打包要点以本 README 当前章节为准；仓库不再单独维护额外的
`docs/` 专题长文档。

### 4. 在用户项目中使用

**CMakeLists.txt**:
```cmake
set(SDL3_ROOT /data/data/com.wuxianggujun.tinaide/files/installed-packages/sdl3)
target_include_directories(my_app PRIVATE ${SDL3_ROOT}/include)
target_link_libraries(my_app PRIVATE ${SDL3_ROOT}/lib/${ANDROID_ABI}/libSDL3.so)
```

**Clang 命令行**:
```bash
clang++ -I/data/data/.../sdl3/include \
        -L/data/data/.../sdl3/lib/arm64-v8a \
        -lSDL3 main.cpp -o my_app
```

### 5. 扩展库

使用相同流程可编译 SDL3 生态系统库：

- **SDL3_image**: 图像加载（PNG, JPEG, WebP）
- **SDL3_mixer**: 音频混音
- **SDL3_ttf**: TrueType 字体渲染

---

## 参考文档

- [Android NDK 官方文档](https://developer.android.com/ndk)
