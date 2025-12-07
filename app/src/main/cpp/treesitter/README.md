# Tree-sitter Integration

This directory contains tree-sitter language parsers and JNI bindings for syntax highlighting.

## Directory Structure

```
treesitter/
├── include/
│   └── tree_sitter/
│       ├── api.h         # Tree-sitter public API
│       ├── parser.h      # Parser definitions
│       ├── alloc.h       # Memory allocation
│       └── array.h       # Array utilities
├── lib/
│   └── src/              # Tree-sitter core library source
│       ├── lib.c         # Single-file amalgamation (main entry)
│       ├── parser.c      # Parser implementation
│       ├── query.c       # Query implementation
│       ├── tree.c        # Tree implementation
│       ├── node.c        # Node implementation
│       └── ...           # Other source files
├── core/                  # JNI bindings for core API
│   ├── ts_jni.cpp        # TSLanguage, TSParser, TSTree bindings
│   ├── ts_node_jni.cpp   # TSNode bindings
│   └── ts_query_jni.cpp  # TSQuery, TSQueryCursor bindings
├── cmake/                 # CMake language parser
│   ├── parser.c
│   ├── scanner.c
│   └── cmake_jni.cpp
└── cpp/                   # C++ language parser
    ├── parser.c          # From tree-sitter-cpp
    ├── scanner.c
    └── cpp_jni.cpp
```

## Kotlin Bindings

The Kotlin bindings are located in:
- `com.wuxianggujun.tinaide.treesitter` - Core classes (TSLanguage, TSParser, TSTree, etc.)
- `com.wuxianggujun.tinaide.treesitter.languages` - Language bindings (TSLanguageCpp)

These bindings are compatible with sora-editor's `language-treesitter` module.

## Source

- Tree-sitter core: https://github.com/tree-sitter/tree-sitter
- Tree-sitter C++: https://github.com/tree-sitter/tree-sitter-cpp
