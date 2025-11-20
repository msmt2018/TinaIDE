#!/bin/bash
set -e

ABI=x86_64
API_LEVEL=28

echo "Testing ANDROID_PIE setting..."

# 清理
rm -rf /work/build/test-pie
mkdir -p /work/build/test-pie

# 创建测试 CMakeLists.txt
cat > /work/build/test-pie/CMakeLists.txt <<'EOF'
cmake_minimum_required(VERSION 3.10)
project(TestPIE)

message(STATUS "CMAKE_POSITION_INDEPENDENT_CODE = ${CMAKE_POSITION_INDEPENDENT_CODE}")
message(STATUS "ANDROID_PIE = ${ANDROID_PIE}")

add_executable(test main.cpp)
EOF

# 创建测试源文件
cat > /work/build/test-pie/main.cpp <<'EOF'
int main() { return 0; }
EOF

# 测试 1: 不设置 ANDROID_PIE
echo "=== Test 1: Without ANDROID_PIE ==="
cmake -S /work/build/test-pie -B /work/build/test-pie/build1 -G Ninja \
  -DCMAKE_SYSTEM_NAME=Android \
  -DCMAKE_ANDROID_NDK=${ANDROID_NDK_HOME} \
  -DCMAKE_TOOLCHAIN_FILE=${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake \
  -DCMAKE_ANDROID_ARCH_ABI=${ABI} \
  -DCMAKE_ANDROID_API=${API_LEVEL} \
  2>&1 | grep -E "CMAKE_POSITION_INDEPENDENT_CODE|ANDROID_PIE"

# 测试 2: 设置 ANDROID_PIE=OFF
echo "=== Test 2: With ANDROID_PIE=OFF ==="
cmake -S /work/build/test-pie -B /work/build/test-pie/build2 -G Ninja \
  -DCMAKE_SYSTEM_NAME=Android \
  -DCMAKE_ANDROID_NDK=${ANDROID_NDK_HOME} \
  -DCMAKE_TOOLCHAIN_FILE=${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake \
  -DCMAKE_ANDROID_ARCH_ABI=${ABI} \
  -DCMAKE_ANDROID_API=${API_LEVEL} \
  -DANDROID_PIE=OFF \
  2>&1 | grep -E "CMAKE_POSITION_INDEPENDENT_CODE|ANDROID_PIE"

# 检查编译命令
echo "=== Build 1 compile flags ==="
ninja -C /work/build/test-pie/build1 -t commands | grep 'main.cpp' | grep -o '\-fPI[CE]'

echo "=== Build 2 compile flags ==="
ninja -C /work/build/test-pie/build2 -t commands | grep 'main.cpp' | grep -o '\-fPI[CE]'

echo "Done!"
