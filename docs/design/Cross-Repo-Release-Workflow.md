# 跨仓库 Release 工作流设计

## 概述

本文档描述如何实现从 `tinaide-pro`（私有仓库）编译后，将 release 产物发布到 `TinaIDE`（开源仓库）的自动化工作流。

## 架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                      tinaide-pro (私有仓库)                          │
│                                                                     │
│  1. Push tag (v*) ──► 2. Build APK ──► 3. Upload Artifact          │
│                                              │                      │
│                                              ▼                      │
│                                    4. Trigger TinaIDE workflow      │
│                                       (repository_dispatch)         │
└─────────────────────────────────────────────────────────────────────┘
                                              │
                                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      TinaIDE (开源仓库)                              │
│                                                                     │
│  5. Receive dispatch ──► 6. Download APK ──► 7. Create Release     │
│                              from tinaide-pro                       │
└─────────────────────────────────────────────────────────────────────┘
```

## 快速配置指南

### 第一步：创建 Personal Access Tokens (PAT)

你需要创建 **两个** Fine-grained PAT：

#### Token 1: TINAIDE_REPO_TOKEN（用于触发开源仓库）

1. 打开 https://github.com/settings/tokens?type=beta
2. 点击 **"Generate new token"**
3. 配置：
   - **Token name**: `tinaide-release-trigger`
   - **Expiration**: 90 天或更长
   - **Repository access**: 选择 **"Only select repositories"** → 选择 **TinaIDE**（开源仓库）
   - **Permissions**:
     - `Contents`: **Read and write**
     - `Actions`: **Write**
4. 点击 **"Generate token"** 并复制

#### Token 2: PRO_REPO_TOKEN（用于读取私有仓库 artifacts）

1. 再次打开 https://github.com/settings/tokens?type=beta
2. 点击 **"Generate new token"**
3. 配置：
   - **Token name**: `tinaide-pro-artifacts-reader`
   - **Expiration**: 90 天或更长
   - **Repository access**: 选择 **"Only select repositories"** → 选择 **tinaide-pro**（私有仓库）
   - **Permissions**:
     - `Actions`: **Read**
     - `Contents`: **Read**
4. 点击 **"Generate token"** 并复制

### 第二步：配置仓库 Secrets

#### 在 tinaide-pro（私有仓库）添加 Secrets

1. 打开 https://github.com/你的用户名/tinaide-pro/settings/secrets/actions
2. 点击 **"New repository secret"**，添加：

| Name | Value |
|------|-------|
| `TINAIDE_REPO_TOKEN` | Token 1 的值 |
| `TINAIDE_REPO` | `wuxianggujun/TinaIDE` |

#### 在 TinaIDE（开源仓库）添加 Secrets

1. 打开 https://github.com/wuxianggujun/TinaIDE/settings/secrets/actions
2. 点击 **"New repository secret"**，添加：

| Name | Value |
|------|-------|
| `PRO_REPO_TOKEN` | Token 2 的值 |

### 第三步：创建 Workflow 文件

#### tinaide-pro 仓库

文件已创建：`.github/workflows/release.yml`

#### TinaIDE 开源仓库

在开源仓库创建文件 `.github/workflows/receive-release.yml`（模板文件见 `docs/workflows/receive-release.yml`）

### 第四步：测试发布

#### ⚠️ 发布前检查清单

在创建 tag 之前，**必须**完成以下检查：

```bash
# 1. 检查子模块状态
git submodule status

# 如果看到 commit hash 前面有 "-" 或 "+"，说明子模块需要同步
# "-" 表示子模块未初始化
# "+" 表示子模块有本地更改

# 2. 确保所有子模块的更改都已推送
cd temp/tina-sora-editor
git status
git push origin HEAD  # 如果有未推送的 commit

cd ../tina-android-tree-sitter
git status
git push origin HEAD  # 如果有未推送的 commit

# 3. 返回主仓库
cd ../..

# 4. 更新 CHANGELOG.md 和 version.properties
# 5. 提交所有更改
git add .
git commit -m "release: vX.X.X"
git push origin main
```

#### 方式一：推送 Tag（正式发布）

```bash
# 在 tinaide-pro 仓库
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

#### 方式二：手动触发（测试用）

1. 打开 tinaide-pro 的 Actions 页面
2. 选择 "Build and Release" workflow
3. 点击 "Run workflow"
4. 选择构建类型（debug/release）
5. 如果是 release，可勾选 "是否触发开源仓库发布"
6. 点击 "Run workflow"

---

---

## 子模块管理

### 子模块结构

本项目包含两个子模块：

| 子模块 | 路径 | 远程仓库 |
|--------|------|----------|
| tina-sora-editor | `temp/tina-sora-editor` | https://github.com/wuxianggujun/tina-sora-editor |
| tina-android-tree-sitter | `external/tina-android-tree-sitter` | https://github.com/wuxianggujun/tina-android-tree-sitter |

### 常见子模块问题

#### 问题：CI 构建失败 - "not our ref"

**错误信息：**
```
fatal: remote error: upload-pack: not our ref d3f4b6591d7ce32b747bbba2e7ccd9c1414cf7a8
fatal: Fetched in submodule path 'temp/tina-sora-editor', but it did not contain d3f4b6591d7ce32b747bbba2e7ccd9c1414cf7a8
```

**原因：** 主仓库引用了子模块的一个 commit，但该 commit 未推送到子模块的远程仓库。

**解决方案：**
```bash
# 1. 进入子模块目录
cd temp/tina-sora-editor

# 2. 检查本地和远程的差异
git log --oneline -5
git log --oneline origin/main -5

# 3. 推送本地 commit 到远程
git push origin HEAD:main

# 4. 返回主仓库，重新触发 CI
cd ../..
git tag -d vX.X.X
git push origin --delete vX.X.X
git tag -a vX.X.X -m "Release vX.X.X"
git push origin vX.X.X
```

#### 问题：子模块未初始化

**错误信息：** `git submodule status` 显示 commit hash 前有 `-`

**解决方案：**
```bash
git submodule update --init --recursive
```

### 子模块同步脚本

可以创建一个脚本来自动检查和同步子模块：

```bash
#!/bin/bash
# scripts/sync-submodules.sh

echo "检查子模块状态..."

for submodule in temp/tina-sora-editor external/tina-android-tree-sitter; do
    echo "检查 $submodule..."
    cd $submodule
    
    # 获取本地和远程的 HEAD
    LOCAL_HEAD=$(git rev-parse HEAD)
    REMOTE_HEAD=$(git rev-parse origin/$(git rev-parse --abbrev-ref HEAD) 2>/dev/null || echo "unknown")
    
    if [ "$LOCAL_HEAD" != "$REMOTE_HEAD" ]; then
        echo "⚠️  $submodule 有未推送的 commit"
        echo "   本地: $LOCAL_HEAD"
        echo "   远程: $REMOTE_HEAD"
        read -p "是否推送? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            git push origin HEAD
        fi
    else
        echo "✅ $submodule 已同步"
    fi
    
    cd - > /dev/null
done

echo "子模块检查完成"
```

---

## 详细配置说明

### Secrets 配置总览

| 仓库 | Secret 名称 | 用途 | 权限要求 |
|------|-------------|------|----------|
| tinaide-pro | `TINAIDE_REPO_TOKEN` | 触发开源仓库 workflow | TinaIDE: Contents(rw), Actions(w) |
| tinaide-pro | `TINAIDE_REPO` | 开源仓库名称 | 无（只是字符串） |
| TinaIDE | `PRO_REPO_TOKEN` | 读取私有仓库 artifacts | tinaide-pro: Actions(r), Contents(r) |

### Workflow 文件说明

#### tinaide-pro/.github/workflows/release.yml

```yaml
name: Build and Release

on:
  # 只在推送 tag 时触发构建和发布
  push:
    tags:
      - 'v*'
  
  # 手动触发（用于测试或特殊情况）
  workflow_dispatch:
    inputs:
      build_type:
        description: '构建类型'
        required: true
        default: 'debug'
        type: choice
        options:
          - debug
          - release
      trigger_opensource:
        description: '是否触发开源仓库发布（仅 release 构建有效）'
        required: false
        default: 'false'
        type: boolean

# ... 完整内容见实际文件
```

**触发条件：**
- 推送 tag（如 v1.0.0）：自动编译 release 并触发开源仓库发布
- 手动触发：可选择 debug/release 构建类型，release 时可选择是否触发开源仓库发布

**注意：** 推送到 main 分支**不会**触发构建，避免浪费 CI 资源。

#### TinaIDE/.github/workflows/receive-release.yml（模板见 `docs/workflows/receive-release.yml`）

```yaml
name: Receive and Publish Release

on:
  repository_dispatch:
    types: [pro-release]
  workflow_dispatch:
    inputs:
      version:
        description: '版本号 (如 v1.0.0)'
        required: true
        type: string

# ... 完整内容见 docs/workflows/receive-release.yml
```

**触发条件：**
- `repository_dispatch`：由 tinaide-pro 触发
- `workflow_dispatch`：手动触发（用于测试）

---

## 签名配置（可选）

如果需要对 APK 进行签名，在 tinaide-pro 仓库添加以下 Secrets：

| Name | Value |
|------|-------|
| `SIGNING_KEY` | keystore 文件的 Base64 编码 |
| `KEY_STORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | key 别名 |
| `KEY_PASSWORD` | key 密码 |

**生成 SIGNING_KEY：**
```bash
base64 -i your-keystore.jks | tr -d '\n'
```

---

## 故障排除

### 常见问题

#### 1. Token 权限不足

**错误信息：** `Resource not accessible by integration`

**解决方案：**
- 检查 PAT 的权限配置
- 确保选择了正确的仓库
- 确保 token 没有过期

#### 2. Artifact 下载失败

**错误信息：** `Unable to find any artifacts`

**解决方案：**
- 检查 run_id 是否正确
- 确保 artifact 还在保留期内（默认 30 天）
- 检查 PRO_REPO_TOKEN 是否有 Actions 读取权限

#### 3. Release 创建失败

**错误信息：** `Validation Failed: tag already exists`

**解决方案：**
- 删除已存在的 tag 和 release
- 或使用新的版本号

#### 4. Workflow 未触发

**可能原因：**
- TINAIDE_REPO_TOKEN 没有 Actions 写入权限
- TINAIDE_REPO 格式错误（应该是 `owner/repo`，不是完整 URL）

#### 5. 子模块 commit 不存在

**错误信息：** `not our ref` 或 `did not contain xxx`

**解决方案：**
```bash
# 进入子模块目录
cd temp/tina-sora-editor  # 或 tina-android-tree-sitter

# 推送本地 commit
git push origin HEAD

# 返回主仓库，重新创建 tag
cd ../..
```

#### 6. 开源仓库 tag 推送失败 (403)

**错误信息：** `Permission to xxx.git denied to github-actions[bot]`

**解决方案：**
1. 确保开源仓库 workflow 有 `permissions: contents: write`
2. 创建 Fine-grained PAT 并添加为 `RELEASE_TOKEN` secret
3. 在 checkout 和 create tag 步骤使用该 token

### 调试技巧

1. **查看 workflow 日志**
   - 在 Actions 页面查看详细日志
   - 检查每个步骤的输出

2. **手动触发测试**
   - 使用 `workflow_dispatch` 手动触发
   - 可以快速验证配置是否正确

3. **检查 Secrets**
   - Secrets 添加后无法查看值
   - 如果不确定，可以删除重新添加

---

## 版本管理建议

### Tag 命名规范

| 格式 | 说明 | 示例 |
|------|------|------|
| `v1.0.0` | 正式版本 | v1.0.0, v2.1.3 |
| `v1.0.0-beta.1` | Beta 版本 | v1.0.0-beta.1 |
| `v1.0.0-alpha.1` | Alpha 版本 | v1.0.0-alpha.1 |
| `v1.0.0-rc.1` | Release Candidate | v1.0.0-rc.1 |

### 发布流程

1. **检查子模块状态**
   ```bash
   git submodule status
   # 确保所有子模块的 commit 都已推送到远程
   ```

2. **更新版本信息**
   - 更新 `version.properties`（versionName 和 versionCode）
   - 更新 `CHANGELOG.md`（添加新版本的更新内容）

3. **提交代码**
   ```bash
   git add .
   git commit -m "release: vX.X.X - 版本描述"
   git push origin main
   ```

4. **创建并推送 tag**
   ```bash
   git tag -a vX.X.X -m "Release vX.X.X"
   git push origin vX.X.X
   ```

5. **等待 CI/CD 完成**
   - 私有仓库：构建 APK，提取 CHANGELOG
   - 开源仓库：创建 Release，上传 APK

6. **验证发布**
   - 检查开源仓库的 Release 页面
   - 确认 APK 文件和 Release Notes 正确

### CHANGELOG 自动同步

从 v0.6.49 开始，私有仓库的 CHANGELOG 内容会自动同步到开源仓库的 Release Notes：

1. 私有仓库 workflow 从 `CHANGELOG.md` 提取当前版本的更新内容
2. 使用 base64 编码传递到开源仓库
3. 开源仓库 workflow 解码并添加到 Release Notes

**CHANGELOG 格式要求：**
```markdown
## [X.X.X] - YYYY-MM-DD

### 新增
- 功能描述

### 改进
- 改进描述

### 修复
- 修复描述

---
```

每个版本之间用 `---` 分隔，workflow 会自动提取对应版本的内容。

---

## 安全考虑

### Token 权限最小化

- 使用 Fine-grained PAT 而非 Classic PAT
- 只授予必要的仓库访问权限
- 只授予必要的操作权限

### Secrets 管理

- 定期轮换 token（建议每 90 天）
- 不要在日志中打印敏感信息
- 使用 GitHub 的 Dependabot secrets 扫描

### 代码保护

- tinaide-pro 的源码不会暴露
- 只有编译后的 APK 会发布到开源仓库
- 可以在编译时移除调试信息和敏感配置

---

## 总结

通过这个工作流，你可以：

✅ 在 tinaide-pro 中开发付费功能，代码保持私有
✅ 推送 tag 后自动编译 APK
✅ 自动将 APK 发布到开源仓库 TinaIDE
✅ 用户从 https://github.com/wuxianggujun/TinaIDE/releases 下载
