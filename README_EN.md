# TinaIDE

> A lightweight C/C++ IDE running on Android devices

[中文文档](README.md)

---

TinaIDE is an integrated development environment designed specifically for Android devices, allowing you to write, compile, and run C/C++ code directly on your phone or tablet. With a built-in complete Clang/LLVM toolchain and clangd language server, it provides a development experience close to desktop IDEs.

## Features

- **Embedded Compiler**: Built-in Clang/LLVM 17, in-process compilation, no external tools required
- **Intelligent Code Completion**: Integrated clangd LSP for precise semantic-level code completion
- **Syntax Highlighting**: High-performance incremental syntax highlighting based on Tree-sitter
- **Code Navigation**: Go to definition, find references, hover documentation
- **Real-time Diagnostics**: Display errors and warnings in real-time while editing
- **Modern Editor**: Powered by Tina Editor (Compose + Canvas) with multi-tab editing
- **Material Design 3**: Following the latest Material Design guidelines
- **In-process Execution**: Run compiled programs directly within the app
- **Multi-shell Terminal**: Built-in Bash and Zsh environment
- **LLDB Debugging**: Breakpoints, stepping, variables, and call stacks
- **Git Integration**: Clone/commit/push/pull/branches/conflict resolution with HTTPS credentials and SSH keys
- **Plugin System**: Themes/snippets/menu extensions; supports auto-install bundled plugins from assets
- **File Preview**: Built-in preview for Markdown/JSON/images/Hex and more

## UI Preview

<table>
  <tr>
    <td align="center"><img src="./image/projects.jpg" alt="Projects" width="220"><br>Projects</td>
    <td align="center"><img src="./image/marketplace.jpg" alt="Marketplace" width="220"><br>Marketplace</td>
    <td align="center"><img src="./image/tutorials.jpg" alt="Tutorials" width="220"><br>Tutorials</td>
  </tr>
  <tr>
    <td align="center"><img src="./image/profile.jpg" alt="Profile" width="220"><br>Profile</td>
    <td align="center"><img src="./image/code-editor.jpg" alt="Code editor" width="220"><br>Code editor</td>
    <td align="center">More screens will be updated over time</td>
  </tr>
</table>

## Core Features

### Compiler Integration

| Feature | Description |
|---------|-------------|
| In-process Compilation | Clang/LLVM integrated as dynamic library, no need to fork external processes |
| LLD Linker | Fast linking using LLVM LLD (with process isolation) |
| Shared Library Output | Compile to .so files, support in-process loading and execution |
| Complete Sysroot | Android NDK headers and runtime libraries |

### LSP Language Services

| Feature | Description |
|---------|-------------|
| Code Completion | Semantic-level intelligent completion, supporting member access, headers, macros, etc. |
| Go to Definition | Quickly jump to the definition of functions, variables, and types |
| Find References | Find all usages of a symbol in the project |
| Hover Documentation | Display type information and documentation on cursor hover |
| Real-time Diagnostics | Detect syntax and semantic errors in real-time while editing |

### Editor Features

| Feature | Description |
|---------|-------------|
| Multi-tab Editing | Open multiple files simultaneously with quick switching |
| Tree-sitter Highlighting | C/C++/CMake syntax highlighting |
| Symbol Input Bar | Quick input of programming symbols (brackets, operators, etc.) |
| Undo/Redo | Complete editing history support |
| Auto Indentation | Smart code indentation |
| Line Numbers | Configurable line number area |

### Project Management

| Feature | Description |
|---------|-------------|
| File Tree Navigation | Drawer-style project file browser |
| Project Templates | Single-file / CMake executable / CMake library templates |
| compile_commands.json | Auto-generated to provide compilation configuration for LSP |

### Bottom Panel

| Tab | Function |
|-----|----------|
| Build Log | Display compilation output and error messages |
| Log | General application logs |
| Diagnostics | LSP diagnostic list, click to jump to location |

### Debugging (LLDB)

| Feature | Description |
|---------|-------------|
| Breakpoints | Set/remove breakpoints and continue |
| Stepping | Step In / Step Over / Step Out |
| Inspection | Variables, call stack, threads, etc. |

### Git Integration

| Feature | Description |
|---------|-------------|
| Common Operations | status/diff/commit/log/checkout |
| Remote Operations | clone/fetch/pull/push (including rebase/force/tags options) |
| Credentials & Security | HTTPS credentials (encrypted by Keystore), SSH keys (passphrase supported) |
| Conflict Resolution | merge/rebase conflict workflows (continue/skip/abort) |

### Plugin System

| Feature | Description |
|---------|-------------|
| Plugin Manager | Install/uninstall/enable/disable (local directory) |
| Theme Plugins | Provide editor themes via plugins |
| Snippet Plugins | Show snippets in completion list and insert placeholders |
| Bundled Plugins | `assets/bundled_plugins/*` auto install/update on app start |

### File Preview

| Type | Description |
|------|-------------|
| Markdown | Built-in Markdown viewer |
| JSON | JSON viewer |
| Images | Image preview |
| Hex | Hex viewer for binary files |

## Quick Start

### 1. Toolchain Assets

```powershell
# This repo already includes the built-in runtime/debug assets (proot/sysroot/toolchain metadata),
# so normal development usually does NOT require rebuilding them locally.
#
# If you need to rebuild (contributors/maintainers), see docs/快速开始.md
# ("Rebuild built-in runtime environment").
#
# Common entry points:
# - Refresh Linux distro manifest: pwsh ./tools/linux-distro/generate-linux-distro-manifest.ps1
# - Rebuild proot + sysroot: pwsh ./docker/proot-build/build-proot.ps1 -CopyToJniLibs -CopyToAssets
```

### 2. Build Application

```bash
# Build and install (Debug version)
./gradlew installDebug

# Build Release version (requires signing configuration)
./gradlew assembleRelease
```

### 3. Getting Started

1. Launch the app (first launch will auto-extract sysroot, takes about 1-2 minutes)
2. Create a new project or open an existing one
3. Write code (LSP automatically provides completion and diagnostics)
4. Click the run button to compile and execute

For detailed steps, see [Quick Start Guide](docs/快速开始.md)

## Documentation

- [Quick Start](docs/快速开始.md) - Start using TinaIDE from scratch
- [Architecture Overview](docs/架构概览.md) - Understand project architecture
- [Development Guide](docs/开发指南.md) - Contribute to the project
- [Documentation Center](docs/README.md) - Complete documentation index
- [Changelog](CHANGELOG.md) - Version update history

### Technical Documentation

- [Clang/LLVM Integration Roadmap](docs/CLANG_INTEGRATION_ROADMAP.md)
- [LSP Integration Guide](docs/LSP-Integration.md)
- [Native Compile & Runtime](docs/Native-Compile-Runtime.md)
- [Bottom Panel Guide](docs/Bottom-Panel-Guide.md)

## Tech Stack

| Category | Technology |
|----------|------------|
| Languages | Kotlin, C++ |
| UI Framework | Android View + Material Design 3 |
| Editor | Tina Editor (in-house, Compose + Canvas) |
| Syntax Highlighting | Tree-sitter (C/C++/CMake) |
| Compiler | Clang/LLVM 17 |
| Linker | LLD |
| LSP Service | clangd (embedded) |
| Async Processing | Kotlin Coroutines |
| Build System | Gradle + CMake |
| Dependency Injection | Custom ServiceLocator |

## Supported Architectures

| Architecture | Status | Usage |
|--------------|--------|-------|
| `arm64-v8a` | ✅ Primary | Physical devices |
| `x86_64` | ✅ Supported | Emulators |

**Target API Level**: 28+ (Android 9.0+)
**Compile SDK**: 36 (Android 16)

## System Requirements

### Development Environment

- Android Studio (latest stable version)
- JDK 17+
- Docker Desktop (for building LLVM)
- PowerShell 7+

### Runtime Environment

- Android 9.0+ (API 28+)
- Recommended 3GB+ RAM
- Recommended 800MB+ available storage (including sysroot)

## Project Structure

```
TinaIDE/
├── app/
│   └── src/main/
│       ├── java/.../tinaide/
│       │   ├── core/                    # Core services
│       │   │   ├── compile/             # Build & run
│       │   │   ├── config/              # App configuration
│       │   │   ├── debug/               # Debugger (LLDB)
│       │   │   ├── format/              # Code formatting
│       │   │   ├── git/                 # Git integration
│       │   │   ├── lsp/                 # LSP client
│       │   │   └── proot/               # PRoot environment
│       │   ├── editor/                  # Editor core
│       │   ├── file/                    # File management
│       │   ├── output/                  # Output management
│       │   ├── plugin/                  # Plugin system
│       │   └── ui/                      # UI layer (Jetpack Compose)
│       └── cpp/
│           ├── compiler/       # Clang compiler JNI
│           ├── linker/         # LLD linker JNI
│           ├── lsp/            # clangd service JNI
│           └── treesitter/     # Tree-sitter syntax highlighting
├── external/
│   ├── tina-android-tree-sitter/ # Tree-sitter integration
│   └── termux-terminal/        # Terminal modules
├── language-cmake/             # CMake language support module
└── docs/                       # Project documentation
```

## Support

TinaIDE is being opened up fully, and future energy will focus on stable maintenance
and community collaboration. If this project helps you, voluntary support is welcome.
PRs are also welcome for features, bug fixes, and docs.

| WeChat | Alipay |
|--------|--------|
| <img src="./docs/assets/donation/weixin.png" alt="WeChat donation QR code" width="220"> | <img src="./docs/assets/donation/zhifubao.jpg" alt="Alipay payment QR code" width="220"> |

To quickly measure the project size while excluding third-party code and generated files:

```powershell
cloc . --exclude-dir=.git,build,.gradle,.idea,node_modules,external,temp,tmp,docs/assets
```

## Acknowledgments

- [LLVM Project](https://llvm.org/) - Compiler infrastructure
- [Tree-sitter](https://tree-sitter.github.io/) - Syntax highlighting parser
- [clangd](https://clangd.llvm.org/) - C/C++ language server

---

**Making mobile development more accessible**
