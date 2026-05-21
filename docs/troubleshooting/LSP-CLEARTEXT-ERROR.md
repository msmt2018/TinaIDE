# LSP 连接明文通信错误修复记录

## 错误信息

```
连接失败：CLEARTEXT communication to 127.0.0.1 not permitted by network security policy
```

## 问题背景

- **发生场景**：启用远程 LSP 功能，尝试通过 WebSocket 连接本地回环地址 `127.0.0.1:48488`
- **Android 版本**：Android 9 (API 28) 及以上
- **日期**：2026-01-07

## 原因分析

从 Android 9 (API 28) 开始，系统默认禁止应用进行明文（HTTP/WebSocket）网络通信，即使是本地回环地址 `127.0.0.1` 也受此限制。

这是 Android 的网络安全策略变更，目的是强制应用使用 HTTPS/WSS 等加密通信方式。

## 解决方案

### 1. 创建网络安全配置文件

文件路径：`app/src/main/res/xml/network_security_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    网络安全配置

    允许明文通信（HTTP/WS），用于：
    - 远程 LSP 服务器连接（ws://pc-ip:port）
    - 本地开发和调试（127.0.0.1、localhost）

    注意：TinaIDE 作为开发工具需要连接各种本地/远程服务器
-->
<network-security-config>
    <!-- 允许所有明文通信（开发工具必需） -->
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

### 2. 在 AndroidManifest.xml 中引用配置

在 `<application>` 标签中添加以下属性：

```xml
<application
    ...
    android:networkSecurityConfig="@xml/network_security_config"
    android:usesCleartextTraffic="true">
```

### 3. 重新构建并安装

修改配置后，必须重新构建并安装 APK：

```powershell
# 真机（arm64-v8a）
pwsh ./tools/build-apk.ps1 -Variant debug -Abi arm64 -Install

# 模拟器（x86_64）
pwsh ./tools/build-apk.ps1 -Variant debug -Abi x86 -Install
```

或在 Android Studio 中直接点击 Run。

## 配置说明

### 为什么使用 base-config 而非 domain-config？

最初尝试使用 `domain-config` 针对特定域名/IP 配置：

```xml
<!-- 不推荐：对 IP 地址配置可能存在兼容性问题 -->
<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">127.0.0.1</domain>
    <domain includeSubdomains="true">localhost</domain>
</domain-config>
```

但这种方式存在问题：
1. `includeSubdomains` 对 IP 地址没有实际意义
2. 不同 Android 版本对 IP 地址的 domain-config 解析可能有差异
3. TinaIDE 作为开发工具，需要连接各种本地和远程服务器

因此采用 `base-config` 允许所有明文通信，更简单可靠。

### trust-anchors 配置

```xml
<trust-anchors>
    <certificates src="system" />
    <certificates src="user" />
</trust-anchors>
```

- `system`：信任系统预装的 CA 证书
- `user`：信任用户安装的 CA 证书（用于调试 HTTPS 等场景）

## 相关文件

| 文件 | 说明 |
|------|------|
| `app/src/main/res/xml/network_security_config.xml` | 网络安全配置 |
| `app/src/main/AndroidManifest.xml` | 应用清单（第 38-39 行引用配置） |

## 参考资料

- [Android Network Security Configuration](https://developer.android.com/training/articles/security-config)
- [Opt out of cleartext traffic](https://developer.android.com/guide/topics/manifest/application-element#usesCleartextTraffic)

## 修复记录

- **修复人**：Claude Code
- **修复日期**：2026-01-07
- **修复内容**：简化 network_security_config.xml，使用 base-config 允许所有明文通信
