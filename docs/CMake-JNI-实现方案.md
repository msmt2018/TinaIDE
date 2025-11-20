# CMake JNI 实现方案

## 问题分析

### 为什么 CMake 的 so 构建如此困难？

1. **CMake 不是设计为库**
   - CMake 是一个独立的可执行程序
   - 没有提供库接口（libcmake）
   - 所有功能都在 `main()` 函数中

2. **依赖众多**
   - libuv (事件循环)
   - libarchive (压缩/解压)
   - curl (网络下载)
   - nghttp2 (HTTP/2)
   - expat (XML 解析)
   - jsoncpp (JSON 解析)
   - rhash (哈希计算)

3. **PIC 问题**
   - 默认构建不使用 `-fPIC`
   - 无法直接链接成共享库
   - 需要重新编译所有依赖

## 可行的解决方案

### 方案 A: 使用 CMake 可执行文件 + Shell 包装器 ⭐⭐⭐

**现状**: 
- ✅ 已经有 cmake 可执行文件
- ❌ 但在 Android 10+ 上无法执行

**问题**: SELinux 阻止执行

**可能的绕过方式**:
1. 使用 `LD_PRELOAD` 注入代码？（不可行）
2. 使用 `ptrace` 附加？（不可行）
3. 修改 SELinux 策略？（需要 root）

**结论**: ❌ 不可行

### 方案 B: 构建真正的 libcmake_runner.so ⭐⭐⭐⭐⭐

**实现步骤**:

#### 1. 修改 CMake 源码

创建一个新的入口点文件：

```cpp
// cmake_jni_wrapper.cpp
#include <vector>
#include <string>

// CMake 的 main 函数
extern "C" int main(int argc, char** argv);

// JNI 入口点
extern "C" int cmake_run(int argc, char** argv) {
    return main(argc, argv);
}
```

#### 2. 构建配置

```bash
cmake -S /work/src/cmake -B /work/build/cmake-so -G Ninja \
  -DCMAKE_SYSTEM_NAME=Android \
  -DCMAKE_TOOLCHAIN_FILE=${NDK}/build/cmake/android.toolchain.cmake \
  -DCMAKE_ANDROID_ARCH_ABI=x86_64 \
  -DCMAKE_ANDROID_API=28 \
  -DCMAKE_BUILD_TYPE=MinSizeRel \
  -DBUILD_SHARED_LIBS=OFF \
  -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
  -DCMAKE_C_FLAGS="-fPIC" \
  -DCMAKE_CXX_FLAGS="-fPIC"

# 构建所有库
ninja -C /work/build/cmake-so

# 手动链接成 so
clang++ -shared -fPIC \
  -o libcmake_runner.so \
  cmake_jni_wrapper.cpp \
  /work/build/cmake-so/Source/CMakeFiles/cmake.dir/*.o \
  /work/build/cmake-so/Source/CMakeFiles/CMakeLib.dir/**/*.o \
  /work/build/cmake-so/Utilities/**/*.a \
  -llog -landroid
```

#### 3. 预期问题

- **链接错误**: 可能有未定义的符号
- **体积巨大**: 所有依赖都会被包含
- **构建时间长**: 需要重新编译所有依赖

### 方案 C: 简化的 CMake 替代品 ⭐⭐⭐⭐

**思路**: 不使用完整的 CMake，而是实现一个简化版本

**功能**:
- 解析 `CMakeLists.txt`（简化版）
- 生成 `build.ninja` 文件
- 支持基本的 CMake 命令

**优点**:
- 体积小
- 易于构建成 so
- 可控性强

**缺点**:
- 功能有限
- 兼容性问题
- 开发工作量大

### 方案 D: 跳过 CMake，直接使用 Ninja ⭐⭐⭐⭐⭐

**思路**: 不运行 CMake 配置，直接提供预生成的 `build.ninja`

**实现**:

1. **在开发机上生成 build.ninja**
   ```bash
   # 在 PC 上运行
   cmake -S . -B build -G Ninja \
     -DCMAKE_TOOLCHAIN_FILE=android.toolchain.cmake \
     -DANDROID_ABI=arm64-v8a
   
   # 复制 build.ninja 到项目
   cp build/build.ninja app/src/main/assets/templates/
   ```

2. **在 Android 上使用模板**
   ```kotlin
   // 复制模板
   val template = assets.open("templates/build.ninja").readText()
   
   // 替换路径变量
   val buildNinja = template
       .replace("\${PROJECT_DIR}", projectDir.absolutePath)
       .replace("\${NDK_PATH}", ndkPath)
   
   // 写入构建目录
   File(buildDir, "build.ninja").writeText(buildNinja)
   
   // 直接运行 Ninja
   NinjaRunner.runNinja(buildDir.absolutePath, arrayOf())
   ```

**优点**:
- ✅ 完全绕过 CMake
- ✅ 只需要 Ninja（已经有 so）
- ✅ 实现简单

**缺点**:
- ⚠️ 需要预生成模板
- ⚠️ 灵活性降低
- ⚠️ 用户无法自定义配置

### 方案 E: 使用 CMake Server/File API ⭐⭐⭐

**思路**: 在服务器端运行 CMake，生成配置文件

**实现**:
1. 用户上传 `CMakeLists.txt` 到服务器
2. 服务器运行 CMake 生成 `build.ninja`
3. 用户下载 `build.ninja` 到 Android
4. 使用 Ninja 构建

**优点**:
- ✅ 完全绕过 Android 上的 CMake
- ✅ 可以使用完整的 CMake 功能

**缺点**:
- ❌ 需要网络连接
- ❌ 隐私问题
- ❌ 服务器成本

## 推荐方案

### 短期（立即实施）: 方案 D - 跳过 CMake

**理由**:
1. 最简单、最快速
2. 利用已有的 Ninja JNI
3. 可以快速验证可行性

**实施步骤**:
1. 创建 `build.ninja` 模板
2. 实现模板变量替换
3. 集成到 `CMakeProjectCompiler`
4. 测试基本的 C++ 项目

### 中期（1-2周）: 方案 B - 真正的 libcmake_runner.so

**理由**:
1. 提供完整的 CMake 功能
2. 用户体验最好
3. 长期可维护

**实施步骤**:
1. 修改 `build-tools.ps1`，正确构建 PIC 版本
2. 创建 JNI 包装器
3. 处理链接问题
4. 优化体积和性能

### 长期（1-2月）: 方案 C - 简化的 CMake

**理由**:
1. 体积小、性能好
2. 完全可控
3. 可以针对 Android 优化

**实施步骤**:
1. 设计简化的 CMake 语法
2. 实现解析器
3. 实现 Ninja 生成器
4. 测试兼容性

## 立即行动：实现方案 D

让我创建一个 `build.ninja` 模板生成器：

### 1. 模板结构

```ninja
# build.ninja template for Android C++ projects

# Variables (will be replaced at runtime)
project_dir = ${PROJECT_DIR}
ndk_path = ${NDK_PATH}
build_dir = ${BUILD_DIR}
abi = ${ABI}
api_level = ${API_LEVEL}

# Toolchain
cc = $ndk_path/toolchains/llvm/prebuilt/linux-x86_64/bin/clang
cxx = $ndk_path/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++
ar = $ndk_path/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar

# Flags
cflags = -target aarch64-linux-android$api_level -fPIC
cxxflags = $cflags -std=c++17
ldflags = -shared

# Rules
rule cc
  command = $cc $cflags -c $in -o $out
  description = CC $out

rule cxx
  command = $cxx $cxxflags -c $in -o $out
  description = CXX $out

rule link
  command = $cxx $ldflags $in -o $out
  description = LINK $out

# Build targets (will be generated based on source files)
build $build_dir/main.o: cxx $project_dir/main.cpp
build $build_dir/libapp.so: link $build_dir/main.o
```

### 2. Kotlin 实现

```kotlin
class NinjaBuildGenerator {
    fun generateBuildNinja(
        projectDir: File,
        buildDir: File,
        sourceFiles: List<File>,
        targetName: String,
        abi: String = "arm64-v8a",
        apiLevel: Int = 28
    ): File {
        val ndkPath = getNdkPath()
        
        val content = buildString {
            appendLine("# Generated build.ninja")
            appendLine()
            
            // Variables
            appendLine("project_dir = ${projectDir.absolutePath}")
            appendLine("ndk_path = $ndkPath")
            appendLine("build_dir = ${buildDir.absolutePath}")
            appendLine("abi = $abi")
            appendLine("api_level = $apiLevel")
            appendLine()
            
            // Toolchain
            val triple = when (abi) {
                "arm64-v8a" -> "aarch64-linux-android"
                "x86_64" -> "x86_64-linux-android"
                else -> throw IllegalArgumentException("Unsupported ABI: $abi")
            }
            appendLine("cc = \$ndk_path/toolchains/llvm/prebuilt/linux-x86_64/bin/$triple\$api_level-clang")
            appendLine("cxx = \$ndk_path/toolchains/llvm/prebuilt/linux-x86_64/bin/$triple\$api_level-clang++")
            appendLine()
            
            // Flags
            appendLine("cflags = -fPIC")
            appendLine("cxxflags = \$cflags -std=c++17")
            appendLine("ldflags = -shared")
            appendLine()
            
            // Rules
            appendLine("rule cc")
            appendLine("  command = \$cc \$cflags -c \$in -o \$out")
            appendLine("  description = CC \$out")
            appendLine()
            appendLine("rule cxx")
            appendLine("  command = \$cxx \$cxxflags -c \$in -o \$out")
            appendLine("  description = CXX \$out")
            appendLine()
            appendLine("rule link")
            appendLine("  command = \$cxx \$ldflags \$in -o \$out")
            appendLine("  description = LINK \$out")
            appendLine()
            
            // Build targets
            val objFiles = mutableListOf<String>()
            sourceFiles.forEach { src ->
                val objName = src.nameWithoutExtension + ".o"
                val objPath = "\$build_dir/$objName"
                objFiles.add(objPath)
                
                val rule = if (src.extension == "c") "cc" else "cxx"
                appendLine("build $objPath: $rule ${src.absolutePath}")
            }
            appendLine()
            
            // Link
            appendLine("build \$build_dir/lib$targetName.so: link ${objFiles.joinToString(" ")}")
            appendLine()
            
            // Default target
            appendLine("default \$build_dir/lib$targetName.so")
        }
        
        return File(buildDir, "build.ninja").apply {
            writeText(content)
        }
    }
}
```

这个方案可以立即实施，不需要等待 CMake 的 so 构建完成！

---

**更新时间**: 2025-11-19  
**推荐方案**: 方案 D（跳过 CMake，直接使用 Ninja）  
**下一步**: 实现 `NinjaBuildGenerator`
