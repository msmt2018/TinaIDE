# TinaIDE

> 在 Android 设备上运行的轻量级 C/C++ IDE

TinaIDE 是一个专为 Android 设备设计的集成开发环境，支持在手机或平板上直接编写、编译和运行 C/C++ 代码。

## ✨ 特性

- 🚀 **嵌入式编译器**: 内置 Clang/LLVM 17，无需外部工具
- 📝 **强大编辑器**: 基于 Sora Editor，支持语法高亮和代码补全
- ⚡ **快速编译**: 单文件快速编译
- 🎨 **现代 UI**: Material Design 3 设计语言
- 🔌 **插件系统**: 可扩展的插件架构

## 🎯 核心功能

### 编译器集成
- **库模式**: Clang/LLVM 以动态库形式集成，进程内编译
- **完整工具链**: 嵌入式 Clang/LLVM + Android Sysroot
- **Sysroot**: 完整的 Android NDK 头文件和库

### 项目支持
- **单文件项目**: 快速编译单个 C/C++ 文件
- **项目模板**: 内置单文件模板

### 编辑器功能
- 语法高亮
- 代码补全
- 错误提示
- 代码导航

## 🚀 快速开始

### 1. 构建工具链

```powershell
# 构建 LLVM/Clang 工具链（首次需要 30-60 分钟）
pwsh ./docker/llvm-build/build-local.ps1 -Abi arm64-v8a -ApiLevel 28

# 同步到项目
pwsh ./tools/sync-llvm-build.ps1 -Abi arm64-v8a -ApiLevel 28
```

### 2. 构建应用

```bash
# 构建并安装
./gradlew installDebug
```

> **多 ABI 构建（arm64 + x86_64）**
>
> 如果要打包同时包含 arm64-v8a 与 x86_64 的 native 库，可运行：
> ```bash
> ./gradlew assembleDebugAllAbi
> ```
> 该任务会先编译两个 ABI 的本地库，再生成 Debug APK。

### 3. 开始使用

1. 启动应用（首次启动会自动解压 sysroot）
2. 创建新项目（单文件）
3. 编写代码
4. 点击编译按钮

详细步骤请查看 [快速开始指南](docs/快速开始.md)

## 📚 文档

- [快速开始](docs/快速开始.md) - 从零开始使用 TinaIDE
- [架构概览](docs/架构概览.md) - 了解项目架构
- [开发指南](docs/开发指南.md) - 参与项目开发
- [文档中心](docs/README.md) - 完整文档索引

### 技术文档

- [Clang/LLVM 集成路线图](docs/CLANG_INTEGRATION_ROADMAP.md)
- [架构概览](docs/架构概览.md)
- [插件系统架构](docs/Plugin-System-Architecture.md)

## 🏗️ 技术栈

- **语言**: Kotlin, C++
- **UI**: Jetpack Compose, Material Design 3
- **编辑器**: Sora Editor
- **编译器**: Clang/LLVM 17
- **构建系统**: Gradle, Docker

## 🎨 支持的架构

- `arm64-v8a` (主要支持，真机)
- `x86_64` (模拟器支持)

目标 API Level: 28 (Android 9.0+)

## 🔧 系统要求

### 开发环境
- Android Studio (最新稳定版)
- JDK 17+
- Docker Desktop
- PowerShell 7+

### 运行环境
- Android 9.0+ (API 28+)
- 推荐 2GB+ RAM
- 推荐 500MB+ 可用存储

## 🤝 贡献

欢迎贡献代码、报告问题或提出建议！

1. Fork 本项目
2. 创建特性分支 (`git checkout -b feat/amazing-feature`)
3. 提交更改 (`git commit -m 'feat: add amazing feature'`)
4. 推送到分支 (`git push origin feat/amazing-feature`)
5. 创建 Pull Request

详见 [开发指南](docs/开发指南.md)

## 📄 许可证

本项目使用 Apache 2.0 with LLVM Exceptions 许可证。

## 🙏 致谢

- [LLVM Project](https://llvm.org/) - 编译器基础设施
- [Sora Editor](https://github.com/Rosemoe/sora-editor) - 代码编辑器

## 📮 联系方式

- GitHub Issues: [提交问题](https://github.com/wuxianggujun/TinaIDE/issues)
- 项目主页: [TinaIDE](https://github.com/wuxianggujun/TinaIDE)

---

**让移动开发更自由** 🚀
