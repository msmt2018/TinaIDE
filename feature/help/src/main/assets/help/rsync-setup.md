# rsync 同步配置

如果选择使用 rsync 增量同步方案，需要在 PC 端配置 rsync daemon。

## Linux/macOS 配置

### 1. 创建配置文件

```bash
# 创建配置目录
sudo mkdir -p /etc/rsyncd

# 创建配置文件
sudo tee /etc/rsyncd.conf << 'EOF'
# TinaIDE rsync daemon 配置
pid file = /var/run/rsyncd.pid
lock file = /var/run/rsync.lock
log file = /var/log/rsyncd.log

# TinaIDE 工作区模块
[tina-workspace]
    # 工作区路径（clangd 将在此目录工作）
    path = /tmp/tina-workspace
    # 允许写入
    read only = no
    # 允许列出模块
    list = yes
    # 运行用户（建议使用当前用户）
    uid = $USER
    gid = $USER
    # 注释说明
    comment = TinaIDE Project Workspace
EOF

# 创建工作区目录
mkdir -p /tmp/tina-workspace
```

### 2. 启动 rsync daemon

**临时启动**（测试用）：

```bash
rsync --daemon --config=/etc/rsyncd.conf --no-detach
```

**后台启动**：

```bash
rsync --daemon --config=/etc/rsyncd.conf
```

**使用 systemd 管理**（推荐）：

```bash
# 创建 systemd 服务文件
sudo tee /etc/systemd/system/rsyncd-tina.service << 'EOF'
[Unit]
Description=TinaIDE rsync daemon
After=network.target

[Service]
Type=forking
ExecStart=/usr/bin/rsync --daemon --config=/etc/rsyncd.conf
ExecReload=/bin/kill -HUP $MAINPID
Restart=on-failure

[Install]
WantedBy=multi-user.target
EOF

# 启用并启动服务
sudo systemctl daemon-reload
sudo systemctl enable rsyncd-tina
sudo systemctl start rsyncd-tina

# 查看状态
sudo systemctl status rsyncd-tina
```

### 3. 防火墙配置

```bash
# Ubuntu/Debian (ufw)
sudo ufw allow 873/tcp

# CentOS/RHEL (firewalld)
sudo firewall-cmd --permanent --add-port=873/tcp
sudo firewall-cmd --reload
```

## Windows 配置

Windows 上可以使用 cwRsync 或 WSL2。

### 方式 1：使用 WSL2（推荐）

```powershell
# 在 WSL2 中按照 Linux 配置步骤操作
wsl

# 然后按照上面的 Linux 配置步骤
```

### 方式 2：使用 cwRsync

1. 下载 [cwRsync](https://www.itefix.net/cwrsync)
2. 解压到 `C:\cwRsync`
3. 创建配置文件 `C:\cwRsync\rsyncd.conf`：

```ini
use chroot = false
strict modes = false
log file = C:\cwRsync\rsyncd.log

[tina-workspace]
    path = C:\TinaWorkspace
    read only = no
    list = yes
    comment = TinaIDE Project Workspace
```

4. 启动服务：

```cmd
cd C:\cwRsync\bin
rsync --daemon --config=C:\cwRsync\rsyncd.conf
```

## 验证配置

在 PC 端验证 rsync daemon 是否正常运行：

```bash
# 列出可用模块
rsync rsync://localhost/

# 应该显示：
# tina-workspace    TinaIDE Project Workspace
```

## 手机端配置

1. 设置 → 语言服务器
2. 同步模式：项目模式
3. 同步方案：rsync 增量同步
4. rsync 模块：`tina-workspace`
5. rsync 端口：`873`

## 安全建议

1. **局域网使用**：确保 rsync daemon 只在可信网络中运行
2. **限制访问**：在 `rsyncd.conf` 中使用 `hosts allow` 限制客户端 IP
3. **使用 SSH 隧道**：对于公网访问，建议通过 SSH 隧道转发 rsync 流量

```bash
# 在手机端建立 SSH 隧道（需要 Termux）
ssh -L 873:localhost:873 user@pc-ip

# 然后 TinaIDE 连接 localhost:873
```

## 常见问题

**Q: rsync 连接超时？**

检查防火墙是否开放 873 端口，确认 rsync daemon 正在运行。

**Q: 权限被拒绝？**

检查 `rsyncd.conf` 中的 `uid/gid` 设置，确保有写入权限。

**Q: 模块不存在？**

确认 `rsyncd.conf` 中定义了 `[tina-workspace]` 模块，并且 rsync daemon 已重启。
