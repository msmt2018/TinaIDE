# Linux Distro Manifest Tools

本目录用于维护 `:core:linux-distro` 的自研发行版 manifest 数据。

## 原则

- 只整理发行版官方源数据，例如 Alpine Linux 官方 CDN、Ubuntu Base 官方 cdimage 与校验文件。
- 不复制外部脚本项目的 shell 脚本、插件字段结构或发行版元数据。
- `linux-distros.lock.json` 是人工审核后的锁定源数据，`manifest.json` 由脚本生成。
- 新增发行版前先确认官方 rootfs 来源、架构映射、文件大小和 SHA-256。

## 当前发行版

- Alpine Linux 3.23：`AARCH64`、`ARM`、`X86_64`、`I686`。
- Ubuntu 24.04 LTS：`AARCH64`、`ARM`、`X86_64`，官方 Ubuntu Base 当前不提供 `I686`。

## 生成 manifest

```powershell
pwsh tools/linux-distro/generate-linux-distro-manifest.ps1
```

输出文件：

```text
core/linux-distro/src/main/assets/linux-distro/manifest.json
```

## 刷新官方元数据

刷新全部已支持的官方源：

```powershell
pwsh tools/linux-distro/generate-linux-distro-manifest.ps1 -RefreshRemoteMetadata -StampNow
```

只刷新 Alpine：

```powershell
pwsh tools/linux-distro/generate-linux-distro-manifest.ps1 -RefreshAlpineMetadata -StampNow
```

只刷新 Ubuntu Base：

```powershell
pwsh tools/linux-distro/generate-linux-distro-manifest.ps1 -RefreshUbuntuMetadata -StampNow
```

刷新命令会访问发行版官方地址：

- `https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/<arch>/alpine-minirootfs-<version>-<arch>.tar.gz`
- `https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/<arch>/alpine-minirootfs-<version>-<arch>.tar.gz.sha256`
- `https://cdimage.ubuntu.com/cdimage/ubuntu-base/releases/24.04/release/ubuntu-base-<version>-base-<arch>.tar.gz`
- `https://cdimage.ubuntu.com/cdimage/ubuntu-base/releases/24.04/release/SHA256SUMS`

刷新后必须人工审查 `linux-distros.lock.json` 与生成的 assets manifest 差异。