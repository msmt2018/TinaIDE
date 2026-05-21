# 远程 LSP 功能使用指南

本文档介绍如何使用 TinaIDE 的远程 LSP 功能，在手机上获得 PC 级别的代码补全和诊断体验。

## 快速开始

### 1. PC 端准备

在 PC 上运行 LSP 代理服务器：

```bash
# 方式 1：使用 TinaIDE 提供的 Kotlin 版本（推荐，支持项目同步）
cd tools/tina-lsp-proxy-kt
./gradlew jar
java -jar build/libs/tina-lsp-proxy-kt-1.0.0.jar --port 6789 --lsp clangd

# 方式 2：使用 Python 脚本（推荐：快速验证）
python tools/tina-lsp-proxy.py

# 方式 3：使用 lsp-ws-proxy（仅纯 LSP，不支持 TinaIDE 自定义消息/项目同步）
cargo install lsp-ws-proxy
lsp-ws-proxy --listen 0.0.0.0:6789 -- clangd
```

### 2. TinaIDE 配置

1. 打开 **设置** → **编辑器**
2. 找到 **远程 LSP** 部分
3. 开启 **启用远程 LSP**
4. 输入 PC 的 **IP 地址**（如 `192.168.1.100`）
5. 端口保持默认 `6789`
6. 点击 **测试连接** 验证

### 3. 开始使用

打开任意 C/C++ 文件，状态栏会显示连接状态。连接成功后即可享受：
- 智能代码补全
- 实时错误诊断
- 跳转到定义
- 查找引用

---

## 功能详解

### 同步模式

| 模式 | 适用场景 | 说明 |
|------|----------|------|
| **自动（推荐）** | 所有项目 | 根据项目大小自动选择 |
| **轻量模式** | 单文件/小项目 | 仅传输当前打开的文件 |
| **项目模式** | CMake/大型项目 | 同步整个项目到 PC |

**设置路径**：设置 → 编辑器 → 远程 LSP → 同步模式

### 同步方案（项目模式）

| 方案 | 优点 | 适用场景 |
|------|------|----------|
| **内置同步** | 零配置 | 小中型项目（<1000 文件） |
| **rsync 增量** | 速度快、省流量 | 大型项目、频繁同步 |
| **手动同步** | 完全控制 | 特殊需求 |

**设置路径**：设置 → 编辑器 → 远程 LSP → 同步方案

### 状态栏指示

状态栏会显示当前连接状态：

| 显示 | 含义 |
|------|------|
| `远程 LSP` | 已连接 |
| `延迟: 50ms` | 连接正常，显示网络延迟 |
| `重连中 (2/5)` | 正在尝试重新连接 |
| `扫描 5/100` | 正在扫描项目文件 |
| `上传 3/10` | 正在分块上传项目 |

---

## 常见问题

### Q: 连接失败怎么办？

1. **检查网络**：确保手机和 PC 在同一局域网
2. **检查防火墙**：PC 需要开放 6789 端口
3. **检查服务**：确认 PC 端代理服务正在运行
4. **测试连接**：使用设置中的"测试连接"按钮

### Q: 补全很慢怎么办？

1. **检查延迟**：状态栏显示的延迟应 < 100ms
2. **切换模式**：大项目使用"项目模式"
3. **使用 rsync**：频繁同步的大项目使用 rsync 方案

### Q: 项目模式下没有补全？

1. **等待同步**：首次打开需要同步项目
2. **检查 CMake**：确保 PC 端有 `compile_commands.json`
3. **查看日志**：检查 PC 端代理的输出日志

### Q: 如何查看当前使用的模式？

设置 → 编辑器 → 远程 LSP → 同步模式

如果选择"自动"，下方会显示检测结果和原因。

---

## 高级配置

### rsync 同步配置

如果选择 rsync 方案，需要在 PC 端配置 rsync daemon：

**Linux/macOS**：
```bash
# 创建配置文件 /etc/rsyncd.conf
[tina-workspace]
    path = /tmp/tina-workspace
    read only = no

# 启动服务
rsync --daemon
```

**TinaIDE 配置**：
- rsync 模块：`tina-workspace`
- rsync 端口：`873`

详细配置请参考 [PC-LSP-Proxy-Setup-Guide.md](PC-LSP-Proxy-Setup-Guide.md)

### 自定义 LSP 命令

PC 端可以自定义 LSP 启动参数：

```bash
# Kotlin 代理支持传入完整命令行（推荐）
java -jar build/libs/tina-lsp-proxy-kt-1.0.0.jar --lsp "clangd --background-index=false"

# Python 代理当前仅支持指定 clangd 可执行文件路径（不支持在 --clangd-path 里拼接参数）
# 如需自定义参数，建议使用 Kotlin 代理，或直接修改 tools/tina-lsp-proxy.py

# lsp-ws-proxy 也可用于自定义参数（但不支持 TinaIDE 自定义消息/项目同步）
lsp-ws-proxy --listen 0.0.0.0:6789 -- clangd --background-index=false
```

---

## 技术规格

| 特性 | 规格 |
|------|------|
| 协议 | WebSocket + LSP JSON-RPC |
| 默认端口 | 6789 |
| 自动重连 | 支持（指数退避 1s-30s） |
| 心跳保活 | 30 秒 |
| 分块传输 | 自动（>1MB 或 >100 文件） |
| 压缩 | gzip + base64 |

---

## 相关文档

- [PC LSP 代理配置](PC-LSP-Proxy-Setup-Guide.md) - 代理部署与配置
- [LSP 调试指南](LSP-Debug-Guide.md) - 问题排查

---

*最后更新: 2026-01-12*
