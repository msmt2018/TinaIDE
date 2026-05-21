上游来源：

- `termux/termux-core-package`
- `termux/termux-exec-package`

当前目录仅 vendoring 运行时所需的最小源码子集，并在 TinaIDE 内部重新做了：

- 模块拆分
- Gradle / CMake 接入
- 环境变量前缀本地化（`TINA_*`）
- preload 入口文件重命名

许可证请查看同目录下的 [UPSTREAM_LICENSE.txt](/Users/wuxianggujun/CodeSpace/AndroidStudioProjects/TinaIDE/external/tina-exec/UPSTREAM_LICENSE.txt)。
