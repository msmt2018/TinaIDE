# 游戏引擎插件 GUI 运行方案

本文记录 TinaIDE 当前插件接口对“游戏引擎插件”的支持现状，以及如何实现“安装插件 → 创建项目 → 点击运行 → 打开 GUI 画面”的可行路线。

## 1. 背景目标

目标体验：

```text
用户安装游戏引擎插件
  ↓
新建项目中出现该游戏引擎模板
  ↓
模板生成可构建项目
  ↓
点击运行
  ↓
TinaIDE 打开 GUI 运行画面
```

需要注意：Android 里的依赖库不能像桌面程序一样“自己弹出窗口”。宿主 App 必须提供 `Activity`、`View`、`SurfaceView`、`SDLActivity` 或其他 GUI 容器。插件的职责更适合定义为：

- 提供项目模板。
- 提供构建配置。
- 提供运行配置或运行约定。
- 可选提供 APK 导出模板。

## 2. 当前已经支持的能力

### 2.1 插件项目模板

当前插件清单已经支持 `contributions.projectTemplates`。

对应模型：

- `core/plugin/src/main/java/com/wuxianggujun/tinaide/plugin/PluginModels.kt`
- `PluginManifest.contributions`
- `PluginContributions.projectTemplates`
- `PluginProjectTemplate`

插件模板会由 `PluginManager.listProjectTemplateOptions()` 汇总，并转换为 `ProjectTemplateSpec.Zip`。

对应逻辑：

- `core/plugin/src/main/java/com/wuxianggujun/tinaide/plugin/PluginManager.kt`
- `listProjectTemplateOptions()`
- `resolveProjectTemplateOption()`

这意味着：游戏引擎插件可以通过 `manifest.json` 声明一个模板 ZIP，让新建项目向导显示该模板。

示例：

```json
{
  "id": "friend.game.engine.starter",
  "name": "Friend Game Engine Starter",
  "version": "1.0.0",
  "type": "config",
  "contributions": {
    "projectTemplates": [
      {
        "id": "sdl3-empty-game",
        "name": "Friend Engine SDL3 Game",
        "description": "Create a GUI game project powered by Friend Engine.",
        "templatePath": "templates/friend-engine-sdl3.zip",
        "buildSystem": "cmake",
        "primaryLanguage": "CPP"
      }
    ]
  }
}
```

### 2.2 模板 ZIP 创建项目

当前 `ProjectTemplateInstaller` 支持从插件 ZIP 解压项目。

对应文件：

- `core/project/src/main/java/com/wuxianggujun/tinaide/project/ProjectTemplateInstaller.kt`
- `core/project/src/main/java/com/wuxianggujun/tinaide/project/ProjectCreationService.kt`

模板支持文本占位符替换：

- `{{PROJECT_NAME}}`
- `{{PROJECT_NAME_UPPER}}`
- `{{CPP_STANDARD}}`
- `{{CPP_STANDARD_FLAG}}`
- `{{NDK_API_LEVEL}}`

因此插件可以把完整项目骨架放进 ZIP：

```text
friend-engine-sdl3.zip
├── CMakeLists.txt
├── src/main.cpp
├── assets/
├── .tinaide/project.json
└── .tinaide/run_configs.json
```

### 2.3 新建项目向导已接入插件模板

新建项目向导会合并内置模板与插件模板。

对应文件：

- `feature/wizard/src/main/java/com/wuxianggujun/tinaide/ui/wizard/NewProjectWizardActivity.kt`
- `feature/wizard/src/main/java/com/wuxianggujun/tinaide/ui/wizard/NewProjectWizardViewModel.kt`
- `feature/wizard/src/main/java/com/wuxianggujun/tinaide/ui/wizard/NewProjectWizardSupport.kt`

当前已有“偏向插件模板”的入口：

- `NewProjectWizardActivity.createPluginProjectIntent()`

这说明插件模板已经不是纯数据，已经能进入实际创建项目流程。

### 2.4 GUI 输出模式

运行配置已经支持 `OutputMode.GUI`。

对应文件：

- `core/compile/src/main/java/com/wuxianggujun/tinaide/core/compile/RunConfiguration.kt`

关键字段：

- `outputMode`
- `guiOrientation`
- `enableFloatingLog`

当项目元数据识别为 SDL3 项目时，默认运行模式会倾向 GUI：

```text
ProjectApkExportType.SDL3 → OutputMode.GUI
```

### 2.5 GUI 宿主 Activity

当前已有两类 GUI 运行宿主：

1. SDL 运行宿主：

```text
ExternalSdlActivity
```

用于运行依赖 SDL2/SDL3 的 `.so` 产物。

对应文件：

- `app/src/main/java/com/wuxianggujun/tinaide/ui/sdl/ExternalSdlActivity.kt`
- `app/src/main/java/com/wuxianggujun/tinaide/ui/sdl/SdlRuntimeResolver.kt`

2. 通用 GUI 宿主：

```text
GuiHostActivity
```

用于加载普通 `.so`，要求用户库暴露 TinaIDE 约定的渲染符号。

对应文件：

- `app/src/main/java/com/wuxianggujun/tinaide/ui/gui/GuiHostActivity.kt`
- `app/src/main/java/com/wuxianggujun/tinaide/ui/gui/GuiRuntimeBridge.kt`
- `app/src/main/cpp/gui/gui_runtime_jni.cpp`

通用 GUI `.so` 至少需要导出：

```cpp
extern "C" int tina_gui_render_argb32(
    int width,
    int height,
    uint32_t* pixels,
    int stride
);
```

可选导出：

```cpp
extern "C" int tina_gui_init();
extern "C" void tina_gui_shutdown();
extern "C" void tina_gui_on_touch(int action, float x, float y, int pointer_id);
extern "C" void tina_gui_on_key(int keycode, int action);
```

## 3. 当前不支持或不完整的能力

### 3.1 没有独立 DependencyProvider

当前插件清单没有专门字段表达：

```json
{
  "dependencies": [
    "com.friend:game-engine-android:1.0.0"
  ]
}
```

因此目前依赖只能通过模板文件写死，例如：

- `CMakeLists.txt`
- `Makefile`
- `.tinaide/project.json`
- 其他项目内配置文件

如果后续要支持 Android AAR 游戏引擎，应新增插件级依赖声明能力。

### 3.2 没有独立 RunProfileProvider

当前插件不能在 `manifest.json` 中声明默认运行方式，例如：

```json
{
  "runProfiles": [
    {
      "id": "gui",
      "outputMode": "GUI",
      "targetName": "main",
      "orientation": "LANDSCAPE"
    }
  ]
}
```

目前推荐做法是让模板内置 `.tinaide/run_configs.json`，或者依赖项目识别逻辑自动设置 GUI 模式。

### 3.3 没有 Android Gradle App 构建系统

当前项目构建系统枚举主要是：

- `SINGLE_FILE`
- `CMAKE`
- `MAKE`
- `PLUGIN`
- `UNKNOWN`

对应文件：

- `core/model/src/main/java/com/wuxianggujun/tinaide/project/ProjectMetadata.kt`

还没有类似：

```text
ANDROID_GRADLE
```

所以如果好友引擎以 Android AAR 形式发布，例如：

```kotlin
implementation("com.friend:game-engine-android:1.0.0")
```

TinaIDE 当前还不能完整承担“生成 Gradle Android App → 构建 APK → 安装 → 启动 Activity”的全链路。

### 3.4 没有动态 Activity/View 插件入口

当前插件不能直接声明：

```json
{
  "entryActivity": "com.friend.engine.GameActivity"
}
```

也不能动态把插件 AAR 里的 `Activity` 注册到宿主 `AndroidManifest.xml`。

这是 Android 平台限制和安全模型共同导致的。第一版不建议走动态 Activity 插件路线。

## 4. 推荐落地路线

### 4.1 第一版：CMake / SDL3 模板插件

这是当前架构最容易落地的方案。

插件包结构：

```text
friend-engine-plugin.tinaplug
├── manifest.json
└── templates/
    └── friend-engine-sdl3.zip
```

模板生成项目：

```text
FriendGame/
├── CMakeLists.txt
├── src/
│   └── main.cpp
├── assets/
├── .tinaide/
│   ├── project.json
│   └── run_configs.json
└── README.md
```

运行链路：

```text
插件贡献模板
  ↓
新建项目向导选择模板
  ↓
ProjectCreationService 解压模板
  ↓
CompileProjectUseCase 构建共享库
  ↓
OutputMode.GUI
  ↓
CompileUiEventObserver 打开 GUI 宿主
  ↓
ExternalSdlActivity / GuiHostActivity 显示画面
```

适合场景：

- 好友游戏引擎有 C/C++ 核心。
- 好友游戏引擎支持 SDL2/SDL3。
- 项目输出 `libmain.so` 或可被 TinaIDE GUI 宿主加载的共享库。

### 4.2 第二版：通用 GUI `.so` 引擎适配

如果不走 SDL，可以让引擎导出 TinaIDE 约定符号。

最小接口：

```cpp
extern "C" int tina_gui_render_argb32(
    int width,
    int height,
    uint32_t* pixels,
    int stride
) {
    // 写入 ARGB32 像素数据
    return 0;
}
```

这种方案能被 `GuiHostActivity` 直接加载。

优点：

- 不依赖 SDL。
- TinaIDE 可完全控制外层 UI。
- 更适合轻量图形程序、教学示例、调试预览。

缺点：

- 引擎必须适配 TinaIDE 渲染 ABI。
- 性能和输入模型需要继续优化。
- 复杂游戏引擎仍更适合 SDL / NativeActivity / 独立 APK。

### 4.3 第三版：Android AAR 游戏引擎项目

如果好友引擎发布为 Android AAR，推荐新增能力：

1. 新增 `ProjectBuildSystem.ANDROID_GRADLE`。
2. 新增 Android Gradle 项目模板。
3. 新增 Android APK 构建器。
4. 新增安装 APK 能力。
5. 新增启动 Activity 能力。
6. 插件 manifest 支持依赖与运行入口声明。

目标 manifest 可以演进为：

```json
{
  "id": "friend.game.engine.android",
  "name": "Friend Engine Android",
  "version": "1.0.0",
  "type": "config",
  "contributions": {
    "projectTemplates": [
      {
        "id": "android-empty-game",
        "name": "Friend Engine Android Game",
        "description": "Create an Android APK project powered by Friend Engine.",
        "templatePath": "templates/friend-engine-android.zip",
        "buildSystem": "android_gradle",
        "primaryLanguage": "KOTLIN"
      }
    ],
    "androidDependencies": [
      {
        "group": "com.friend",
        "name": "game-engine-android",
        "version": "1.0.0"
      }
    ],
    "runProfiles": [
      {
        "id": "android-app",
        "module": "app",
        "activity": ".MainActivity"
      }
    ]
  }
}
```

注意：上面的 `androidDependencies` 和 `runProfiles` 是建议扩展字段，当前代码尚未支持。

## 5. 当前 MVP 模板建议

如果现在就要做好友游戏引擎插件，建议先做 SDL3/CMake 版。

### 5.1 manifest.json

```json
{
  "id": "friend.engine.starters",
  "name": "Friend Engine Starters",
  "version": "1.0.0",
  "type": "config",
  "description": "Project templates for Friend Engine GUI games.",
  "author": {
    "name": "Friend Engine Team"
  },
  "contributions": {
    "projectTemplates": [
      {
        "id": "sdl3-empty-game",
        "name": "Friend Engine SDL3 Game",
        "description": "A minimal SDL3 GUI game project.",
        "templatePath": "templates/friend-engine-sdl3.zip",
        "buildSystem": "cmake",
        "primaryLanguage": "CPP"
      }
    ]
  }
}
```

### 5.2 模板项目关键点

模板项目要满足：

- `CMakeLists.txt` 能构建共享库。
- 产物最好命名为 `libmain.so`。
- 代码或构建脚本包含 SDL3 标记，便于 TinaIDE 自动识别。
- `.tinaide/project.json` 可显式写入 `apkExportType: "SDL3"`。
- `.tinaide/run_configs.json` 可显式写入 `outputMode: "GUI"`。

推荐 `.tinaide/project.json`：

```json
{
  "schemaVersion": 2,
  "id": "{{PROJECT_NAME}}",
  "displayName": "{{PROJECT_NAME}}",
  "createdAt": 0,
  "buildSystem": "CMAKE",
  "cppStandard": "CPP_17",
  "primaryLanguage": "CPP",
  "apkExportType": "SDL3"
}
```

推荐 `.tinaide/run_configs.json`：

```json
{
  "schemaVersion": 2,
  "configurations": [
    {
      "id": "debug-gui",
      "name": "Debug GUI",
      "args": "",
      "workDir": "",
      "buildType": "DEBUG",
      "outputMode": "GUI",
      "targetName": "main",
      "sourceFileMode": "AUTO",
      "sourceFilePath": "",
      "compilerType": "CLANG",
      "guiOrientation": "LANDSCAPE",
      "enableFloatingLog": true
    }
  ],
  "selectedId": "debug-gui"
}
```

## 6. 结论

当前 TinaIDE 已具备游戏引擎插件 MVP 的关键基础：

- 插件可以贡献项目模板。
- 模板可以生成项目。
- 项目可以进入现有构建链路。
- `OutputMode.GUI` 可以打开 GUI 宿主。
- SDL3 / 普通 `.so` 已有运行入口。

当前最推荐路线：

```text
游戏引擎插件 = 项目模板插件 + CMake/SDL3 项目骨架 + GUI 运行配置
```

暂不建议第一版实现：

```text
动态加载 AAR → 动态注册 Activity → 直接运行插件 Activity
```

如果后续目标是 Android AAR 游戏引擎生态，需要补齐 Android Gradle 构建系统、依赖声明、APK 安装和 Activity 启动链路。
