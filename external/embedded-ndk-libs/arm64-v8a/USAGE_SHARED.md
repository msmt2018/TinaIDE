使用说明（共享库模式）

目标
- 以 .so 形式在 Android App 内加载 Clang/LLVM/LLD，避免对可执行文件的执行权限限制。

产物布局
- libs/<abi>/libclang-cpp.so, libLLVM*.so, liblld*.{so,a}, libc++_shared.so
- sysroot/usr/{include,lib/<triple>/<api>}
- include/clang-c/*（如需使用 libclang C API）
- MANIFEST, SHA256SUMS

加载建议
- 将 libs/<abi> 内容放入应用的 jniLibs/<abi>，通过 System.loadLibrary 加载；
- sysroot 作为数据资源放入 assets（或解压到 files/），仅用于编译时 include/链接路径；
- 注意：libclang-cpp/LLVM/LLD 属于非稳定内部 API，建议封装稳定入口并固定版本。

典型集成方式
1) 在 JNI 层提供最小封装，暴露“编译/链接”函数，App 层调用；
2) 编译：驱动 clang Frontend（libclang-cpp）并设置 --target/--sysroot 等；
3) 链接：通过 LLD 库（如 ELF）调用对应入口进行链接（例如 lld::elf::link）。

