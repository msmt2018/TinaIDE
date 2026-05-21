#!/bin/bash
# build-pcre2.sh - 构建 PCRE2 正则表达式库
# 用法: ./build-pcre2.sh <架构> <链接类型>

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../build-common.sh"

# ===== 配置 =====
PCRE2_VERSION="10.43"
PCRE2_URL="https://github.com/PCRE2Project/pcre2/releases/download/pcre2-${PCRE2_VERSION}/pcre2-${PCRE2_VERSION}.tar.gz"
PCRE2_MIRROR="https://ghproxy.com/${PCRE2_URL}"

# ===== 参数解析 =====
ARCH=${1:-arm64-v8a}
LINK_TYPE=${2:-static}

log_info "Building PCRE2 ${PCRE2_VERSION}"
log_info "  Architecture: ${ARCH}"
log_info "  Link Type: ${LINK_TYPE}"

# ===== 设置工具链 =====
setup_toolchain "$ARCH"

# ===== 下载源码 =====
SRC_DIR="/build/src/pcre2-${PCRE2_VERSION}"
if [ ! -d "$SRC_DIR" ]; then
    log_info "Downloading PCRE2..."
    cd /build/src
    download_with_retry "$PCRE2_URL" "pcre2.tar.gz" "$PCRE2_MIRROR"
    tar -xzf pcre2.tar.gz
    rm pcre2.tar.gz
fi

# ===== 构建目录 =====
BUILD_DIR="/build/build/pcre2-${ARCH}-${LINK_TYPE}"
INSTALL_DIR="/build/install/pcre2-${ARCH}-${LINK_TYPE}"
rm -rf "$BUILD_DIR" "$INSTALL_DIR"
mkdir -p "$BUILD_DIR" "$INSTALL_DIR"

cd "$BUILD_DIR"

# ===== CMake 配置 =====
CMAKE_EXTRA_ARGS=""

if [ "$LINK_TYPE" = "static" ]; then
    CMAKE_EXTRA_ARGS="-DBUILD_SHARED_LIBS=OFF -DBUILD_STATIC_LIBS=ON"
else
    CMAKE_EXTRA_ARGS="-DBUILD_SHARED_LIBS=ON -DBUILD_STATIC_LIBS=OFF"
fi

log_info "Configuring PCRE2..."
cmake -B "$BUILD_DIR" -S "$SRC_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="${CMAKE_TOOLCHAIN_FILE}" \
    -DANDROID_ABI="${CMAKE_ARCH}" \
    -DANDROID_PLATFORM="android-${ANDROID_API}" \
    -DCMAKE_INSTALL_PREFIX="${INSTALL_DIR}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DPCRE2_BUILD_PCRE2GREP=OFF \
    -DPCRE2_BUILD_TESTS=OFF \
    -DPCRE2_SUPPORT_UNICODE=ON \
    -DPCRE2_SUPPORT_JIT=ON \
    $CMAKE_EXTRA_ARGS

# ===== 编译 =====
log_info "Building PCRE2..."
cmake --build "$BUILD_DIR" -j$(nproc)

# ===== 安装 =====
log_info "Installing PCRE2..."
cmake --install "$BUILD_DIR"

# ===== 打包 =====
OUTPUT_DIR="/output/pcre2/${ARCH}"
mkdir -p "$OUTPUT_DIR"

cd "$INSTALL_DIR"

# Strip
if [ "$LINK_TYPE" = "shared" ]; then
    find lib -name "*.so*" -exec ${STRIP} --strip-unneeded {} \; 2>/dev/null || true
fi

# 创建极致压缩包
if [ "$LINK_TYPE" = "static" ]; then
    tar -cf - include lib/*.a lib/pkgconfig | xz -9e --threads=0 > "${OUTPUT_DIR}/pcre2-${ARCH}-static.tar.xz"
else
    tar -cf - include lib/*.so* | xz -9e --threads=0 > "${OUTPUT_DIR}/pcre2-${ARCH}-shared.tar.xz"
fi

log_success "PCRE2 build complete!"
log_info "Output: ${OUTPUT_DIR}/pcre2-${ARCH}-${LINK_TYPE}.tar.xz"
ls -lh "${OUTPUT_DIR}"/*.tar.xz
