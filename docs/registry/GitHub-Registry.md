# TinaIDE GitHub Registry

> 更新日期：2026-05-22

TinaIDE 开源版的插件市场与依赖包市场不再从 TinaServer 读取索引。
客户端默认读取公开仓库：

```text
https://github.com/wuxianggujun/TinaIDE-Registry
```

客户端内置两个索引入口，按顺序自动尝试：

```text
https://cdn.jsdelivr.net/gh/wuxianggujun/TinaIDE-Registry@main
https://raw.githubusercontent.com/wuxianggujun/TinaIDE-Registry/main
```

国内网络优先走 jsDelivr CDN；如果 CDN 不可用，再回退到 GitHub Raw。
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
plugins/index.json
plugins/<plugin-id>/<version>/<plugin-id>.tinaplug
packages/index.json
packages/<package-id>/<version>/<file>.tar.xz
```

`download_url` 和 `download_sources[].url` 支持两种写法：

- 绝对 URL：客户端原样访问。
- 相对路径：客户端会拼到本次成功加载索引的 Registry base 后面。
  默认通常是 jsDelivr CDN，失败后才会是 GitHub Raw。

国内网络建议：

- 小文件、索引、示例插件可以继续使用相对路径，客户端会优先走 CDN。
- 大文件、依赖包、运行时包建议填写你自己的 CDN/对象存储绝对 URL。
- 不要把未校验的大文件只放在随机公开代理上；能填写 `sha256:` 时必须填写。

## 插件索引

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
        "install_type": "download",
        "size": 1234,
        "download_url": "packages/sdl3/3.2.0/sdl3.tar.xz",
        "checksum": "sha256:<sha256>",
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
          "install_type": "download",
          "download_size": 1234,
          "download_url": "packages/sdl3/3.2.0/sdl3.tar.xz",
          "checksum": "sha256:<sha256>",
          "is_latest": true
        }
      ]
    }
  }
}
```

客户端会继续保留本地安装状态、下载历史、缓存与插件系统能力。
评论、评分、举报等需要账号系统的互动能力在开源版不可用。
