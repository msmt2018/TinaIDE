# TinaIDE GitHub Registry

> 更新日期：2026-06-06

TinaIDE 开源版的插件市场与依赖包市场不再从 TinaServer 读取索引。
客户端默认读取公开仓库：

```text
https://github.com/wuxianggujun/TinaIDE-Registry
```

这个仓库是插件与依赖包的公开 Registry，不是 Android 主项目的源码目录。
它现在同时承载：

- `plugins/index.v2.json` / `packages/index.v2.json`
- `plugins/index.json` / `packages/index.json` 兼容索引
- `plugins/<plugin-id>/plugin.json`
- `plugins/<plugin-id>/<version>/*.tinaplug`
- `packages/<package-id>/package.json`
- `packages/<package-id>/<version>/*`
- `sources/plugins/**`
- `sources/plugin-starters/**`
- `metadata/*.json`
- `scripts/*.ps1`

发布插件或依赖包时，需要把 `.tinaplug` / 包文件放入该仓库约定目录，或在索引里填写
可信 CDN、对象存储、自建代理的绝对下载地址，并同步更新对应索引。

客户端内置两个 Registry base，按顺序自动尝试：

```text
https://raw.githubusercontent.com/wuxianggujun/TinaIDE-Registry/main
https://cdn.jsdelivr.net/gh/wuxianggujun/TinaIDE-Registry@main
```

默认优先走 GitHub Raw，避免 jsDelivr 缓存旧索引导致市场列表为空；
Raw 不可用时再回退到 jsDelivr CDN。
如果用户设备配置了系统代理，OkHttp 会继续按系统代理策略发起请求。

不建议把插件/依赖索引托管在不可信的第三方 GitHub 加速代理上，因为索引会决定
后续下载地址。需要更稳定的国内下载体验时，优先把具体包文件同步到你信任的
CDN、对象存储或自建代理，并在索引里填写绝对 URL。

GitHub Raw 兜底地址：

```text
https://raw.githubusercontent.com/wuxianggujun/TinaIDE-Registry/main
```

## 目录结构

```text
plugins/index.v2.json
plugins/index.json
plugins/<plugin-id>/plugin.json
plugins/<plugin-id>/<version>/<plugin-id>.tinaplug
packages/index.v2.json
packages/index.json
packages/<package-id>/package.json
packages/<package-id>/<version>/<file>.tar.xz
sources/plugins/<plugin-id>/manifest.json
sources/plugin-starters/<template>/
metadata/plugins.json
metadata/packages.json
scripts/build-registry.ps1
```

客户端优先读取 v2 索引；v2 不存在、请求失败或解析失败时，自动回退旧的
`plugins/index.json` / `packages/index.json`。Registry 可以先保留 v1 全量索引，
再逐步新增 v2 轻量索引和详情文件。

`download_url` 和 `download_sources[].url` 支持两种写法：

- 绝对 URL：客户端原样访问。
- 相对路径：客户端会拼到本次成功加载索引的 Registry base 后面。
  默认通常是 GitHub Raw，失败后才会是 jsDelivr CDN。

国内网络建议：

- 小文件、索引、示例插件可以继续使用相对路径，客户端会优先走 GitHub Raw。
- 大文件、依赖包、运行时包建议填写你自己的 CDN/对象存储绝对 URL。
- 不要把未校验的大文件只放在随机公开代理上；能填写 `sha256:` 时必须填写。

## 构建索引

Registry 仓库内执行：

```powershell
pwsh ./scripts/build-registry.ps1
```

脚本会重新打包 `sources/plugins/**`，计算插件包和依赖包的 SHA-256，
并重写 v2 与 v1 兼容产物：

- `plugins/index.v2.json`：插件轻量列表。
- `plugins/<plugin-id>/plugin.json`：单个插件详情和版本历史。
- `plugins/index.json`：旧客户端兼容的全量索引。
- `packages/index.v2.json`：依赖包轻量列表。
- `packages/<package-id>/package.json`：单个包详情、版本和下载信息。
- `packages/index.json`：旧客户端兼容的全量索引。

## v2 索引设计

v2 的核心目标是避免客户端每次刷新市场都下载和解析全量详情。
列表页只读取轻量索引；用户打开详情、安装、检查更新时，再按需读取单个详情文件。

```text
[index.v2.json] --列表/搜索/分类--> [详情页或安装] --按需--> [<id>/plugin.json 或 <id>/package.json]
```

轻量索引必须包含列表展示、分类过滤和排序需要的字段。详情文件包含版本历史、
下载地址、hash、changelog、包下载源等较重字段。

## 协议生命周期

当前主协议是 v2。v1 全量索引只作为旧客户端兼容层保留，不再作为新能力入口。

- `0.17.11`：Android 客户端引入 v2 优先读取，并把 v1 fallback 标记为废弃兼容层。
- `0.18.x` / `0.19.x`：迁移窗口，Registry 必须继续生成 v2 与 v1 两套产物。
- `0.20.0` 起：Android 客户端可以删除 v1 fallback 代码，市场读取只要求 v2。
- `0.21.0` 起：Registry 可以停止生成 `plugins/index.json` / `packages/index.json`。

移除 v1 前必须同时满足：

- `plugins/index.v2.json` / `packages/index.v2.json` 已连续稳定发布。
- 所有插件和依赖包都有有效的 `detail_url` 与详情文件。
- 发布说明明确通知旧客户端将不再支持 v1 Registry。
- `scripts/validate-registry.ps1` 已能阻止 v2 详情缺失和轻量索引混入重字段。

删除 v1 fallback 时，Android 仓库要同步移除 `PluginRegistryIndex` /
`PackageRegistryIndex` 的回退读取测试；Registry 仓库要同步移除 v1 生成和 v1
校验逻辑。

## 插件索引

### 插件 v2 轻量索引

`plugins/index.v2.json` 的推荐结构：

```json
{
  "schema_version": 2,
  "generated_at": "2026-06-06T00:00:00Z",
  "plugins": [
    {
      "id": "tinaide.plugin.example",
      "plugin_id": "tinaide.plugin.example",
      "name": "Example Plugin",
      "description": "Example plugin",
      "category": "tool",
      "tags": ["tool"],
      "publisher": {
        "id": "tinaide",
        "display_name": "TinaIDE"
      },
      "latest_version": "1.0.0",
      "detail_url": "plugins/tinaide.plugin.example/plugin.json",
      "created_at": "2026-05-21T00:00:00Z",
      "updated_at": "2026-06-06T00:00:00Z"
    }
  ]
}
```

`plugins/<plugin-id>/plugin.json` 使用和旧索引单个插件条目接近的详情结构：

```json
{
  "id": "tinaide.plugin.example",
  "plugin_id": "tinaide.plugin.example",
  "name": "Example Plugin",
  "description": "Example plugin",
  "category": "tool",
  "tags": ["tool"],
  "publisher": {
    "id": "tinaide",
    "display_name": "TinaIDE"
  },
  "versions": [
    {
      "version": "1.0.0",
      "version_code": 1,
      "file_size": 1234,
      "file_hash": "sha256:<sha256>",
      "download_url": "plugins/tinaide.plugin.example/1.0.0/tinaide.plugin.example.tinaplug",
      "created_at": "2026-05-21T00:00:00Z"
    }
  ],
  "created_at": "2026-05-21T00:00:00Z",
  "updated_at": "2026-06-06T00:00:00Z"
}
```

### 插件 v1 兼容索引

`plugins/index.json` 的最小结构：

```json
{
  "plugins": [
    {
      "id": "tinaide.plugin.example",
      "plugin_id": "tinaide.plugin.example",
      "name": "Example Plugin",
      "description": "Example plugin",
      "category": "tool",
      "tags": ["tool"],
      "publisher": {
        "id": "tinaide",
        "display_name": "TinaIDE"
      },
      "versions": [
        {
          "version": "1.0.0",
          "version_code": 1,
          "file_size": 1234,
          "file_hash": "sha256:<sha256>",
          "download_url": "plugins/tinaide.plugin.example/1.0.0/tinaide.plugin.example.tinaplug",
          "created_at": "2026-05-21T00:00:00Z"
        }
      ],
      "download_count": 0,
      "rating_avg": 0.0,
      "rating_count": 0,
      "created_at": "2026-05-21T00:00:00Z",
      "updated_at": "2026-05-21T00:00:00Z"
    }
  ]
}
```

`file_hash` 是推荐字段。填写后客户端会做 SHA-256 校验；未填写时只下载，
不做完整性校验。

## 依赖包索引

### 依赖包 v2 轻量索引

`packages/index.v2.json` 的推荐结构：

```json
{
  "schema_version": 2,
  "generated_at": "2026-06-06T00:00:00Z",
  "categories": [
    {
      "id": "runtime",
      "name": "Runtime",
      "sort_order": 0
    }
  ],
  "packages": [
    {
      "id": "sdl3",
      "name": "SDL3",
      "description": "SDL runtime package",
      "category": "runtime",
      "detail_url": "packages/sdl3/package.json",
      "android": {
        "version": "3.2.0",
        "artifact_type": "shared",
        "install_type": "download",
        "size": 1234,
        "abi": ["arm64-v8a", "x86_64"],
        "is_latest": true
      }
    }
  ]
}
```

`packages/<package-id>/package.json` 承载完整详情、版本和下载源：

```json
{
  "package": {
    "id": "sdl3",
    "name": "SDL3",
    "description": "SDL runtime package",
    "category": "runtime",
    "android": {
      "version": "3.2.0",
      "artifact_type": "shared",
      "install_type": "download",
      "size": 1234,
      "download_url": "packages/sdl3/3.2.0/sdl3.tar.xz",
      "checksum": "sha256:<sha256>",
      "abi": ["arm64-v8a", "x86_64"],
      "is_latest": true
    }
  },
  "versions": {
    "android": [
      {
        "id": 2,
        "package_id": "sdl3",
        "platform": "android",
        "version": "3.2.0",
        "artifact_type": "shared",
        "install_type": "download",
        "download_size": 1234,
        "download_url": "packages/sdl3/3.2.0/sdl3.tar.xz",
        "checksum": "sha256:<sha256>",
        "abi": ["arm64-v8a", "x86_64"],
        "is_latest": true
      }
    ]
  },
  "downloads": {
    "sdl3:2": {
      "package_id": "sdl3",
      "version": "3.2.0",
      "platform": "android",
      "install_type": "download",
      "size": 1234,
      "checksum": "sha256:<sha256>",
      "sources": [
        {
          "id": 1,
          "name": "GitHub",
          "url": "packages/sdl3/3.2.0/sdl3.tar.xz",
          "priority": 100,
          "supports_range": true
        }
      ]
    }
  }
}
```

`downloads` 的 key 保持 `<package-id>:<version-id>`，与 v1 全量索引一致。

### 依赖包 v1 兼容索引

`packages/index.json` 支持简单结构。下载信息可以直接写在 `linux` 或
`android` 节点里：

```json
{
  "categories": [
    {
      "id": "runtime",
      "name": "Runtime",
      "sort_order": 0
    }
  ],
  "packages": [
    {
      "id": "sdl3",
      "name": "SDL3",
      "description": "SDL runtime package",
      "category": "runtime",
      "android": {
        "version": "3.2.0",
        "artifact_type": "shared",
        "install_type": "download",
        "size": 1234,
        "download_url": "packages/sdl3/3.2.0/sdl3.tar.xz",
        "checksum": "sha256:<sha256>",
        "abi": ["arm64-v8a", "x86_64"],
        "is_latest": true
      }
    }
  ]
}
```

如果一个包需要多版本，也可以使用 `versions` 映射：

```json
{
  "packages": [
    {
      "id": "sdl3",
      "name": "SDL3",
      "category": "runtime"
    }
  ],
  "versions": {
    "sdl3": {
      "android": [
        {
          "id": 2,
          "package_id": "sdl3",
          "platform": "android",
          "version": "3.2.0",
          "artifact_type": "shared",
          "install_type": "download",
          "download_size": 1234,
          "download_url": "packages/sdl3/3.2.0/sdl3.tar.xz",
          "checksum": "sha256:<sha256>",
          "abi": ["arm64-v8a", "x86_64"],
          "is_latest": true
        }
      ]
    }
  }
}
```

## Android 包产物类型和 ABI

Android 依赖包不按 ABI 拆成多个逻辑包。一个库只保留一个 `package_id`，
用 `artifact_type` 和 `abi` 表达包内容与设备兼容性。

- `artifact_type` 可取 `source`、`header`、`static`、`shared`、`executable`、`mixed`。
- `source` / `header` 包不写 `abi`，表示所有 ABI 都可安装。
- `static` / `shared` / `executable` 包必须写 `abi`。
- 客户端会在下载前拦截不匹配的 Android ABI。
- 同一个包可以同时包含多个 ABI 的二进制内容，例如 `lib/arm64-v8a/` 和 `lib/x86_64/`。

客户端会继续保留本地安装状态、下载历史、缓存与插件系统能力。
需要账号系统的互动和审核能力不在公开客户端实现。
