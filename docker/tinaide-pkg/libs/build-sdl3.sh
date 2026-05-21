#!/bin/bash
# build-sdl3.sh - 构建 SDL3 动态库
# SDL3 (Simple DirectMedia Layer 3) - 跨平台多媒体库
# 用法: ./build-sdl3.sh <架构> <链接类型>
# 示例: ./build-sdl3.sh arm64-v8a shared

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../build-common.sh"

# ===== 配置 =====
SDL3_VERSION="3.1.6"
SDL3_REPO="libsdl-org/SDL"
SDL3_BRANCH="main"  # SDL3 在 main 分支
ANDROID_API=28      # 与 TinaIDE app minSdk 保持一致

# ===== 参数解析 =====
ARCH=${1:-arm64-v8a}
LINK_TYPE=${2:-shared}  # SDL3 推荐使用 shared

log_info "Building SDL3 ${SDL3_VERSION}"
log_info "  Architecture: ${ARCH}"
log_info "  Link Type: ${LINK_TYPE}"

# ===== 设置工具链 =====
setup_toolchain "$ARCH"

# ===== 下载源码 =====
SRC_DIR="/build/src/SDL-${SDL3_VERSION}"
if [ ! -d "$SRC_DIR" ]; then
    log_info "Downloading SDL3..."
    cd /build/src
    git_clone_with_mirror "${SDL3_REPO}" "$SRC_DIR" "${SDL3_BRANCH}"

    # 检出特定版本（如果需要）
    cd "$SRC_DIR"
    # git checkout "release-${SDL3_VERSION}" 2>/dev/null || log_warn "Using latest main branch"
fi

# ===== 构建目录 =====
BUILD_DIR="/build/build/sdl3-${ARCH}-${LINK_TYPE}"
INSTALL_DIR="/build/install/sdl3-${ARCH}-${LINK_TYPE}"
rm -rf "$BUILD_DIR" "$INSTALL_DIR"
mkdir -p "$BUILD_DIR" "$INSTALL_DIR"

cd "$BUILD_DIR"

# ===== CMake 配置 =====
log_info "Configuring SDL3..."

# SDL3 构建选项
CMAKE_EXTRA_ARGS="-DCMAKE_INSTALL_PREFIX=${INSTALL_DIR}"

# 链接类型
if [ "$LINK_TYPE" = "static" ]; then
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DSDL_SHARED=OFF -DSDL_STATIC=ON"
else
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DSDL_SHARED=ON -DSDL_STATIC=OFF"
fi

# 禁用测试和示例
CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DSDL_TEST=OFF -DSDL_TESTS=OFF"

# Android 特定配置
CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DANDROID_STL=c++_static"

cmake -B "$BUILD_DIR" -S "$SRC_DIR" \
    -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="${CMAKE_TOOLCHAIN_FILE}" \
    -DANDROID_ABI="${CMAKE_ARCH}" \
    -DANDROID_PLATFORM="android-${ANDROID_API}" \
    -DCMAKE_BUILD_TYPE=Release \
    $CMAKE_EXTRA_ARGS

# ===== 编译 =====
log_info "Building SDL3..."
cmake --build "$BUILD_DIR" --parallel $(nproc)

# ===== 安装 =====
log_info "Installing SDL3..."
cmake --install "$BUILD_DIR"

# ===== 生成 pkg-config 文件 =====
PKGCONFIG_DIR="${INSTALL_DIR}/lib/pkgconfig"
mkdir -p "$PKGCONFIG_DIR"

cat > "${PKGCONFIG_DIR}/sdl3.pc" <<EOF
prefix=${INSTALL_DIR}
exec_prefix=\${prefix}
libdir=\${prefix}/lib
includedir=\${prefix}/include

Name: SDL3
Description: Simple DirectMedia Layer 3
Version: ${SDL3_VERSION}
Requires:
Conflicts:
Libs: -L\${libdir} -lSDL3
Cflags: -I\${includedir} -I\${includedir}/SDL3
EOF

log_info "Generated pkg-config file: ${PKGCONFIG_DIR}/sdl3.pc"

# ===== 生成包元数据 =====
cat > "${INSTALL_DIR}/package.json" <<EOF
{
  "id": "sdl3",
  "name": "SDL3",
  "version": "${SDL3_VERSION}",
  "description": "Simple DirectMedia Layer 3 - Cross-platform multimedia library",
  "platform": "android",
  "installType": "download",
  "category": "library",
  "homepage": "https://www.libsdl.org/",
  "license": "Zlib",
  "installedAt": $(date +%s)000,
  "files": {
    "include": "include/SDL3",
    "lib": "lib",
    "pkgconfig": "lib/pkgconfig/sdl3.pc"
  },
  "abi": "${ARCH}",
  "dependencies": []
}
EOF

log_info "Generated package metadata: ${INSTALL_DIR}/package.json"

# ===== 打包 =====
OUTPUT_DIR="/output/sdl3/${ARCH}"
mkdir -p "$OUTPUT_DIR"

cd "$INSTALL_DIR"

# Strip 动态库
if [ "$LINK_TYPE" = "shared" ]; then
    find lib -name "*.so*" -exec ${STRIP} --strip-unneeded {} \; 2>/dev/null || true
fi

# 创建压缩包
if [ "$LINK_TYPE" = "static" ]; then
    tar -cf - include lib/*.a lib/pkgconfig package.json | xz -9e --threads=0 > "${OUTPUT_DIR}/sdl3-${ARCH}-static.tar.xz"
else
    tar -cf - include lib/*.so* lib/pkgconfig package.json | xz -9e --threads=0 > "${OUTPUT_DIR}/sdl3-${ARCH}-shared.tar.xz"
fi

# 显示结果
log_success "SDL3 build complete!"
log_info "Output: ${OUTPUT_DIR}/sdl3-${ARCH}-${LINK_TYPE}.tar.xz"
ls -lh "${OUTPUT_DIR}"/*.tar.xz

# 显示包内容
log_info "Package contents:"
tar -tf "${OUTPUT_DIR}/sdl3-${ARCH}-${LINK_TYPE}.tar.xz" | head -20
