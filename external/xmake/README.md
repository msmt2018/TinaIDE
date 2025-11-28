# External Xmake Overlay

该目录用于存放我们在 TinaIDE 中对 xmake/tbox 的所有定制源码。运行
`docker/llvm-build/build-xmake.ps1` 时，脚本会将本目录镜像到
`docker/llvm-build/dev-work/overlays/xmake`，随后在容器里覆盖
`/work/src/xmake`，这样 upstream 仓库在每次重新 clone 后也能自动带上这些修改。

使用约定：

- 目录结构与 xmake upstream 保持一致，只放需要替换/新增的文件。
- 若 upstream 更新导致冲突，只需在这里手动 rebase，对应的构建脚本无需改动。
- 当前主要包含 Android 进程 hook（`core/src/tbox/.../platform/android|posix/process.*`）。

要新增自定义代码，只需在 `external/xmake` 下创建对应路径并提交即可。
