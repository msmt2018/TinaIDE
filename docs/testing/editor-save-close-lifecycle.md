# 保存/关闭标签时的编辑器生命周期回归

## 崩溃依据

报告版本：`0.18.35 (code 18362, build 0aaf9d52e9-20261005180501)`。
使用对应的 `app/mappings/<version-build>/arm64Release/mapping.txt` 还原主线程调用栈：

```text
SnapshotStateObserver.recordInvalidation
→ DerivedSnapshotState.currentRecord
→ EditorState.visibleLines
→ EditorVisualLineMapper.visualLineMap
→ RopeTextBuffer.getLineCount
→ TextBufferClosedException: RopeTextBuffer is already closed
```

崩溃前还有 `EditorTabManager.updateTabState: tab not found`，与标签退出后的残留观察相符。
LSP `semanticTokens/range: method not found` 不在这条致命异常调用链中。

## 所有权与释放顺序

- 缓存拥有 `CodeEditorRuntime`，`TinaCodeEditorPage` 每个挂载页面再持有一个 UI 引用。
- `CodeEditorRuntimeEffect` 在页面其他 effects 之前注册；Compose 退出时后注册的 effects 先清理。
- 标签删除、缓存淘汰、工作区释放只请求 dispose。仍有页面（包括镜像分屏）时，buffer 和语言服务继续保留。
- 最后一个页面退出后，运行时移除 bindings、语言服务和监听器，最后关闭 buffer；重复 dispose 不重复释放。
- 普通切换标签只释放页面引用，缓存中的运行时继续保留，直到缓存真正请求 dispose。
- 编辑器内核对关闭后的可见行观察返回空范围，并覆盖派生状态在全局 Snapshot 通知中的重算。
  原始 buffer 的关闭后读写仍抛异常；无关错误仍然上抛。

这次修改复用了现有 `DisposableEffect`、binding 引用计数模式和 `TextBufferClosedException`，
没有增加依赖、用户可见文案或宿主到编辑器内核的反向依赖。代码、测试和文档采用 UTF-8 无 BOM。

## 自动化验证

先在 `editor-kit` 内使用独立 wrapper：

```bash
./gradlew :core:editor-view:testDebugUnitTest --tests '*EditorClosedSnapshotTest' --tests '*EditorDrawFrameTest' --tests '*EditorCanvasClosedBufferTest' --tests '*EditorRuntimeEffectsTest' --tests '*EditorScaleGestureLifecycleTest' --tests '*EditorScaleTransformCoordinatorTest' --no-daemon --console=plain
```

再在 TinaIDE 根目录验证真实宿主运行时与文件保存：

```bash
./gradlew :app:testArm64DebugUnitTest --tests '*CodeEditorRuntimeTest' --tests '*CodeEditorRuntimeLifecycleTest' --tests '*EditorFontSizeHostIntegrationTest' --no-daemon --console=plain
```

`CodeEditorRuntimeLifecycleTest` 使用真实 `DocumentSession.save(CLOSE)` 写入带中文的 UTF-8 文件，
保留真实 TinaEditor 与可见行 snapshotFlow，先请求释放，再触发快照更新，最后移除页面。
断言保存内容不变、子 effects 清理时 buffer 仍打开、退出后 buffer 已关闭。

设备端在 `editor-kit` 运行（用 `ANDROID_SERIAL` 指定空闲模拟器）：

```bash
./gradlew :core:editor-view:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wuxianggujun.tinaide.core.editorview.EditorLifecycleInstrumentationTest --no-daemon --console=plain
```

设备测试覆盖相同全局快照失效时序及关闭软换行时双指缩放松手后的文档锚点、滚动偏移与随后单指滚动。

### 真实触摸回归补充

Foundation 1.11.2 的 Main pass 按 modifier 从内向外传递；原先内层的轴向 scrollable
可能先于 transformable 消费双指移动，依赖重组更新 `enabled` 不足以避开这个窗口。
将 transformable 放在两个 scrollable 后面，并指定 `canPan = { false }`：
缩放优先认领双指移动，单指拖动仍由 scrollable 处理，不另建一套手势检测器。

宿主测试保留真实 `Prefs.editorSettingsFlow` 回写，验证小数字号不被取整、回写不改变已提交的视口。
Robolectric 显式绘制宿主 View 后再注入触摸，避免尚未生成真实 viewport metrics 就开始手势。
设备测试在缩放结束的 100 ms 保护窗口后，从正文区域起滑；`swipeUp()` 默认从底边开始，
关闭软换行时可能命中横向滚动条，不能用这种输入验证正文的纵向滚动。

### 本轮结果（2026-10-12）

| 验证范围 | 结果 |
| --- | --- |
| kit 关闭快照、残留帧、RuntimeEffects | 13 项通过 |
| kit 缩放生命周期与坐标提交 | 13 项通过 |
| 宿主运行时、真实保存/关闭、字号偏好回写 | 7 项通过 |
| Android 14 / API 34 模拟器真实触摸与快照 | 2 项通过，连续运行 3 轮均通过 |

共 35 个不同用例通过，0 失败、0 跳过；不把重复运行或历史结果累加为新用例。
kit 独立 Kotlin/测试 APK 编译及宿主 `compileArm64DebugKotlin`、测试源码编译均通过。
一次后台回归因包含 Gradle 启动、资源准备和测试的 60 秒总限时结束，随后以前台有界命令重跑通过，
超时尝试不计作通过。未运行 Release 构建，也未在报告中的 Redmi Note 12 5G 真机回测。

本地验证日志：`.tmp/editor-save-final-kit-tests.log`、`.tmp/editor-save-host-tests.log`、
`.tmp/editor-save-device-tests.log`、`.tmp/editor-save-device-tests-2.log`、`.tmp/editor-save-device-tests-3.log`。

## 手工回归

1. 修改 C++ 文件，执行保存，然后关闭标签；重新打开，核对最新内容和 UTF-8 中文。
2. 在关闭未保存标签的确认框选择保存；连续重复，并穿插切后台/返回。
3. 同一文件在两个分屏页面中打开，关闭一个页面后继续编辑另一个。
4. 保存、关闭标签、切换工程时检查主线程日志，没有关闭 buffer 访问异常。
5. 关闭软换行，滚动到长文件中部，双指缩放后松手，检查焦点下的文本不回跳。
6. 缩放结束后从正文中部单指上下滑动，确认没有被缩放手势持续占用；横向滚动条仍单独拖动。
