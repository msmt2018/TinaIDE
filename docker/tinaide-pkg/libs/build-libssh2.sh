#!/bin/bash
# build-libssh2.sh - 构建 libssh2 静态/动态库
# 依赖: OpenSSL, zlib
# 用法: ./build-libssh2.sh <架构> <链接类型>

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../build-common.sh"

# ===== 配置 =====
LIBSSH2_VERSION="1.11.0"
LIBSSH2_URL="https://www.libssh2.org/download/libssh2-${LIBSSH2_VERSION}.tar.gz"
LIBSSH2_MIRROR="https://ghproxy.com/https://github.com/libssh2/libssh2/releases/download/libssh2-${LIBSSH2_VERSION}/libssh2-${LIBSSH2_VERSION}.tar.gz"

# ===== 参数解析 =====
ARCH=${1:-arm64-v8a}
LINK_TYPE=${2:-static}

log_info "Building libssh2 ${LIBSSH2_VERSION}"
log_info "  Architecture: ${ARCH}"
log_info "  Link Type: ${LINK_TYPE}"

# ===== 设置工具链 =====
setup_toolchain "$ARCH"

# ===== 下载源码 =====
SRC_DIR="/build/src/libssh2-${LIBSSH2_VERSION}"
if [ ! -d "$SRC_DIR" ]; then
    log_info "Downloading libssh2..."
    cd /build/src
    download_with_retry "$LIBSSH2_URL" "libssh2.tar.gz" "$LIBSSH2_MIRROR"
    tar -xzf libssh2.tar.gz
    rm libssh2.tar.gz
fi

# ===== 构建目录 =====
BUILD_DIR="/build/build/libssh2-${ARCH}-${LINK_TYPE}"
INSTALL_DIR="/build/install/libssh2-${ARCH}-${LINK_TYPE}"
rm -rf "$BUILD_DIR" "$INSTALL_DIR"
mkdir -p "$BUILD_DIR" "$INSTALL_DIR"

cd "$BUILD_DIR"

# ===== 查找依赖 =====
OPENSSL_DIR="/build/install/openssl-${ARCH}-static"
ZLIB_DIR="/build/install/zlib-${ARCH}-static"

CMAKE_EXTRA_ARGS=""

if [ -d "$OPENSSL_DIR" ]; then
    log_info "Found OpenSSL at ${OPENSSL_DIR}"
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DOPENSSL_ROOT_DIR=${OPENSSL_DIR}"
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DCRYPTO_BACKEND=OpenSSL"
else
    log_warn "OpenSSL not found, using mbedTLS fallback"
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DCRYPTO_BACKEND=mbedTLS"
fi

if [ -d "$ZLIB_DIR" ]; then
    log_info "Found zlib at ${ZLIB_DIR}"
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DZLIB_ROOT=${ZLIB_DIR}"
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DENABLE_ZLIB_COMPRESSION=ON"
else
    log_warn "zlib not found, disabling compression"
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DENABLE_ZLIB_COMPRESSION=OFF"
fi

# 链接类型
if [ "$LINK_TYPE" = "static" ]; then
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DBUILD_SHARED_LIBS=OFF -DBUILD_STATIC_LIBS=ON"
else
    CMAKE_EXTRA_ARGS="$CMAKE_EXTRA_ARGS -DBUILD_SHARED_LIBS=ON -DBUILD_STATIC_LIBS=OFF"
fi

# ===== CMake 配置 =====
log_info "Configuring libssh2..."
cmake -B "$BUILD_DIR" -S "$SRC_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="${CMAKE_TOOLCHAIN_FILE}" \
    -DANDROID_ABI="${CMAKE_ARCH}" \
    -DANDROID_PLATFORM="android-${ANDROID_API}" \
    -DCMAKE_INSTALL_PREFIX="${INSTALL_DIR}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_EXAMPLES=OFF \
    -DBUILD_TESTING=OFF \
    $CMAKE_EXTRA_ARGS

# ===== 编译 =====
log_info "Building libssh2..."
cmake --build "$BUILD_DIR" -j$(nproc)

# ===== 安装 =====
log_info "Installing libssh2..."
cmake --install "$BUILD_DIR"

# ===== 打包 =====
OUTPUT_DIR="/output/libssh2/${ARCH}"
mkdir -p "$OUTPUT_DIR"

cd "$INSTALL_DIR"

# Strip
if [ "$LINK_TYPE" = "shared" ]; then
    find lib -name "*.so*" -exec ${STRIP} --strip-unneeded {} \; 2>/dev/null || true
fi

# 创建极致压缩包
if [ "$LINK_TYPE" = "static" ]; then
    tar -cf - include lib/*.a | xz -9e --threads=0 > "${OUTPUT_DIR}/libssh2-${ARCH}-static.tar.xz"
else
    tar -cf - include lib/*.so* | xz -9e --threads=0 > "${OUTPUT_DIR}/libssh2-${ARCH}-shared.tar.xz"
fi

log_success "libssh2 build complete!"
log_info "Output: ${OUTPUT_DIR}/libssh2-${ARCH}-${LINK_TYPE}.tar.xz"
ls -lh "${OUTPUT_DIR}"/*.tar.xz
