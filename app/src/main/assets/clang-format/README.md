# Clang-Format 配置文件

此目录包含 TinaIDE 内置的 clang-format 格式化风格配置文件。

## 可用风格

| 文件名 | 风格 | 说明 |
|--------|------|------|
| `llvm.clang-format` | LLVM | LLVM 项目编码规范，2 空格缩进 |
| `google.clang-format` | Google | Google C++ 编码规范，2 空格缩进 |
| `chromium.clang-format` | Chromium | Chromium 项目编码规范，2 空格缩进 |
| `mozilla.clang-format` | Mozilla | Mozilla/Firefox 编码规范，2 空格缩进 |
| `webkit.clang-format` | WebKit | WebKit 项目编码规范，4 空格缩进 |
| `microsoft.clang-format` | Microsoft | Microsoft C++ 编码规范，4 空格缩进，Allman 大括号风格 |
| `gnu.clang-format` | GNU | GNU 编码规范，2 空格缩进，GNU 大括号风格 |

## 使用方式

### 1. 使用内置风格

在 TinaIDE 设置中选择默认格式化风格，当项目中没有 `.clang-format` 文件时，将使用所选风格进行格式化。

### 2. 自定义项目风格

1. 将所需的配置文件复制到项目根目录
2. 重命名为 `.clang-format`
3. 根据需要修改配置

### 3. 配置优先级

1. 项目目录中的 `.clang-format` 或 `_clang-format` 文件（最高优先级）
2. 用户在设置中选择的默认风格

## 配置文件格式

配置文件使用 YAML 格式，主要选项包括：

- `BasedOnStyle`: 基础风格
- `IndentWidth`: 缩进宽度
- `TabWidth`: Tab 宽度
- `UseTab`: 是否使用 Tab
- `ColumnLimit`: 列宽限制
- `BreakBeforeBraces`: 大括号换行风格
- `PointerAlignment`: 指针对齐方式

完整选项列表请参考 [Clang-Format Style Options](https://clang.llvm.org/docs/ClangFormatStyleOptions.html)

## 风格对比

### 大括号风格

```cpp
// Attach (LLVM, Google, Chromium)
if (condition) {
    doSomething();
}

// Allman (Microsoft)
if (condition)
{
    doSomething();
}

// GNU
if (condition)
  {
    doSomething();
  }
```

### 指针对齐

```cpp
// Left (Google, Chromium, Mozilla, WebKit)
int* ptr;

// Right (LLVM, Microsoft, GNU)
int *ptr;
```

### 缩进宽度

- 2 空格: LLVM, Google, Chromium, Mozilla, GNU
- 4 空格: WebKit, Microsoft