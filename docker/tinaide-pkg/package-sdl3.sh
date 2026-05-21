#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/output"
ASSETS_DIR="${SCRIPT_DIR}/../../app/src/main/assets/bundled_packages"

echo "[INFO] Packaging SDL3 for assets..."

# 创建临时目录
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

# 解压 SDL3
TARBALL="${OUTPUT_DIR}/sdl3/arm64-v8a/sdl3-arm64-v8a-shared.tar.xz"
echo "[INFO] Extracting ${TARBALL}..."
tar -xf "$TARBALL" -C "$TEMP_DIR"

# 重组目录结构
mkdir -p "$TEMP_DIR/final/lib/arm64-v8a"
mkdir -p "$TEMP_DIR/final/pkgconfig"

# 移动文件
mv "$TEMP_DIR/include" "$TEMP_DIR/final/"
mv "$TEMP_DIR/lib"/*.so* "$TEMP_DIR/final/lib/arm64-v8a/"
if [ -f "$TEMP_DIR/lib/pkgconfig/sdl3.pc" ]; then
    sed 's|libdir=.*|libdir=${prefix}/lib/${ANDROID_ABI}|' \
        "$TEMP_DIR/lib/pkgconfig/sdl3.pc" \
        > "$TEMP_DIR/final/pkgconfig/sdl3.pc"
fi

# 创建 package.json
cat > "$TEMP_DIR/final/package.json" << 'EOF'
{
  "id": "sdl3",
  "name": "SDL3",
  "version": "3.1.6",
  "description": "Simple DirectMedia Layer 3 - Cross-platform multimedia library",
  "platform": "android",
  "installType": "download",
  "category": "library",
  "homepage": "https://www.libsdl.org/",
  "license": "Zlib",
  "installedAt": 1740621600000,
  "files": {
    "include": "include/SDL3",
    "lib": "lib",
    "pkgconfig": "pkgconfig/sdl3.pc"
  },
  "abis": ["arm64-v8a"]
}
EOF

# 使用 PowerShell 创建 zip
mkdir -p "$ASSETS_DIR"
TEMP_FINAL_WIN=$(cygpath -w "$TEMP_DIR/final")
ASSETS_ZIP_WIN=$(cygpath -w "$ASSETS_DIR/sdl3.zip")

echo "[INFO] Creating zip archive..."
powershell.exe -Command "Compress-Archive -Path '${TEMP_FINAL_WIN}\*' -DestinationPath '${ASSETS_ZIP_WIN}' -Force"

echo "[SUCCESS] Package created!"
ls -lh "$ASSETS_DIR/sdl3.zip"

