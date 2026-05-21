#!/bin/bash
# build-libgit2.sh - 构建 libgit2 静态/动态库
# 依赖: OpenSSL, libssh2, zlib
# 用法: ./build-libgit2.sh <架构> <链接类型>

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../build-common.sh"

# ===== 配置 =====
LIBGIT2_VERSION="1.7.2"
LIBGIT2_REPO="libgit2/libgit2"

# ===== 参数解析 =====
ARCH=${1:-arm64-v8a}
LINK_TYPE=${2:-static}

log_info "Building libgit2 ${LIBGIT2_VERSION}"
log_info "  Architecture: ${ARCH}"
log_info "  Link Type: ${LINK_TYPE}"

# ===== 设置工具链 =====
setup_toolchain "$ARCH"

# ===== 下载源码 =====
SRC_DIR="/build/src/libgit2-${LIBGIT2_VERSION}"
if [ ! -d "$SRC_DIR" ]; then
    log_info "Downloading libgit2..."
    cd /build/src
    git_clone_with_mirror "${LIBGIT2_REPO}" "$SRC_DIR" "v${LIBGIT2_VERSION}"
fi

# ===== 构建目录 =====
BUILD_DIR="/build/build/libgit2-${ARCH}-${LINK_TYPE}"
INSTALL_DIR="/build/install/libgit2-${ARCH}-${LINK_TYPE}"
rm -rf "$BUILD_DIR" "$INSTALL_DIR"
mkdir -p "$BUILD_DIR" "$INSTALL_DIR"

cd "$BUILD_DIR"

# ===== 查找依赖 =====
OPENSSL_DIR="/build/install/openssl-${ARCH}-static"
LIBSSH2_DIR="/build/install/libssh2-${ARCH}-static"
ZLIB_DIR="/build/install/zlib-${ARCH}-static"

CMAKE_EXTRA_ARGS=""

if [ -d "$OPENSSL_DIR" ]; then
    log_info "Found OpenSSL at ${OPENSSL_DIR}"
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DOPENSSL_ROOT_DIR=${OPENSSL_DIR}"
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DUSE_HTTPS=OpenSSL"
else
    log_warn "OpenSSL not found, HTTPS disabled"
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DUSE_HTTPS=OFF"
fi

if [ -d "$LIBSSH2_DIR" ]; then
    log_info "Found libssh2 at ${LIBSSH2_DIR}"
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DLIBSSH2_FOUND=ON"
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DLIBSSH2_INCLUDE_DIRS=${LIBSSH2_DIR}/include"
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DLIBSSH2_LIBRARY_DIRS=${LIBSSH2_DIR}/lib"
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DUSE_SSH=ON"
else
    log_warn "libssh2 not found, SSH disabled"
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DUSE_SSH=OFF"
fi

if [ -d "$ZLIB_DIR" ]; then
    log_info "Found zlib at ${ZLIB_DIR}"
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DZLIB_ROOT=${ZLIB_DIR}"
fi

# 链接类型
if [ "$LINK_TYPE" = "static" ]; then
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DBUILD_SHARED_LIBS=OFF"
else
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DBUILD_SHARED_LIBS=ON"
fi

# ===== CMake 配置 =====
log_info "Configuring libgit2..."
cmake -B "$BUILD_DIR" -S "$SRC_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="${CMAKE_TOOLCHAIN_FILE}" \
    -DANDROID_ABI="${CMAKE_ARCH}" \
    -DANDROID_PLATFORM="android-${ANDROID_API}" \
    -DCMAKE_INSTALL_PREFIX="${INSTALL_DIR}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_TESTS=OFF \
    -DBUILD_CLI=OFF \
    -DREGEX_BACKEND=builtin \
    $CMAKE_EXTRA_ARGS

# ===== 编译 =====
log_info "Building libgit2..."
cmake --build "$BUILD_DIR" -j$(nproc)

# ===== 安装 =====
log_info "Installing libgit2..."
cmake --install "$BUILD_DIR"

# ===== 打包 =====
OUTPUT_DIR="/output/libgit2/${ARCH}"
mkdir -p "$OUTPUT_DIR"

cd "$INSTALL_DIR"

# Strip
if [ "$LINK_TYPE" = "shared" ]; then
    find lib -name "*.so*" -exec ${STRIP} --strip-unneeded {} \; 2>/dev/null || true
fi

# 创建极致压缩包
if [ "$LINK_TYPE" = "static" ]; then
    tar -cf - include lib/*.a lib/pkgconfig | xz -9e --threads=0 > "${OUTPUT_DIR}/libgit2-${ARCH}-static.tar.xz"
else
    tar -cf - include lib/*.so* | xz -9e --threads=0 > "${OUTPUT_DIR}/libgit2-${ARCH}-shared.tar.xz"
fi

log_success "libgit2 build complete!"
log_info "Output: ${OUTPUT_DIR}/libgit2-${ARCH}-${LINK_TYPE}.tar.xz"
ls -lh "${OUTPUT_DIR}"/*.tar.xz
