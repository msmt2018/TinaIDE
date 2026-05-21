#!/bin/bash
# build-curl.sh - 构建 libcurl 静态/动态库
# 依赖: OpenSSL (可选)
# 用法: ./build-curl.sh <架构> <链接类型> [with-ssl]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../build-common.sh"

# ===== 配置 =====
CURL_VERSION="8.6.0"
CURL_URL="https://curl.se/download/curl-${CURL_VERSION}.tar.gz"
CURL_MIRROR="https://mirrors.aliyun.com/macports/distfiles/curl/curl-${CURL_VERSION}.tar.gz"

# ===== 参数解析 =====
ARCH=${1:-arm64-v8a}
LINK_TYPE=${2:-static}
WITH_SSL=${3:-}

log_info "Building curl ${CURL_VERSION}"
log_info "  Architecture: ${ARCH}"
log_info "  Link Type: ${LINK_TYPE}"
log_info "  SSL: ${WITH_SSL:-no}"

# ===== 设置工具链 =====
setup_toolchain "$ARCH"

# ===== 下载源码 =====
SRC_DIR="/build/src/curl-${CURL_VERSION}"
if [ ! -d "$SRC_DIR" ]; then
    log_info "Downloading curl..."
    cd /build/src
    download_with_retry "$CURL_URL" "curl.tar.gz" "$CURL_MIRROR"
    tar -xzf curl.tar.gz
    rm curl.tar.gz
fi

# ===== 构建目录 =====
BUILD_DIR="/build/build/curl-${ARCH}-${LINK_TYPE}"
INSTALL_DIR="/build/install/curl-${ARCH}-${LINK_TYPE}"
rm -rf "$BUILD_DIR" "$INSTALL_DIR"
mkdir -p "$BUILD_DIR" "$INSTALL_DIR"

cd "$BUILD_DIR"

# ===== 配置选项 =====
CONFIGURE_OPTS="
    --prefix=${INSTALL_DIR}
    --host=${TARGET_TRIPLE}
    --disable-manual
    --disable-verbose
    --disable-debug
    --disable-curldebug
    --disable-ares
    --disable-ldap
    --disable-ldaps
    --disable-rtsp
    --disable-dict
    --disable-telnet
    --disable-tftp
    --disable-pop3
    --disable-imap
    --disable-smb
    --disable-smtp
    --disable-gopher
    --disable-mqtt
    --without-libidn2
    --without-librtmp
    --without-brotli
    --without-zstd
"

if [ "$LINK_TYPE" = "static" ]; then
    CONFIGURE_OPTS="$CONFIGURE_OPTS --disable-shared --enable-static"
else
    CONFIGURE_OPTS="$CONFIGURE_OPTS --enable-shared --disable-static"
fi

# SSL 支持
if [ "$WITH_SSL" = "with-ssl" ]; then
    OPENSSL_DIR="/build/install/openssl-${ARCH}-static"
    if [ -d "$OPENSSL_DIR" ]; then
        CONFIGURE_OPTS="$CONFIGURE_OPTS --with-ssl=${OPENSSL_DIR}"
        export PKG_CONFIG_PATH="${OPENSSL_DIR}/lib/pkgconfig:${PKG_CONFIG_PATH:-}"
    else
        log_warn "OpenSSL not found at ${OPENSSL_DIR}, building without SSL"
        CONFIGURE_OPTS="$CONFIGURE_OPTS --without-ssl"
    fi
else
    CONFIGURE_OPTS="$CONFIGURE_OPTS --without-ssl"
fi

# ===== 配置 =====
log_info "Configuring curl..."
${SRC_DIR}/configure ${CONFIGURE_OPTS}

# ===== 编译 =====
log_info "Building curl..."
make -j$(nproc)

# ===== 安装 =====
log_info "Installing curl..."
make install

# ===== 打包 =====
OUTPUT_DIR="/output/curl/${ARCH}"
mkdir -p "$OUTPUT_DIR"

cd "$INSTALL_DIR"

# Strip 二进制文件
if [ "$LINK_TYPE" = "shared" ]; then
    find lib -name "*.so*" -exec ${STRIP} --strip-unneeded {} \; 2>/dev/null || true
fi

# 创建极致压缩包
if [ "$LINK_TYPE" = "static" ]; then
    tar -cf - include lib/*.a | xz -9e --threads=0 > "${OUTPUT_DIR}/curl-${ARCH}-static.tar.xz"
else
    tar -cf - include lib/*.so* | xz -9e --threads=0 > "${OUTPUT_DIR}/curl-${ARCH}-shared.tar.xz"
fi

log_success "curl build complete!"
log_info "Output: ${OUTPUT_DIR}/curl-${ARCH}-${LINK_TYPE}.tar.xz"
ls -lh "${OUTPUT_DIR}"/*.tar.xz
