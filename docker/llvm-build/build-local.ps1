Param(
  [ValidateSet('arm64-v8a','x86_64')][string]$Abi = 'arm64-v8a',
  [int]$ApiLevel = 24,
  [string]$NdkVersion = 'r26d',
  [string]$LlvmTag = 'llvmorg-17.0.6',
  [string]$ContainerName = 'tina-llvm-build',
  [string]$OutputPath
)

$ErrorActionPreference = 'Stop'

function Write-Info($msg) { Write-Host "[i] $msg" -ForegroundColor Cyan }
function Write-Err($msg)  { Write-Host "[!] $msg" -ForegroundColor Red }

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Resolve-Path (Join-Path $scriptRoot '..\..')

# Determine output directory (allow override via parameter)
if (-not $OutputPath -or [string]::IsNullOrWhiteSpace($OutputPath)) {
  $OutputPath = Join-Path $root 'docker/llvm-build/build-output'
}

# Ensure output directory exists
New-Item -ItemType Directory -Force -Path $OutputPath | Out-Null
$outputBase = (Resolve-Path $OutputPath).Path

function Ensure-DevContainer {
  param([string]$containerName,[string]$ndkVersion)
  $devImage = "llvm-build-dev:$ndkVersion"
  Write-Info "Ensuring dev image: $devImage"
  & docker build -f (Join-Path $root 'docker/llvm-build/Dockerfile.dev') --build-arg NDK_VERSION=$ndkVersion -t $devImage $root
  $running = (& docker ps --format '{{.Names}}' | Select-String -SimpleMatch $containerName) -ne $null
  if (-not $running) {
    $exists = (& docker ps -a --format '{{.Names}}' | Select-String -SimpleMatch $containerName) -ne $null
    if ($exists) { & docker rm -f $containerName | Out-Null }
    $workHost = Join-Path $root 'docker/llvm-build/dev-work'
    New-Item -ItemType Directory -Force -Path $workHost | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $workHost 'src'), (Join-Path $workHost 'build'), (Join-Path $workHost 'out') | Out-Null
    Write-Info "Starting dev container: $containerName"
    & docker run -d --name $containerName -w /work `
      -v "$($workHost):/work" `
      -v "$($outputBase):/hostout" `
      $devImage | Out-Null
  }
}

function Exec-In-Dev { param([string]$cmd) & docker exec $ContainerName bash -lc $cmd }

Ensure-DevContainer -containerName $ContainerName -ndkVersion $NdkVersion

$outDirHost = Join-Path $OutputPath "${Abi}"
New-Item -ItemType Directory -Force -Path $outDirHost | Out-Null
$assign = "ABI='$Abi'; API_LEVEL='$ApiLevel'; LLVM_TAG='$LlvmTag'; NDK_VERSION='$NdkVersion';"
$sessionScript = @'
set -eux
case "${ABI}" in
  arm64-v8a) TRIPLE=aarch64-linux-android; LLVM_TARGET=AArch64;;
  x86_64)    TRIPLE=x86_64-linux-android; LLVM_TARGET=X86;;
  *) echo "Unsupported ABI: ${ABI}"; exit 1;;
esac
mkdir -p /work/src /work/build/host /work/build/android/${ABI}-api${API_LEVEL} /hostout/${ABI}
if [ ! -d /work/src/llvm-project/.git ]; then
  git clone --depth=1 --branch ${LLVM_TAG} https://github.com/llvm/llvm-project.git /work/src/llvm-project
fi
if [ ! -x /work/build/host/bin/llvm-tblgen ]; then
  cmake -S /work/src/llvm-project/llvm -B /work/build/host -G Ninja \
    -DLLVM_ENABLE_PROJECTS="clang;lld" -DLLVM_TARGETS_TO_BUILD="AArch64;X86" -DCMAKE_BUILD_TYPE=Release
  ninja -C /work/build/host -j$(nproc) llvm-tblgen clang-tblgen
fi
cmake -S /work/src/llvm-project/llvm -B /work/build/android/${ABI}-api${API_LEVEL} -G Ninja \
  -DLLVM_ENABLE_PROJECTS="clang;lld" -DLLVM_TARGETS_TO_BUILD="${LLVM_TARGET}" -DCMAKE_BUILD_TYPE=MinSizeRel \
  -DCMAKE_SYSTEM_NAME=Android -DCMAKE_ANDROID_NDK=${ANDROID_NDK_HOME} -DCMAKE_ANDROID_ARCH_ABI=${ABI} -DCMAKE_ANDROID_API=${API_LEVEL} \
  -DLLVM_TABLEGEN=/work/build/host/bin/llvm-tblgen -DCLANG_TABLEGEN=/work/build/host/bin/clang-tblgen \
  -DLLVM_INCLUDE_TESTS=OFF -DLLVM_INCLUDE_EXAMPLES=OFF -DLLVM_INCLUDE_BENCHMARKS=OFF \
  -DLLVM_ENABLE_TERMINFO=OFF -DLLVM_ENABLE_LIBEDIT=OFF -DLLVM_ENABLE_CURSES=OFF \
  -DLLVM_ENABLE_ZLIB=OFF -DLLVM_ENABLE_ZSTD=OFF -DLLVM_ENABLE_LIBXML2=OFF \
  -DCLANG_ENABLE_ARCMT=OFF -DCLANG_ENABLE_STATIC_ANALYZER=OFF \
  -DLLVM_BUILD_TOOLS=OFF -DLLVM_BUILD_LLVM_DYLIB=ON -DLLVM_LINK_LLVM_DYLIB=ON -DCLANG_LINK_CLANG_DYLIB=ON \
  -DLLVM_ENABLE_ASSERTIONS=OFF -DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON
ninja -C /work/build/android/${ABI}-api${API_LEVEL} -j$(nproc) clang-cpp lld

# Clean destination to avoid duplicate/readonly collisions on host mounts
rm -rf /hostout/${ABI}/libs/${ABI} /hostout/${ABI}/sysroot /hostout/${ABI}/include || true
mkdir -p /hostout/${ABI}/libs/${ABI} /hostout/${ABI}/sysroot/usr/include /hostout/${ABI}/sysroot/usr/lib/${TRIPLE}/${API_LEVEL}
mkdir -p /hostout/${ABI}/include/clang-c /hostout/${ABI}/include/clang /hostout/${ABI}/include/llvm /hostout/${ABI}/include/lld
cp -af /work/build/android/${ABI}-api${API_LEVEL}/lib/libclang-cpp.so* /hostout/${ABI}/libs/${ABI}/ || true
cp -af /work/build/android/${ABI}-api${API_LEVEL}/lib/libLLVM*.so*    /hostout/${ABI}/libs/${ABI}/ || true
if compgen -G "/work/build/android/${ABI}-api${API_LEVEL}/lib/liblld*.so*" > /dev/null; then
  cp -af /work/build/android/${ABI}-api${API_LEVEL}/lib/liblld*.so* /hostout/${ABI}/libs/${ABI}/
fi
# strip .so to minimize size (use NDK's llvm-strip)
if [ -x "${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" ]; then
  STRIP_BIN="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
  for so in /hostout/${ABI}/libs/${ABI}/*.so*; do
    [ -f "$so" ] && $STRIP_BIN -S "$so" || true
  done
fi
if [ -d "${ANDROID_NDK_HOME}/sources/cxx-stl/llvm-libc++/libs/${ABI}" ]; then
  cp -af ${ANDROID_NDK_HOME}/sources/cxx-stl/llvm-libc++/libs/${ABI}/libc++_shared.so /hostout/${ABI}/libs/${ABI}/ || true
fi
cp -af ${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/. /hostout/${ABI}/sysroot/usr/include/
# Ensure libc++ headers are present under sysroot/usr/include/c++/v1 (NDK layout varies by version)
mkdir -p /hostout/${ABI}/sysroot/usr/include/c++/v1 || true
# Try prebuilt include path (newer NDKs)
cp -af ${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/include/c++/v1/. /hostout/${ABI}/sysroot/usr/include/c++/v1/ 2>/dev/null || true
# If still missing, try legacy sources path (older NDKs)
if [ ! -f /hostout/${ABI}/sysroot/usr/include/c++/v1/__ios/fpos.h ]; then
  if [ -d "${ANDROID_NDK_HOME}/sources/cxx-stl/llvm-libc++/include" ]; then
    cp -af ${ANDROID_NDK_HOME}/sources/cxx-stl/llvm-libc++/include/. /hostout/${ABI}/sysroot/usr/include/c++/v1/
  fi
fi
# Final verification: fail early if libc++ core headers not found
if [ ! -f /hostout/${ABI}/sysroot/usr/include/c++/v1/__ios/fpos.h ]; then
  echo "[ERROR] libc++ headers not found in NDK. Checked prebuilt include/c++/v1 and sources/cxx-stl/llvm-libc++/include" >&2
  exit 2
fi

# Copy Clang resource headers (builtin headers like stdarg.h) into sysroot/lib/clang/<ver>/include
verdir=$(ls -d /work/build/android/${ABI}-api${API_LEVEL}/lib/clang/* 2>/dev/null | head -n1 || true)
if [ -n "$verdir" ] && [ -d "$verdir/include" ]; then
  verbase=$(basename "$verdir")
  # Place resource headers under the real version dir
  mkdir -p /hostout/${ABI}/sysroot/lib/clang/${verbase}
  cp -af "$verdir/include" /hostout/${ABI}/sysroot/lib/clang/${verbase}/
  # Also provide a stable alias "17" for runtime -resource-dir compatibility
  major=${verbase%%.*}
  [ -z "$major" ] && major=17
  mkdir -p /hostout/${ABI}/sysroot/lib/clang/${major}
  cp -af "$verdir/include" /hostout/${ABI}/sysroot/lib/clang/${major}/
else
  echo "[WARN] Clang resource headers not found under build output; falling back to clang/lib/Headers" >&2
  # Fallback: copy from clang source tree
  if [ -d /work/src/llvm-project/clang/lib/Headers ]; then
    mkdir -p /hostout/${ABI}/sysroot/lib/clang/17/include
    cp -af /work/src/llvm-project/clang/lib/Headers/. /hostout/${ABI}/sysroot/lib/clang/17/include/
  else
    echo "[ERROR] No clang resource headers available (build and source both missing)" >&2
    exit 2
  fi
fi
cp -af ${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/${TRIPLE}/${API_LEVEL}/. /hostout/${ABI}/sysroot/usr/lib/${TRIPLE}/${API_LEVEL}/
cp -af /work/src/llvm-project/clang/include/clang-c/. /hostout/${ABI}/include/clang-c/ || true
cp -a /work/src/llvm-project/clang/include/. /hostout/${ABI}/include/clang/ || true
cp -a /work/src/llvm-project/llvm/include/.  /hostout/${ABI}/include/llvm/  || true
cp -a /work/src/llvm-project/lld/include/.   /hostout/${ABI}/include/lld/   || true
cp -a /work/build/android/${ABI}-api${API_LEVEL}/include/.          /hostout/${ABI}/include/       || true
printf "MODE=shared-libs\nNDK=%s\nLLVM_TAG=%s\nABI=%s\nAPI_LEVEL=%s\nTRIPLE=%s\n" "${NDK_VERSION}" "${LLVM_TAG}" "${ABI}" "${API_LEVEL}" "${TRIPLE}" > /hostout/${ABI}/MANIFEST
(cd /hostout/${ABI} && find . -type f -print0 | sort -z | xargs -0 sha256sum) > /hostout/${ABI}/SHA256SUMS || true
(cd /hostout/${ABI} && zip -qr llvm-build-${ABI}-api${API_LEVEL}.zip .)
'@
Exec-In-Dev "$assign`n$sessionScript"
Write-Info "Build completed!"
Write-Info "Artifacts ready at: $outDirHost"

Write-Info "Done."
