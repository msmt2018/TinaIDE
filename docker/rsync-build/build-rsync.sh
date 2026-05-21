#!/bin/bash -e

# TinaIDE Rsync Builder Script
# 编译支持 16KB 页面对齐的 Android rsync

set -e

echo "=========================================="
echo "TinaIDE Rsync Builder (16KB Alignment)"
echo "=========================================="
echo ""

# 配置
RSYNC_VERSION=${RSYNC_VERSION:-v3.4.0}
TARGET=${TARGET_ARCH:-aarch64-linux-android}
PLATFORM=21
TOOLCHAIN=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64

echo "[INFO] 配置信息:"
echo "  Rsync 版本: $RSYNC_VERSION"
echo "  目标架构: $TARGET"
echo "  API Level: $PLATFORM"
echo "  NDK 路径: $ANDROID_NDK_HOME"
echo ""

# 检查 NDK
if [ ! -d "$ANDROID_NDK_HOME" ]; then
    echo "[ERROR] Android NDK not found at: $ANDROID_NDK_HOME"
    exit 1
fi

if [ ! -d "$TOOLCHAIN" ]; then
    echo "[ERROR] Toolchain not found at: $TOOLCHAIN"
    exit 1
fi

echo "[OK] NDK 检查通过"
echo ""

# 克隆或更新 rsync 源码
cd /build/src

if [ -d "rsync" ]; then
    echo "[INFO] 使用已存在的 rsync 源码目录"
    cd rsync
    git fetch --tags
    git checkout "$RSYNC_VERSION" || {
        echo "[WARN] 无法切换到 $RSYNC_VERSION，尝试重新克隆..."
        cd /build/src
        rm -rf rsync
        git clone -b "$RSYNC_VERSION" --depth 1 https://github.com/RsyncProject/rsync.git
        cd rsync
    }
else
    echo "[INFO] 克隆 rsync 源码..."
    git clone -b "$RSYNC_VERSION" --depth 1 https://github.com/RsyncProject/rsync.git
    cd rsync
fi

echo "[OK] 源码准备完成"
echo ""

# 配置编译
echo "[INFO] 配置编译选项..."

# 关键：添加 16KB 页面对齐的链接器标志
export LDFLAGS="-Wl,-z,max-page-size=16384"

./configure \
    --host="$TARGET" \
    --disable-md2man \
    --disable-lz4 \
    --disable-openssl \
    --disable-xxhash \
    --disable-zstd \
    AR="$TOOLCHAIN/bin/llvm-ar" \
    CC="$TOOLCHAIN/bin/$TARGET$PLATFORM-clang" \
    RANLIB="$TOOLCHAIN/bin/llvm-ranlib" \
    LDFLAGS="$LDFLAGS"

echo "[OK] 配置完成"
echo ""

# 编译
echo "[INFO] 开始编译..."
make -j$(nproc)
echo "[OK] 编译完成"
echo ""

# Strip 符号
echo "[INFO] 移除调试符号..."
"$TOOLCHAIN/bin/llvm-strip" rsync
echo "[OK] Strip 完成"
echo ""

# 验证 16KB 对齐
echo "[INFO] 验证 16KB 页面对齐..."
if readelf -l rsync | grep -q "0x4000"; then
    echo "[OK] ✓ 16KB 对齐验证通过 (0x4000)"
else
    echo "[WARN] ⚠ 未检测到 16KB 对齐，请检查构建配置"
    readelf -l rsync | grep LOAD | head -3
fi
echo ""

# 复制到输出目录
echo "[INFO] 复制二进制文件到输出目录..."

# 根据架构确定输出目录名
case "$TARGET" in
    aarch64-linux-android)
        ARCH_DIR="arm64-v8a"
        ;;
    armv7a-linux-androideabi)
        ARCH_DIR="armeabi-v7a"
        ;;
    x86_64-linux-android)
        ARCH_DIR="x86_64"
        ;;
    i686-linux-android)
        ARCH_DIR="x86"
        ;;
    *)
        ARCH_DIR="unknown"
        ;;
esac

OUTPUT_DIR="/output/$ARCH_DIR"
mkdir -p "$OUTPUT_DIR"

# 复制并重命名为 librsync.so（Android 约定）
cp rsync "$OUTPUT_DIR/librsync.so"
chmod 755 "$OUTPUT_DIR/librsync.so"

echo "[OK] 输出文件: $OUTPUT_DIR/librsync.so"
echo ""

# 显示文件信息
echo "[INFO] 文件信息:"
ls -lh "$OUTPUT_DIR/librsync.so"
file "$OUTPUT_DIR/librsync.so"
echo ""

# 显示 ELF 段信息
echo "[INFO] ELF LOAD 段信息:"
readelf -l "$OUTPUT_DIR/librsync.so" | grep LOAD | head -3
echo ""

echo "=========================================="
echo "✓ 构建完成！"
echo "=========================================="
echo ""
echo "输出位置: $OUTPUT_DIR/librsync.so"
echo "架构: $ARCH_DIR"
echo "版本: $RSYNC_VERSION"
echo ""
