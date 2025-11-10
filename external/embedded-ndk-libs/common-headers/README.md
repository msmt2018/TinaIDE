Unified LLVM/Clang Headers (for TinaIDE)

This directory is the single source of truth for compiler headers used by the
native_compiler JNI module. Keep all compile-time headers here to avoid
cross-references and duplication.

Layout
- llvm/llvm/              → LLVM source headers (from docker/dev-work/src/llvm-project/llvm/include/)
- llvm/llvm/Config/       → Generated LLVM config headers (from docker/dev-work/build/.../llvm/Config/)
- clang/                  → Clang C++ headers + generated .inc files (from docker/dev-work/build/.../clang/)
- clang-c/                → Clang C API headers
- clang-generated/        → Additional Clang generated files (AST, Sema)

How to refresh headers
Simply run the sync script from project root:
```powershell
.\tools\sync-llvm-headers.ps1
```

This script will automatically:
1. Copy LLVM source headers from docker/dev-work/src/llvm-project/llvm/include/
2. Copy generated LLVM Config headers from docker/dev-work/build/android/x86_64-api21/include/llvm/Config/
3. Copy Clang generated headers (.inc files) from docker/dev-work/build/android/x86_64-api21/tools/clang/
4. Copy LLVM Frontend/OpenMP generated header OMP.inc from docker/dev-work/build/android/*/include/llvm/Frontend/OpenMP/

Manual refresh (if needed)
If the script doesn't work, manually copy:
- LLVM source: docker/.../src/llvm-project/llvm/include/* → llvm/
- LLVM config: docker/.../build/android/x86_64-api21/include/llvm/Config/* → llvm/llvm/Config/
- Clang generated: docker/.../build/android/x86_64-api21/tools/clang/include/clang/* → clang/

Notes
- Runtime libraries (.so files) live in app/src/main/jniLibs/<abi> and are separate
- Do NOT include docker paths directly from CMake; they are excluded by .gitignore
- After syncing, CMake should detect LLVM headers and enable -DLLVM_HEADERS_AVAILABLE=1
- All .inc files are TableGen-generated during the docker LLVM build

Unified sync for all resources
If you also want to sync jniLibs and sysroot besides headers:
```powershell
.# x86_64
 .\tools\sync-embedded-ndk.ps1 -Abi x86_64
 # or arm64-v8a
 .\tools\sync-embedded-ndk.ps1 -Abi arm64-v8a
```
