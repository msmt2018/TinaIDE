## i18n 工具

本目录提供一些“可持续维护”的检查/修复脚本，用于：
- 同步 `values/strings.xml` 与 `values-en/strings.xml` 的 key（避免英文资源缺失导致回退混乱）
- 扫描/修复代码中常见的乱码（mojibake）
- 扫描 Kotlin 里疑似用户可见的硬编码中文字符串（仅字符串字面量，忽略注释）
- 一键验收（本地/CI）

### 0) 一键验收（推荐）

`python tools/i18n/check_all.py`

更严格（包含日志行）：

`python tools/i18n/check_all.py --include-logs`

### 1) 同步英文 strings key

将 `values/strings.xml` 中缺失的资源项追加到 `values-en/strings.xml`（默认复制中文文本作为占位，后续可逐步人工翻译）。

当前会同步：
- `<string>`
- `<string-array>`
- `<plurals>`

`python tools/i18n/sync_values_en.py`

仅检查（CI/本地验收用）：

`python tools/i18n/sync_values_en.py --check`

### 2) 修复 mojibake（乱码）

先扫描：

`python tools/i18n/fix_mojibake.py --check`

自动修复（会改写文件）：

`python tools/i18n/fix_mojibake.py --apply`

### 3) 扫描 Kotlin 硬编码中文（非注释）

`python tools/i18n/check_hardcoded_cjk.py`
