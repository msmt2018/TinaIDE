# Android SELinux 限制说明

## 核心问题

在 Android 10+ (API 29+) 上，应用沙箱内**无法执行普通的可执行文件**。

### 错误现象

```bash
# 尝试执行任何可执行文件都会失败
/data/data/com.example.app/files/cmake
# 错误：Permission denied

# 即使使用 sh -c 包装也无法绕过
/system/bin/sh -c "/data/data/com.example.app/files/cmake"
# 错误：execve() failed: Permission denied
```

### 根本原因：SELinux 策略

Android 的 SELinux 策略明确禁止应用进程执行非系统目录下的可执行文件：

```
# SELinux 策略（简化版）
neverallow untrusted_app exec_type:file { execute execute_no_trans };
```

这意味着：
- ❌ 应用数据目录（`/data/data/...`）中的可执行文件无法运行
- ❌ 外部存储（`/sdcard/...`）中的可执行文件无法运行
- ❌ 应用私有目录（`/data/user/...`）中的可执行文件无法运行
- ✅ 只有系统目录（`/system/bin/`）中的可执行文件可以运行

### 影响的工具

以下工具在 Android 10+ 上无法直接执行：
- `cmake` - CMake 构建系统
- `ninja` - Ninja 构建工具
- `clang` - Clang 编译器
- `clang++` - Clang++ 编译器
- `ld.lld` - LLD 链接器
- 任何自定义的可执行文件

## 解决方案：JNI 包装器

### 为什么 JNI 可以工作

通过 `System.loadLibrary()` 加载的共享库（.so 文件）不受 SELinux 限制：

```kotlin
// ✅ 这可以工作
System.loadLibrary("ninja_runner")  // 加载 libninja_runner.so
```

原因：
1. 共享库不是"可执行文件"，而是"库文件"
2. SELinux 允许应用加载和使用共享库
3. 共享库在应用进程内运行，不需要 `execve()`

### 实现方式

#### 1. 将工具编译成共享库

```bash
# 原始方式：编译成可执行文件
clang++ -o ninja ninja.cpp

# JNI 方式：编译成共享库
clang++ -shared -fPIC -o libninja_runner.so ninja.cpp ninja_runner.cpp
```

#### 2. 提供 JNI 入口点

```cpp
// ninja_runner.cpp
extern "C" int main(int argc, char** argv);

extern "C" int ninja_run(int argc, char** argv) {
    return main(argc, argv);
}
```

#### 3. Kotlin/Java 调用

```kotlin
object NinjaRunner {
    init {
        System.loadLibrary("ninja_runner")
    }
    
    external fun runNinja(workDir: String, args: Array<String>): Int
}

// 使用
val exitCode = NinjaRunner.runNinja("/path/to/build", arrayOf("-v"))
```

## 已实现的 JNI 包装器

### ✅ Ninja

**文件**:
- `app/src/main/jniLibs/x86_64/libninja_runner.so`
- `app/src/main/jniLibs/arm64-v8a/libninja_runner.so`

**接口**:
```kotlin
// app/src/main/java/com/wuxianggujun/tinaide/core/nativebridge/NinjaRunner.kt
object NinjaRunner {
    external fun runNinja(workDir: String, args: Array<String>): Int
}
```

**使用示例**:
```kotlin
val result = NinjaRunner.runNinja(
    workDir = "/data/data/com.example/files/build",
    args = arrayOf("-v", "-j4")
)
```

### 🚧 CMake（待实现）

**挑战**:
- CMake 依赖众多（libuv, libarchive, curl 等）
- 需要使用 `-fPIC` 重新编译所有依赖
- 构建时间长，复杂度高

**临时方案**:
- 跳过 CMake 配置步骤
- 直接使用 Ninja 构建（需要预生成的 `build.ninja`）

## 其他尝试过的方案（均失败）

### ❌ 方案 1: 使用 sh -c 包装

```kotlin
ProcessBuilder("/system/bin/sh", "-c", "/path/to/cmake")
```

**失败原因**: SELinux 仍然会检查最终执行的文件

### ❌ 方案 2: 修改文件权限

```bash
chmod +x /path/to/cmake
```

**失败原因**: 权限不是问题，SELinux 策略才是

### ❌ 方案 3: 使用 LD_PRELOAD

```bash
LD_PRELOAD=/path/to/lib.so /path/to/cmake
```

**失败原因**: 仍然需要执行可执行文件

### ❌ 方案 4: 使用 proot

```bash
proot -r /path/to/rootfs /bin/cmake
```

**失败原因**: proot 本身也是可执行文件，无法运行

## 最佳实践

### 1. 优先使用 JNI 包装器

对于需要在 Android 上运行的工具：
1. 编译成共享库（.so）
2. 提供 JNI 接口
3. 通过 Kotlin/Java 调用

### 2. 简化依赖

- 尽量减少工具的依赖
- 使用静态链接（除了 libc++_shared.so）
- 避免复杂的运行时依赖

### 3. 提供回退方案

对于复杂工具（如 CMake）：
- 提供简化版本
- 或者使用替代工具
- 或者跳过某些步骤

## 参考资料

### SELinux 策略文档
- [Android SELinux](https://source.android.com/docs/security/features/selinux)
- [App Sandbox](https://source.android.com/docs/security/app-sandbox)

### 相关问题
- [Stack Overflow: Cannot execute binary file on Android](https://stackoverflow.com/questions/tagged/android+selinux)
- [Android Issue Tracker: SELinux denials](https://issuetracker.google.com/issues?q=selinux)

### 类似项目
- [Termux](https://github.com/termux/termux-app) - 使用 proot 和特殊技巧
- [Cosmic IDE](https://github.com/Cosmic-Ide/Cosmic-IDE) - 使用 JNI 包装器
- [AIDE](https://www.android-ide.com/) - 商业方案

---

**更新时间**: 2025-11-19  
**状态**: ✅ 已确认  
**结论**: 在 Android 10+ 上，**必须使用 JNI 包装器**，无法直接执行可执行文件
