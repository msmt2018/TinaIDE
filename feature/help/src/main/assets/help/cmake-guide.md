# CMake 配置指南

CMake 是一个跨平台的构建系统生成器，TinaIDE 使用 CMake 管理 C/C++ 项目的编译和依赖。

## CMake 基础

### 最小化配置

```cmake
cmake_minimum_required(VERSION 3.10)
project(MyProject)

add_executable(myapp main.cpp)
```

**说明**：
- `cmake_minimum_required` - 要求的最低 CMake 版本
- `project` - 项目名称
- `add_executable` - 创建可执行文件目标

### 设置 C++ 标准

```cmake
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
```

支持的标准：
- C++11、C++14、C++17、C++20

## 添加源文件

### 方式 1：逐个列出

```cmake
add_executable(myapp
    src/main.cpp
    src/utils.cpp
    src/config.cpp
    src/logger.cpp
)
```

### 方式 2：使用变量

```cmake
set(SOURCES
    src/main.cpp
    src/utils.cpp
    src/config.cpp
)

add_executable(myapp ${SOURCES})
```

### 方式 3：通配符（不推荐）

```cmake
file(GLOB SOURCES "src/*.cpp")
add_executable(myapp ${SOURCES})
```

**注意**：通配符不会自动检测新文件，需要重新运行 CMake。

## 头文件路径

### 私有包含路径

```cmake
target_include_directories(myapp PRIVATE
    ${CMAKE_SOURCE_DIR}/include
    ${CMAKE_SOURCE_DIR}/src
)
```

### 公共包含路径（库使用）

```cmake
target_include_directories(mylib PUBLIC
    ${CMAKE_SOURCE_DIR}/include
)
```

### 接口包含路径（仅给使用者）

```cmake
target_include_directories(mylib INTERFACE
    ${CMAKE_SOURCE_DIR}/include
)
```

## 链接库

### 系统库

```cmake
target_link_libraries(myapp
    pthread    # POSIX 线程
    m          # 数学库
    dl         # 动态链接
)
```

### 自定义库

```cmake
# 创建静态库
add_library(mylib STATIC
    src/lib.cpp
)

# 链接库
target_link_libraries(myapp mylib)
```

### 查找系统包

```cmake
# 查找 Boost
find_package(Boost REQUIRED COMPONENTS system filesystem)
target_link_libraries(myapp Boost::system Boost::filesystem)

# 查找线程库
find_package(Threads REQUIRED)
target_link_libraries(myapp Threads::Threads)
```

## 编译选项

### 全局编译选项

```cmake
# 警告
add_compile_options(-Wall -Wextra -Wpedantic)

# 优化
add_compile_options(-O2)
```

### 目标特定选项

```cmake
target_compile_options(myapp PRIVATE
    -Wall
    -Wextra
    -O2
    $<$<CONFIG:Debug>:-g>
    $<$<CONFIG:Release>:-O3>
)
```

### 预处理器定义

```cmake
target_compile_definitions(myapp PRIVATE
    VERSION="1.0.0"
    DEBUG_MODE
    $<$<CONFIG:Release>:NDEBUG>
)
```

## 构建类型

### 设置构建类型

```cmake
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Debug)
endif()
```

### 自定义构建类型标志

```cmake
set(CMAKE_CXX_FLAGS_DEBUG "-g -O0")
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_RELWITHDEBINFO "-O2 -g")
```

## 子目录和模块化

### 添加子目录

```
myproject/
├── CMakeLists.txt          # 根配置
├── src/
│   ├── CMakeLists.txt      # 源码配置
│   └── main.cpp
└── lib/
    ├── CMakeLists.txt      # 库配置
    └── mylib.cpp
```

**根 CMakeLists.txt**：
```cmake
cmake_minimum_required(VERSION 3.10)
project(MyProject)

add_subdirectory(lib)
add_subdirectory(src)
```

**lib/CMakeLists.txt**：
```cmake
add_library(mylib STATIC mylib.cpp)
target_include_directories(mylib PUBLIC ../include)
```

**src/CMakeLists.txt**：
```cmake
add_executable(myapp main.cpp)
target_link_libraries(myapp mylib)
```

## 生成 compile_commands.json

LSP 需要此文件提供代码补全：

```cmake
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
```

在 TinaIDE 中，`compile_commands.json` 会生成到项目目录下的 `build` 目录，
由 IDE 自动接入；运行阶段如果需要执行二进制，会单独复制到私有目录启动。

## 常用变量

### CMake 变量

| 变量 | 说明 |
|------|------|
| `CMAKE_SOURCE_DIR` | 顶层 CMakeLists.txt 所在目录 |
| `CMAKE_BINARY_DIR` | 构建目录 |
| `CMAKE_CURRENT_SOURCE_DIR` | 当前 CMakeLists.txt 所在目录 |
| `CMAKE_CURRENT_BINARY_DIR` | 当前构建目录 |
| `PROJECT_SOURCE_DIR` | 项目根目录 |
| `PROJECT_NAME` | 项目名称 |

### 编译器变量

| 变量 | 说明 |
|------|------|
| `CMAKE_CXX_COMPILER` | C++ 编译器路径 |
| `CMAKE_CXX_STANDARD` | C++ 标准版本 |
| `CMAKE_BUILD_TYPE` | 构建类型 |
| `CMAKE_CXX_FLAGS` | C++ 编译标志 |

## 条件编译

### 基于平台

```cmake
if(ANDROID)
    target_compile_definitions(myapp PRIVATE PLATFORM_ANDROID)
elseif(UNIX)
    target_compile_definitions(myapp PRIVATE PLATFORM_UNIX)
endif()
```

### 基于构建类型

```cmake
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_definitions(myapp PRIVATE DEBUG_BUILD)
endif()
```

### 生成器表达式

```cmake
target_compile_options(myapp PRIVATE
    $<$<CONFIG:Debug>:-g -O0>
    $<$<CONFIG:Release>:-O3 -DNDEBUG>
)
```

## 安装配置

```cmake
# 安装可执行文件
install(TARGETS myapp
    RUNTIME DESTINATION bin
)

# 安装头文件
install(DIRECTORY include/
    DESTINATION include
)

# 安装库
install(TARGETS mylib
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
)
```

## 自定义命令和目标

### 添加自定义命令

```cmake
add_custom_command(
    OUTPUT generated.cpp
    COMMAND python ${CMAKE_SOURCE_DIR}/generate.py
    DEPENDS ${CMAKE_SOURCE_DIR}/template.txt
)
```

### 添加自定义目标

```cmake
add_custom_target(docs
    COMMAND doxygen ${CMAKE_SOURCE_DIR}/Doxyfile
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
)
```

## 测试支持

```cmake
enable_testing()

add_executable(test_mylib test/test_mylib.cpp)
target_link_libraries(test_mylib mylib gtest)

add_test(NAME test_mylib COMMAND test_mylib)
```

运行测试：
```bash
cmake --build build
ctest --test-dir build
```

## 常见问题

### Q: CMake 配置失败？

检查：
- CMakeLists.txt 语法是否正确
- 所需的库是否已安装
- CMake 版本是否满足要求

### Q: 找不到头文件？

检查 `target_include_directories` 配置，确保路径正确。

### Q: 链接错误？

检查 `target_link_libraries` 配置，确保库的顺序正确（被依赖的库放在后面）。

### Q: 如何查看 CMake 变量？

```cmake
message(STATUS "Source dir: ${CMAKE_SOURCE_DIR}")
message(STATUS "Binary dir: ${CMAKE_BINARY_DIR}")
```

## 最佳实践

1. **使用现代 CMake** - 使用 `target_*` 命令而非全局命令
2. **明确依赖关系** - 使用 `target_link_libraries` 而非手动链接
3. **避免通配符** - 显式列出源文件
4. **使用子目录** - 模块化大型项目
5. **导出编译数据库** - 启用 `CMAKE_EXPORT_COMPILE_COMMANDS`

## 示例项目

### 简单可执行程序

```cmake
cmake_minimum_required(VERSION 3.10)
project(HelloWorld)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

add_executable(hello main.cpp)
```

### 带库的项目

```cmake
cmake_minimum_required(VERSION 3.10)
project(MyProject)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# 创建库
add_library(utils STATIC
    src/utils.cpp
    src/logger.cpp
)
target_include_directories(utils PUBLIC include)

# 创建可执行文件
add_executable(myapp src/main.cpp)
target_link_libraries(myapp utils pthread)
```

## 下一步

- 学习 [编译项目](build-project.md)
- 了解 [创建项目](create-project.md)
- 参考 [CMake 官方文档](https://cmake.org/documentation/)
