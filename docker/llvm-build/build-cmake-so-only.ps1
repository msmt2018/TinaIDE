Param(
  [ValidateSet('arm64-v8a','x86_64')][string]$Abi = 'x86_64',
  [int]$ApiLevel = 28,
  [string]$ContainerName = 'tina-llvm-build',
  [string]$OutputPath,
  [string]$CMakeTag = 'v3.31.4'
)

$ErrorActionPreference = 'Stop'

function Write-Info($msg) { Write-Host "[i] $msg" -ForegroundColor Cyan }
function Write-Err($msg)  { Write-Host "[!] $msg" -ForegroundColor Red }

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Resolve-Path (Join-Path $scriptRoot '..\..')

if (-not $OutputPath -or [string]::IsNullOrWhiteSpace($OutputPath)) {
  $OutputPath = Join-Path $root 'docker/llvm-build/build-output'
}
$outBase = (Resolve-Path $OutputPath).Path
$outDirHost = Join-Path $outBase $Abi
New-Item -ItemType Directory -Force -Path (Join-Path $outDirHost 'tools/bin') | Out-Null

# Ensure dev container is running
$running = (& docker ps --format '{{.Names}}' | Select-String -SimpleMatch $ContainerName) -ne $null
if (-not $running) {
  Write-Err "Dev container '$ContainerName' not running. Please run docker/llvm-build/build-local.ps1 once to create it."
  exit 2
}

function Exec-In-Dev { param([string]$cmd) & docker exec $ContainerName bash -lc $cmd }

$assign = "ABI='$Abi'; API_LEVEL='$ApiLevel';"
$session = @'
set -eux
case "${ABI}" in
  arm64-v8a) TRIPLE=aarch64-linux-android;;
  x86_64)    TRIPLE=x86_64-linux-android;;
  *) echo "Unsupported ABI: ${ABI}"; exit 1;;
esac

NDK_CLANGXX="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/${TRIPLE}${API_LEVEL}-clang++"

# 确保 CMake 源码存在
if [ ! -d /work/src/cmake/.git ]; then
  : ${CMAKE_TAG:=v3.31.4}
  git clone --depth=1 --branch "${CMAKE_TAG}" https://github.com/Kitware/CMake.git /work/src/cmake || \
  git clone --depth=1 https://github.com/Kitware/CMake.git /work/src/cmake
fi

# 应用必要的补丁（android_lf.h）
if [ ! -f /work/src/cmake/Utilities/cmlibarchive/contrib/android/include/android_lf.h ]; then
  mkdir -p /work/src/cmake/Utilities/cmlibarchive/contrib/android/include
  cat > /work/src/cmake/Utilities/cmlibarchive/contrib/android/include/android_lf.h <<'EOF'
#ifndef ARCHIVE_ANDROID_LF_H_INCLUDED
#define ARCHIVE_ANDROID_LF_H_INCLUDED
#if __ANDROID_API__ > 20
# include <dirent.h>
# include <fcntl.h>
# include <unistd.h>
# include <sys/stat.h>
# include <sys/statvfs.h>
# include <sys/types.h>
# include <sys/vfs.h>
# define readdir  readdir64
# define dirent   dirent64
# define openat   openat64
# define open     open64
# define mkstemp  mkstemp64
# define lseek    lseek64
# define ftruncate ftruncate64
# define fstatat  fstatat64
# define fstat    fstat64
# define lstat    lstat64
# define stat     stat64
# define fstatvfs fstatvfs64
# define statvfs  statvfs64
# define off_t    off64_t
# define fstatfs  fstatfs64
# define statfs   statfs64
#endif
#endif
EOF
fi

# 应用 libuv 补丁（Android 支持）
if grep -q 'if(CMAKE_SYSTEM_NAME STREQUAL "Linux")' /work/src/cmake/Utilities/cmlibuv/CMakeLists.txt; then
  sed -i 's/if(CMAKE_SYSTEM_NAME STREQUAL "Linux")/if(CMAKE_SYSTEM_NAME STREQUAL "Linux" OR CMAKE_SYSTEM_NAME STREQUAL "Android")/' \
    /work/src/cmake/Utilities/cmlibuv/CMakeLists.txt
  sed -i 's/list(APPEND uv_libraries dl rt)/list(APPEND uv_libraries dl $<$<STREQUAL:${CMAKE_SYSTEM_NAME},Linux>:rt>)/' \
    /work/src/cmake/Utilities/cmlibuv/CMakeLists.txt
fi

# 创建 uv_android_compat.h
mkdir -p /work/src/cmake/Utilities/cmlibuv/include
cat > /work/src/cmake/Utilities/cmlibuv/include/uv_android_compat.h <<'EOF'
#ifndef UV_ANDROID_COMPAT_H_
#define UV_ANDROID_COMPAT_H_
#ifdef __ANDROID__
# include <sched.h>
# include <pthread.h>
# include <errno.h>
static inline int pthread_setaffinity_np(pthread_t thread,
                                         size_t cpusetsize,
                                         const cpu_set_t* mask) {
  (void)thread;
  return sched_setaffinity(0, cpusetsize, mask);
}
static inline int pthread_getaffinity_np(pthread_t thread,
                                         size_t cpusetsize,
                                         cpu_set_t* mask) {
  (void)thread;
  return sched_getaffinity(0, cpusetsize, mask);
}
#endif
#endif
EOF

echo "[i] Building PIC version of CMake for libcmake_runner.so..."

# 强制清理旧的 PIC 构建目录
echo "[i] Cleaning old build directory..."
rm -rf /work/build/tools/cmake-${ABI}-api${API_LEVEL}-pic
mkdir -p /work/build/tools/cmake-${ABI}-api${API_LEVEL}-pic

# 配置 PIC 版本的 CMake
# 关键：明确禁用 Android toolchain 的 PIE 设置
echo "[i] Configuring CMake with -DANDROID_PIE=OFF..."
cmake -S /work/src/cmake -B /work/build/tools/cmake-${ABI}-api${API_LEVEL}-pic -G Ninja \
  -DCMAKE_SYSTEM_NAME=Android -DCMAKE_ANDROID_NDK=${ANDROID_NDK_HOME} \
  -DCMAKE_TOOLCHAIN_FILE=${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake \
  -DCMAKE_ANDROID_ARCH_ABI=${ABI} -DCMAKE_ANDROID_API=${API_LEVEL} \
  -DANDROID_ABI=${ABI} -DANDROID_PLATFORM=android-${API_LEVEL} \
  -DANDROID_PIE=OFF \
  -DCMAKE_BUILD_TYPE=MinSizeRel \
  -DBUILD_TESTING=OFF -DBUILD_CursesDialog=OFF \
  -DCMAKE_USE_OPENSSL=OFF -DCMAKE_USE_SYSTEM_CURL=OFF \
  -DBUILD_SHARED_LIBS=OFF \
  -DCMAKE_C_FLAGS="-fPIC -fno-PIE -D_GNU_SOURCE -include /work/src/cmake/Utilities/cmlibuv/include/uv_android_compat.h" \
  -DCMAKE_CXX_FLAGS="-fPIC -fno-PIE -D_GNU_SOURCE -include /work/src/cmake/Utilities/cmlibuv/include/uv_android_compat.h" \
  -DCMAKE_EXE_LINKER_FLAGS="-fPIC -no-pie" \
  -DCMAKE_SHARED_LINKER_FLAGS="-fPIC"

echo "[i] CMake configuration completed. Checking build.ninja..."
if [ ! -f /work/build/tools/cmake-${ABI}-api${API_LEVEL}-pic/build.ninja ]; then
  echo "[!] ERROR: build.ninja not generated! CMake configuration failed."
  exit 1
fi

# Android toolchain still appends -fPIE for executable targets (cmake/cmcmd),
# which later breaks when re-linking into libcmake_runner.so (ld.lld refuses PIE
# objects inside shared libraries). Normalize these compile flags before building.
echo "[i] Normalizing cmake target compile flags (-fPIE -> -fPIC)..."
PATCHED=0
if grep -q -- "-fPIE -pthread" /work/build/tools/cmake-${ABI}-api${API_LEVEL}-pic/build.ninja; then
  sed -i 's/-fPIE -pthread/-fPIC -pthread/g' /work/build/tools/cmake-${ABI}-api${API_LEVEL}-pic/build.ninja
  PATCHED=1
fi
if [ ${PATCHED} -eq 1 ]; then
  echo "[i] Patched cmake target to emit PIC objects."
else
  echo "[w] Expected -fPIE pattern not found; skipping PIC patch."
fi

echo "[i] build.ninja found. Building CMakeLib and cmake targets..."

# 构建 CMakeLib 和 cmake 目标
ninja -C /work/build/tools/cmake-${ABI}-api${API_LEVEL}-pic -j$(nproc) CMakeLib cmake

echo "[i] Build completed. Collecting PIC object files..."

# 收集 PIC 对象文件
mkdir -p /work/build/tools/cmake-runner
cat > /work/build/tools/cmake-runner/cmake_runner.cpp <<'EOF'
#include <android/log.h>

extern "C" int main(int, char**);
extern "C" int cmake_run(int argc, char** argv) { return main(argc, argv); }
EOF

cmake_objs=$(find /work/build/tools/cmake-${ABI}-api${API_LEVEL}-pic -path '*/Source/CMakeFiles/cmake.dir/*' -name '*.o' | xargs echo)
cmake_lib_objs=$(find /work/build/tools/cmake-${ABI}-api${API_LEVEL}-pic -path '*/Source/CMakeFiles/CMakeLib.dir/*' -name '*.o' | xargs echo)

echo "[i] cmake_objs count: $(echo ${cmake_objs} | wc -w)"
echo "[i] cmake_lib_objs count: $(echo ${cmake_lib_objs} | wc -w)"

if [ -n "${cmake_objs}" ] && [ -n "${cmake_lib_objs}" ]; then
  echo "[i] Found CMake PIC objects, linking libcmake_runner.so..."
  
  # 直接使用 NDK 提供的 libc++_shared.so（已经是 PIC 编译的）
  # 关键参数：-lc++_shared 告诉链接器使用动态 C++ 库而不是静态库
  
  echo "[i] Linking with NDK's libc++_shared.so..."
  ${NDK_CLANGXX} -shared -fPIC -Wl,-z,now -Wl,-z,relro \
    -o /hostout/${ABI}/tools/bin/libcmake_runner.so \
    ${cmake_objs} ${cmake_lib_objs} /work/build/tools/cmake-runner/cmake_runner.cpp \
    -lc++_shared -llog -landroid -ldl -lm
  
  if [ $? -ne 0 ]; then
    echo "[!] Linking failed! Check the error messages above."
    exit 1
  fi
  
  # 复制 libc++_shared.so 到输出目录（运行时需要）
  LIBCXX_SHARED="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/${TRIPLE}/libc++_shared.so"
  if [ -f "${LIBCXX_SHARED}" ]; then
    cp -f "${LIBCXX_SHARED}" /hostout/${ABI}/tools/bin/
    echo "[i] Copied libc++_shared.so to output (required at runtime)"
  else
    echo "[w] Warning: libc++_shared.so not found at ${LIBCXX_SHARED}"
  fi
  
  if [ -f /hostout/${ABI}/tools/bin/libcmake_runner.so ]; then
    echo "[i] Successfully built libcmake_runner.so"
    ls -lh /hostout/${ABI}/tools/bin/libcmake_runner.so
  fi
else
  echo "[w] Could not locate cmake PIC objects; skipping libcmake_runner.so build"
  exit 1
fi
'@

Write-Info "Building CMake .so for ABI=$Abi (API=$ApiLevel) in container: $ContainerName"
$cmd = @"
CMAKE_TAG='$CMakeTag'
$assign
$session
"@
# Remove Windows line endings (CRLF -> LF) before sending to Docker
$cmd = $cmd -replace "`r`n", "`n"
Exec-In-Dev $cmd
Write-Info "CMake .so built. Output at: $(Join-Path $outDirHost 'tools/bin/libcmake_runner.so')"
Write-Info "Done."
