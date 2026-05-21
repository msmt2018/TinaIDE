#!/bin/bash
# build-openssl.sh - 构建 OpenSSL 静态/动态库
# 用法: ./build-openssl.sh <架构> <链接类型>
# 示例: ./build-openssl.sh arm64-v8a static

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../build-common.sh"

# ===== 配置 =====
OPENSSL_VERSION="3.2.1"
OPENSSL_URL="https://www.openssl.org/source/openssl-${OPENSSL_VERSION}.tar.gz"
OPENSSL_MIRROR="https://mirrors.aliyun.com/openssl/source/openssl-${OPENSSL_VERSION}.tar.gz"

# ===== 参数解析 =====
ARCH=${1:-arm64-v8a}
LINK_TYPE=${2:-static}

log_info "Building OpenSSL ${OPENSSL_VERSION}"
log_info "  Architecture: ${ARCH}"
log_info "  Link Type: ${LINK_TYPE}"

# ===== 设置工具链 =====
setup_toolchain "$ARCH"

# ===== 下载源码 =====
SRC_DIR="/build/src/openssl-${OPENSSL_VERSION}"
if [ ! -d "$SRC_DIR" ]; then
    log_info "Downloading OpenSSL..."
    cd /build/src
    download_with_retry "$OPENSSL_URL" "openssl.tar.gz" "$OPENSSL_MIRROR"
    tar -xzf openssl.tar.gz
    rm openssl.tar.gz
fi

# ===== 构建目录 =====
BUILD_DIR="/build/build/openssl-${ARCH}-${LINK_TYPE}"
INSTALL_DIR="/build/install/openssl-${ARCH}-${LINK_TYPE}"
rm -rf "$BUILD_DIR" "$INSTALL_DIR"
mkdir -p "$BUILD_DIR" "$INSTALL_DIR"

# ===== 配置 OpenSSL =====
cd "$SRC_DIR"

# OpenSSL 架构映射
case "$ARCH" in
    arm64-v8a)
        OPENSSL_TARGET="android-arm64"
        ;;
    armeabi-v7a)
        OPENSSL_TARGET="android-arm"
        ;;
    x86_64)
        OPENSSL_TARGET="android-x86_64"
        ;;
    x86)
        OPENSSL_TARGET="android-x86"
        ;;
esac

# 配置选项
CONFIGURE_OPTS="--prefix=${INSTALL_DIR} \
    --openssldir=${INSTALL_DIR}/ssl \
    -D__ANDROID_API__=${ANDROID_API} \
    no-tests \
    no-ui-console"

if [ "$LINK_TYPE" = "static" ]; then
    CONFIGURE_OPTS="$CONFIGURE_OPTS no-shared"
else
    CONFIGURE_OPTS="$CONFIGURE_OPTS shared"
fi

log_info "Configuring OpenSSL..."
./Configure ${OPENSSL_TARGET} ${CONFIGURE_OPTS}

# ===== 编译 =====
log_info "Building OpenSSL..."
make -j$(nproc)

# ===== 安装 =====
log_info "Installing OpenSSL..."
make install_sw  # 不安装文档

# ===== 打包 =====
OUTPUT_DIR="/output/openssl/${ARCH}"
mkdir -p "$OUTPUT_DIR"

cd "$INSTALL_DIR"

# 创建极致压缩包
if [ "$LINK_TYPE" = "static" ]; then
    tar -cf - include lib/*.a | xz -9e --threads=0 > "${OUTPUT_DIR}/openssl-${ARCH}-static.tar.xz"
else
    tar -cf - include lib/*.so* | xz -9e --threads=0 > "${OUTPUT_DIR}/openssl-${ARCH}-shared.tar.xz"
fi

# 显示结果
log_success "OpenSSL build complete!"
log_info "Output: ${OUTPUT_DIR}/openssl-${ARCH}-${LINK_TYPE}.tar.xz"
ls -lh "${OUTPUT_DIR}"/*.tar.xz
