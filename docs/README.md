# TinaIDE 文档中心

欢迎来到 TinaIDE 文档中心！这里包含了项目的核心技术文档和使用指南。

## 📚 文档导航

### 快速开始
- [项目 README](../README.md) - 项目概述和快速开始指南

### 核心架构
- [Clang/LLVM 集成路线图](CLANG_INTEGRATION_ROADMAP.md) - 编译器集成的完整技术方案
- [LLVM/Clang 状态](LLVM_CLANG_STATUS.md) - 当前集成状态和进度
- [LLVM 构建工具](LLVM_BUILD_TOOLS.md) - 工具链构建说明
- [Native 链接策略](Native-Linking-Strategies.md) - 原生库链接方案

### Android 平台
- [Android SELinux 限制说明](Android-SELinux-限制说明.md) - SELinux 安全策略说明
- [Android SELinux 解决方案](Android-SELinux-解决方案.md) - 如何应对 SELinux 限制

### 开发指南
- [代码重构指南](Code-Refactoring-Guide.md) - 代码重构的最佳实践
- [Material Design 指南](Material-Design-Guide.md) - UI 设计规范
- [插件系统架构](Plugin-System-Architecture.md) - 插件系统设计
- [LogView 使用说明](LogView-Usage.md) - 日志查看器使用

## 🎯 项目特性

### 核心功能
- ✅ **嵌入式 Clang/LLVM**: 库模式集成，无需外部工具
- ✅ **单文件编译**: 快速编译 C/C++ 单文件项目
- ✅ **Sora Editor**: 强大的代码编辑器
- ✅ **项目管理**: 创建、打开、管理 C/C++ 项目
- ✅ **xmake 支持**: Android 原生构建工具（实验性）

### 支持的构建工具
- **Clang/LLVM**: 编译和链接 C/C++ 代码
- **xmake**: Android 原生构建工具（实验性）

### 支持的架构
- `arm64-v8a` (主要支持)
- `x86_64` (模拟器支持)

## 🚀 快速开始

### 1. 构建工具链
```powershell
# 构建 LLVM/Clang 工具链
pwsh ./docker/llvm-build/build-local.ps1 -Abi arm64-v8a -ApiLevel 24
```

### 2. 同步到项目
```powershell
# 同步库文件和 sysroot
pwsh ./tools/sync-llvm-build.ps1 -Abi arm64-v8a -ApiLevel 24
```

### 3. 构建 APK
```bash
./gradlew assembleDebug
```

### 4. 安装运行
```bash
./gradlew installDebug
```

## 📖 技术栈

### 前端
- **Kotlin**: 主要开发语言
- **Jetpack Compose**: 现代 UI 框架
- **Material Design 3**: UI 设计规范
- **Sora Editor**: 代码编辑器核心

### 后端
- **C++**: JNI 原生代码
- **Clang/LLVM 17**: 编译器工具链
- **xmake**: 构建工具（实验性）

### 构建工具
- **Gradle**: Android 构建系统
- **Docker**: 工具链构建环境
- **PowerShell**: 自动化脚本

## 🔧 开发原则

项目遵循以下设计原则：

- **KISS** (Keep It Simple, Stupid): 保持简单
- **YAGNI** (You Aren't Gonna Need It): 只实现需要的功能
- **DRY** (Don't Repeat Yourself): 避免重复
- **SOLID**: 面向对象设计原则

详见 [Clang/LLVM 集成路线图](CLANG_INTEGRATION_ROADMAP.md#规范与原则)

## 📝 文档贡献

欢迎贡献文档！请遵循以下规范：

1. 使用 Markdown 格式
2. 保持文档简洁清晰
3. 添加代码示例和截图
4. 更新文档索引

## 🐛 问题反馈

如果发现文档问题或有改进建议，请：

1. 在 GitHub 提交 Issue
2. 或直接提交 Pull Request

## 📄 许可证

本项目使用 Apache 2.0 with LLVM Exceptions 许可证。

---

**最后更新**: 2025-11-25  
**维护者**: TinaIDE 开发团队
