# 插件市场排障

> 当前状态：公开仓库只保留 Android 客户端侧插件能力；插件市场索引从 GitHub Registry 读取。

公开 Registry 仓库地址：

```text
https://github.com/wuxianggujun/TinaIDE-Registry
```

后端容器、数据库、管理后台、部署脚本和生产运维排障资料已经迁入私有仓库，
不再随开源 Android 项目分发。

客户端侧排查时优先确认：

1. 网络请求是否能访问 `wuxianggujun/TinaIDE-Registry` 的 `plugins/index.v2.json`。
   当前 Android 主干只读取 v2 轻量索引；v2 不存在、请求失败或解析失败时会直接报错，
   不再回退 `plugins/index.json`。索引入口会先走 GitHub Raw，再回退到 jsDelivr CDN。
   如果 v2 索引中 `plugins` 是 `[]`，插件市场为空是 Registry 还没有发布插件，
   不是客户端代码丢失。
2. 插件包的 `download_url` 是否能通过当前 Registry 入口或绝对下载地址访问。
   国内网络建议把大文件放到可信 CDN、对象存储或自建代理，再在索引中填写绝对 URL。
3. Registry 仓库是否已经运行 `scripts/build-registry.ps1` 生成最新 v2 轻量索引、
   单项详情文件、`.tinaplug` 和依赖包校验值；v1 兼容索引默认不再生成，只服务旧客户端。
4. 如果 GitHub Raw 已经有内容但 jsDelivr 仍为空，先 purge：
   `https://purge.jsdelivr.net/gh/wuxianggujun/TinaIDE-Registry@main/plugins/index.v2.json`。
5. 本地插件缓存、下载历史和安装目录是否可读写。
6. 开源版账号登录、第三方登录、激活码、会员和官方 AI 额度入口均为移除状态，
   不应再按旧商业版链路排查。

依赖包索引同样只读取 GitHub Registry 的 `packages/index.v2.json`，不会回退旧
`packages/index.json`。Registry 结构见
[`docs/registry/GitHub-Registry.md`](../registry/GitHub-Registry.md)。

如果问题需要查看服务端日志、数据库、管理后台或部署配置，请在私有后端仓库
中处理。
