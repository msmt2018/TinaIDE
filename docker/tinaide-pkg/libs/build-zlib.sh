#!/bin/bash
# build-zlib.sh - 构建 zlib 静态/动态库
# 用法: ./build-zlib.sh <架构> <链接类型>

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../build-common.sh"

# ===== 配置 =====
ZLIB_VERSION="1.3.1"
ZLIB_URL="https://zlib.net/zlib-${ZLIB_VERSION}.tar.gz"
ZLIB_MIRROR="https://mirrors.aliyun.com/macports/distfiles/zlib/zlib-${ZLIB_VERSION}.tar.gz"

# ===== 参数解析 =====
ARCH=${1:-arm64-v8a}
LINK_TYPE=${2:-static}

log_info "Building zlib ${ZLIB_VERSION}"
log_info "  Architecture: ${ARCH}"
log_info "  Link Type: ${LINK_TYPE}"

# ===== 设置工具链 =====
setup_toolchain "$ARCH"

# ===== 下载源码 =====
SRC_DIR="/build/src/zlib-${ZLIB_VERSION}"
if [ ! -d "$SRC_DIR" ]; then
    log_info "Downloading zlib..."
    cd /build/src
    download_with_retry "$ZLIB_URL" "zlib.tar.gz" "$ZLIB_MIRROR"
    tar -xzf zlib.tar.gz
    rm zlib.tar.gz
fi

# ===== 构建目录 =====
BUILD_DIR="/build/build/zlib-${ARCH}-${LINK_TYPE}"
INSTALL_DIR="/build/install/zlib-${ARCH}-${LINK_TYPE}"
rm -rf "$BUILD_DIR" "$INSTALL_DIR"
mkdir -p "$BUILD_DIR" "$INSTALL_DIR"

# 复制源码到构建目录 (zlib 不支持 out-of-tree build)
cp -r "$SRC_DIR"/* "$BUILD_DIR/"
cd "$BUILD_DIR"

# ===== 配置 =====
log_info "Configuring zlib..."
./configure --prefix="${INSTALL_DIR}"

# ===== 编译 =====
log_info "Building zlib..."

if [ "$LINK_TYPE" = "static" ]; then
    make -j$(nproc) libz.a
    # 手动安装静态库
    mkdir -p "${INSTALL_DIR}/lib" "${INSTALL_DIR}/include"
    cp libz.a "${INSTALL_DIR}/lib/"
    cp zlib.h zconf.h "${INSTALL_DIR}/include/"
else
    make -j$(nproc)
    make install
    # 只保留共享库
    rm -f "${INSTALL_DIR}/lib/libz.a"
fi

# ===== 打包 =====
OUTPUT_DIR="/output/zlib/${ARCH}"
mkdir -p "$OUTPUT_DIR"

cd "$INSTALL_DIR"

# Strip
if [ "$LINK_TYPE" = "shared" ]; then
    ${STRIP} --strip-unneeded lib/libz.so* 2>/dev/null || true
fi

# 创建极致压缩包
if [ "$LINK_TYPE" = "static" ]; then
    tar -cf - include lib/*.a | xz -9e --threads=0 > "${OUTPUT_DIR}/zlib-${ARCH}-static.tar.xz"
else
    tar -cf - include lib/*.so* | xz -9e --threads=0 > "${OUTPUT_DIR}/zlib-${ARCH}-shared.tar.xz"
fi

log_success "zlib build complete!"
log_info "Output: ${OUTPUT_DIR}/zlib-${ARCH}-${LINK_TYPE}.tar.xz"
ls -lh "${OUTPUT_DIR}"/*.tar.xz
