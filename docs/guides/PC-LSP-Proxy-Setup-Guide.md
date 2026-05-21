# PC端远程LSP代理服务器配置指南

> **更新日期**: 2026-01-05  
> **适用版本**: TinaIDE v1.0+

本指南介绍如何在PC上运行LSP代理服务器，用于调试TinaIDE的远程LSP功能。

---

## 目录

1. [概述](#概述)
2. [方案选择](#方案选择)
3. [Python版本](#python版本)
4. [Kotlin版本](#kotlin版本)
5. [Rust版本（第三方）](#rust版本第三方)
6. [测试验证](#测试验证)
7. [故障排查](#故障排查)

---

## 概述

远程LSP架构：

```
┌─────────────────┐      WebSocket      ┌──────────────────┐
│   TinaIDE (手机)  │ ←───────────────→ │  LSP Proxy (PC)   │
│                 │   ws://IP:6789      │                  │
│  RemoteLspConn  │                     │  WebSocket ↔     │
│  Provider       │                     │  stdio           │
└─────────────────┘                     └──────────────────┘
                                               ↓
                                        ┌──────────────────┐
                                        │   clangd (LSP)   │
                                        │                  │
                                        │  语言服务器       │
                                        └──────────────────┘
```

**核心功能：**
- 接收WebSocket连接（端口6789）
- 启动并管理LSP服务器进程（clangd等）
- 双向转发LSP消息
- 支持项目同步（`tina/syncProject`）
- 支持文件变更（`tina/fileChanged`）

---

## 方案选择

| 方案 | 优点 | 缺点 | 推荐场景 |
|------|------|------|---------|
| **Python版** | 简单快速，无需编译 | 需要 Python 环境；不便传入自定义 clangd 参数 | 快速测试/调试 |
| **Kotlin版** | 类型安全，支持自定义 LSP 命令 | 需要 JDK 17+ | 日常使用（推荐） |
| **Rust版（第三方）** | 性能好、资源占用低 | 不支持 `tina/syncProject` 等自定义消息 | 仅纯 LSP 场景 |

---

## Python版本

### 1. 安装依赖

```bash
# 安装 websockets 库
pip install websockets

# 或使用国内镜像加速
pip install -i https://pypi.tuna.tsinghua.edu.cn/simple websockets
```

### 2. 启动服务器

```bash
# 在仓库根目录执行（默认端口 6789）
python tools/tina-lsp-proxy.py

# 指定端口
python tools/tina-lsp-proxy.py --port 8080

# 指定 clangd 可执行文件路径（如果不在 PATH 中；仅路径，不支持在这里拼接参数）
python tools/tina-lsp-proxy.py --clangd-path /usr/local/bin/clangd

# 指定监听地址和端口
python tools/tina-lsp-proxy.py --host 192.168.1.100 --port 6789
```

### 3. 启动成功输出

```
============================================================
TinaIDE LSP Proxy v2.0 (支持项目模式)
============================================================
监听地址: ws://0.0.0.0:6789
clangd 路径: clangd

支持的功能:
  - 标准 LSP 消息转发
  - tina/syncProject (项目同步)
  - tina/fileChanged (文件变更)

等待 TinaIDE 连接...
============================================================
```

### 4. 连接日志示例

```
[INFO] 客户端已连接: ('192.168.1.10', 54321)
[INFO] clangd 已启动 (PID: 12345)
[INFO] 收到项目同步请求: MyProject (15 文件)
[INFO] 项目已同步到: /tmp/tina-workspace-MyProject-abc123
```

---

## Kotlin版本

### 1. 构建项目

```bash
# 进入Kotlin代理目录
cd tools/tina-lsp-proxy-kt

# Windows
gradlew.bat build jar

# Linux/macOS
./gradlew build jar
```

### 2. 启动服务器

```bash
# 基本用法
java -jar build/libs/tina-lsp-proxy-kt-1.0.0.jar

# 指定参数
java -jar build/libs/tina-lsp-proxy-kt-1.0.0.jar \
  --host 0.0.0.0 \
  --port 6789 \
  --lsp clangd

# 使用带参数的LSP命令
java -jar build/libs/tina-lsp-proxy-kt-1.0.0.jar \
  --lsp "clangd --background-index=false --limit-results=50"

# 禁用文件同步（仅转发LSP消息）
java -jar build/libs/tina-lsp-proxy-kt-1.0.0.jar --sync false
```

### 3. 启动成功输出

```
============================================================
  TinaIDE LSP Proxy v1.0.0 (Kotlin)
============================================================

  支持的功能:
    - 标准 LSP 消息转发
    - tina/syncProject (项目同步)
    - tina/fileChanged (文件变更)

  等待 TinaIDE 连接...
============================================================
监听地址: ws://0.0.0.0:6789
LSP 命令: clangd
文件同步: 启用
```

### 4. 支持的LSP服务器

```bash
# C/C++
java -jar build/libs/tina-lsp-proxy-kt-1.0.0.jar --lsp clangd

# Rust
java -jar build/libs/tina-lsp-proxy-kt-1.0.0.jar --lsp rust-analyzer

# Go
java -jar build/libs/tina-lsp-proxy-kt-1.0.0.jar --lsp gopls

# Python
java -jar build/libs/tina-lsp-proxy-kt-1.0.0.jar --lsp pylsp

# TypeScript/JavaScript
java -jar build/libs/tina-lsp-proxy-kt-1.0.0.jar --lsp "typescript-language-server --stdio"
```

---

## Rust版本（第三方）

### 1. 安装lsp-ws-proxy

```bash
# 使用cargo安装
cargo install lsp-ws-proxy

# 或从源码编译
git clone https://github.com/qualified/lsp-ws-proxy
cd lsp-ws-proxy
cargo build --release
```

### 2. 启动服务器

```bash
# 基本用法
lsp-ws-proxy --listen 0.0.0.0:6789 -- clangd

# 禁用后台索引
lsp-ws-proxy --listen 0.0.0.0:6789 -- clangd --background-index=false

# 指定编译数据库路径
lsp-ws-proxy --listen 0.0.0.0:6789 -- clangd --compile-commands-dir=/path/to/build
```

### 3. 注意事项

⚠️ **lsp-ws-proxy不支持TinaIDE自定义消息**（`tina/syncProject`等），因此：
- ✅ 适用于 **LIGHTWEIGHT模式**（仅转发当前文件）
- ❌ 不适用于 **PROJECT模式**（需要项目同步）

---

## 测试验证

### 1. 网络连接测试

```bash
# 在手机终端测试WebSocket连接（需要安装wscat）
wscat -c ws://192.168.1.100:6789

# 或使用curl测试HTTP端点
curl http://192.168.1.100:6789/
# 应返回: OK
```

### 2. TinaIDE配置

1. **打开TinaIDE设置**
   - 设置 → 编辑器设置 → 远程LSP服务器

2. **配置服务器**
   - 启用远程LSP：✅
   - 服务器地址：`192.168.1.100`（你的PC IP）
   - 端口：`6789`
   - 同步模式：`LIGHTWEIGHT`（首次测试推荐）

3. **打开C/C++文件**
   - 打开任意`.c`或`.cpp`文件
   - 观察底部状态栏：应显示 `Remote LSP`（绿色）

### 3. 功能验证

**测试代码补全：**
```cpp
#include <stdio.h>

int main() {
    std::  // 输入后应出现标准库补全
    return 0;
}
```

**测试错误诊断：**
```cpp
int main() {
    int x = "hello";  // 应显示类型错误的红色波浪线
    return 0;
}
```

**测试悬浮提示：**
```cpp
#include <stdio.h>

int main() {
    printf  // 长按应显示函数签名
    return 0;
}
```

### 4. 查看日志

**PC端日志：**
```bash
# Python版 - 控制台输出
[INFO] 客户端已连接: ('192.168.1.10', 54321)
[INFO] clangd 已启动 (PID: 12345)

# Kotlin版 - 查看详细日志
export JAVA_OPTS="-Dlogback.configurationFile=logback-debug.xml"
java -jar build/libs/tina-lsp-proxy-kt-1.0.0.jar
```

**手机端日志：**
```bash
adb logcat -s RemoteLsp:I LspEditorManager:I

# 关键日志
I/RemoteLsp: Connecting to remote LSP server: ws://192.168.1.100:6789
I/RemoteLsp: WebSocket connected to 192.168.1.100:6789
I/RemoteLsp: Remote LSP connection established
I/LspEditorManager: Attaching remote LSP: ws://192.168.1.100:6789
```

---

## 故障排查

### 问题1: 连接超时

**症状：** TinaIDE状态显示 `Remote Connecting...` 长时间不变

**可能原因：**
- PC防火墙阻止了6789端口
- PC和手机不在同一网络
- IP地址配置错误

**解决方法：**
```bash
# Windows - 允许端口通过防火墙
netsh advfirewall firewall add rule name="TinaLSP" dir=in action=allow protocol=TCP localport=6789

# Linux - 使用iptables
sudo iptables -A INPUT -p tcp --dport 6789 -j ACCEPT

# macOS - 系统偏好设置 → 安全性与隐私 → 防火墙 → 防火墙选项
# 允许传入连接

# 测试端口是否开放
nc -zv 192.168.1.100 6789
```

### 问题2: 连接成功但无LSP功能

**症状：** 状态显示 `Remote LSP` 但没有补全/诊断

**可能原因：**
- clangd未正确启动
- 项目缺少编译数据库

**解决方法：**
```bash
# 检查clangd是否在PATH中
which clangd  # Linux/macOS
where clangd  # Windows

# 手动测试clangd
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}' | clangd

# 查看PC端日志确认clangd启动
# Python版会输出: [INFO] clangd 已启动 (PID: xxxx)
```

### 问题3: PROJECT模式同步失败

**症状：** 项目模式下连接失败或文件未同步

**可能原因：**
- 使用了不支持自定义消息的代理（如lsp-ws-proxy）
- 项目太大导致超时

**解决方法：**
```bash
# 1. 使用Python或Kotlin版本（支持项目同步）
python tools/tina-lsp-proxy.py

# 2. 或切换到LIGHTWEIGHT模式（仅传输当前文件）
# TinaIDE设置 → 同步模式 → LIGHTWEIGHT

# 3. 增加消息大小限制（Kotlin版）
# 编辑 Main.kt，修改 maxFrameSize
install(WebSockets) {
    maxFrameSize = 100 * 1024 * 1024  // 100MB
}
```

### 问题4: 延迟过高

**症状：** 补全响应慢，延迟>500ms

**优化方法：**
```bash
# 1. 使用5GHz WiFi或有线连接

# 2. clangd 参数调优（建议用 Kotlin 代理传入完整命令行）
java -jar build/libs/tina-lsp-proxy-kt-1.0.0.jar --lsp "clangd --background-index=false --limit-results=30"

# Python 代理默认已禁用后台索引；且 --clangd-path 仅支持 clangd 可执行文件路径，不支持拼接参数

# 4. 使用LIGHTWEIGHT模式减少同步开销
```

### 问题5: 代理进程崩溃

**症状：** 代理服务器异常退出

**调试方法：**
```bash
# Python版 - 查看详细错误
python -u tools/tina-lsp-proxy.py 2>&1 | tee proxy.log

# Kotlin版 - 启用调试日志
java -Dlogback.configurationFile=logback-debug.xml \
     -jar build/libs/tina-lsp-proxy-kt-1.0.0.jar

# 检查系统资源
top  # Linux/macOS
taskmgr  # Windows
```

---

## 高级配置

### 使用Docker运行

```dockerfile
# Dockerfile
FROM ubuntu:22.04

RUN apt-get update && \
    apt-get install -y python3 python3-pip clangd && \
    pip3 install websockets && \
    rm -rf /var/lib/apt/lists/*

COPY tools/tina-lsp-proxy.py /app/tina-lsp-proxy.py
WORKDIR /app

EXPOSE 6789
CMD ["python3", "/app/tina-lsp-proxy.py"]
```

```bash
# 构建镜像
docker build -t tina-lsp-proxy .

# 运行容器
docker run -p 6789:6789 tina-lsp-proxy
```

### 系统服务配置（Linux）

```ini
# /etc/systemd/system/tina-lsp-proxy.service
[Unit]
Description=TinaIDE LSP Proxy
After=network.target

[Service]
Type=simple
User=your-username
WorkingDirectory=/path/to/TinaIDE/tools
ExecStart=/usr/bin/python3 tina-lsp-proxy.py
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
# 启用服务
sudo systemctl enable tina-lsp-proxy
sudo systemctl start tina-lsp-proxy

# 查看状态
sudo systemctl status tina-lsp-proxy
```

---

## 相关文档

- [Remote-LSP-Guide.md](Remote-LSP-Guide.md) - 远程LSP完整设计文档
- [LSP-Debug-Guide.md](LSP-Debug-Guide.md) - LSP调试指南

---

## 常见问题

**Q: 可以同时连接多个手机吗？**  
A: 当前版本每个代理实例只支持一个客户端。如需多客户端，请运行多个代理实例在不同端口。

**Q: 支持HTTPS/WSS吗？**  
A: 当前仅支持WS（未加密）。如需加密，可使用nginx反向代理添加TLS。

**Q: 可以用于其他LSP服务器吗？**  
A: 可以！只需修改`--lsp`参数为对应的LSP服务器命令即可。

**Q: 性能对比如何？**  
A: Python版和Kotlin版性能相近，都能满足开发需求。Rust版（lsp-ws-proxy）性能最优但不支持项目同步。

---

**最后更新**: 2026-01-05  
**维护者**: TinaIDE Team
