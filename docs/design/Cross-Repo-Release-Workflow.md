# Release 工作流边界

> 更新日期：2026-05-21

## 当前结论

旧的“私有仓库构建 -> `repository_dispatch` 派发到公开仓库”的发布方案已经废弃。

TinaIDE 公开仓库现在只保留自身的发布入口：

- 推送 `v*` tag 时构建 Release APK 并创建 GitHub Release
- 手动 `workflow_dispatch` 时按输入构建 Debug 或 Release
- Release APK 文件名统一为 `TinaIDE-<version>-<abi>.apk`

对应事实源是 `.github/workflows/release.yml`。

## 当前发布链路

```text
[推送 v* tag 或手动触发]
        |
        v
[wuxianggujun/TinaIDE GitHub Actions]
        |
        +-- 构建 arm64-v8a Release APK
        +-- 构建 x86_64 Release APK
        +-- 校验 APK manifest 与 ABI
        |
        v
[GitHub Release]
        |
        +-- TinaIDE-<version>-arm64-v8a.apk
        +-- TinaIDE-<version>-x86_64.apk
```

## 不再使用的配置

公开仓库不再需要以下 secret 或事件：

- `TINAIDE_REPO_TOKEN`
- `TINAIDE_REPO`
- `PRO_REPO_TOKEN`
- `repository_dispatch: pro-release`

如果后续重新引入跨仓库发布，需要单独创建新的设计文档，并重新评估权限边界、
私有仓库暴露面、产物签名和 Release 回滚策略。

## Release 前检查

创建 tag 前至少确认：

```bash
git status --short --branch
git submodule status --recursive
git log -1 --oneline --decorate
```

如果子模块指针发生变化，必须先提交并推送子模块仓库，再提交并推送主仓库。
否则 CI 的 `git submodule update --recursive` 可能因为 `not our ref <sha>` 失败。

## 签名与产物

Release 构建需要在 GitHub Actions secrets 中配置：

- `TINAIDE_RELEASE_KEYSTORE_B64`
- `TINAIDE_RELEASE_KEYSTORE_PASSWORD`
- `TINAIDE_RELEASE_KEY_ALIAS`
- `TINAIDE_RELEASE_KEY_PASSWORD`

这些值只用于公开仓库的 tag / release 构建，不要写入代码、文档示例输出或构建日志。

## Mapping 处理

Release 构建默认只备份 R8 mapping 文件，不上传到私有后端。

如维护者确实需要上传，应显式开启：

```bash
./gradlew :app:assembleReleaseAllAbi -Ptina.allAbi=true \
  -Ptina.releaseMapping.uploadEnabled=true \
  -Ptina.releaseMapping.serverUrl=https://your-private-host
```

这个默认值的目的很明确：开源仓库构建不应在无人确认的情况下连接私有服务。
