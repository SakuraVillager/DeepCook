# DeepCook

Kotlin 原生 Android 菜谱应用：暖黄 Material 3 主题，离线找菜、组合搜索、收藏、完整菜谱、分步阅读、我的厨房与本地设置。

## 构建与运行

- Android SDK Platform 36、Build Tools、JDK 21。
- 将 Android SDK 路径配置在本机 `local.properties` 的 `sdk.dir`，或设置 `ANDROID_HOME`。
- 设置 `JAVA_HOME` 指向 JDK 21。项目使用 Gradle Wrapper，无需全局安装 Gradle。

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assemblePreview
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

最后一项需要授权的 Android 设备或已启动的模拟器。多个设备同时连接时，可设置 `ANDROID_SERIAL`。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。最低系统 Android 8.0（API 26），目标与编译 SDK 36。

日常体验与性能检查使用 `app/build/outputs/apk/preview/app-preview.apk`：关闭调试、开启 R8 与资源缩减，使用本机 Debug 密钥签名，方便直接安装覆盖开发版。正式 `release` 的发布签名另行配置。Compose 与 AndroidX 附带的基线配置会合并进入优化构建；Debug 构建保留调试工具，仅用于开发与自动测试。

使用 AGP 9.3 / Gradle 9.5、内置 Kotlin 和 Kotlin 2.4.10 Compose/Serialization 插件。
Compose BOM 2025.12.01、Lifecycle 2.10、Coil 3.3 保持与 SDK 36 兼容；较新版本要求 SDK 37，因此本阶段未升级。

## 页面与行为

- 找菜：6 道精选、分类、双列自适应网格。
- 首页卡片保留圆角；菜名 14sp 常规字重。用时与难度以 12sp 白字放在封面底部渐变上，用时省略“分钟”，难度采用 1–5 分制。
- 卡片收藏图标默认 16dp，随字体缩放，并做向右的视觉对齐；点击区域保持 48dp。
- 首页顶部仅保留 DeepCook 文字，右上角搜索；搜索页使用 52dp 紧凑输入框。
- 搜索：菜名与材料关键词，250ms 防抖，多词全部匹配，分类/用时/难度联合筛选。
- 详情：封面、原始份量、完整内容、吸顶分段定位、来源；材料采用紧凑对齐列表。
- 分步阅读：指定步骤、目录、翻步、重建恢复；返回详情定位阅读步骤。
- 收藏：持久化、按时间倒序，取消后可以撤销。
- 我的：收藏摘要、最近浏览、设置；浏览记录按菜谱去重，最多 50 条。
- 设置：明暗模式、动态色、字号、步骤图片、前台阅读保持亮屏、分项数据清理、来源与许可。
- 宽度达到 600dp 使用 NavigationRail；字号继续尊重系统字体缩放。
- 导航沿用 Mihon 的 Material Motion Compose 2.0.1：平级标签 200ms Fade Through（无缩放），多级页面 300ms / 30dp Shared Axis X，返回反向；主导航栏只随多级页面一起转场。
- 转场完成后才记录最近浏览；连续点击同一道菜不会重复压入详情页。首页采用紧凑间距，详情保留原有连续正文与步骤布局，分步阅读固定进度与翻步操作。

翻步只代表查看位置。本版没有做菜会话、AI、语音、计时器、账号、同步或配方调整。

## 工程结构

`app` 负责导航与装配，`core/model` 定义内容和偏好，`core/designsystem` 提供主题及复用组件，
`data` 定义 Repository 与本地实现，`feature` 按浏览、搜索、详情和设置组织页面。

菜谱从 assets JSON 读取，图片由 Coil 读取本地 assets。收藏、最近浏览和设置由单一 DataStore 保存，
经 Repository 与 StateFlow 驱动页面。文件损坏会报告错误并保留原文件，不静默清空数据。

## 示例内容与素材

12 道自编演示菜谱未经独立试做验证。12 张图片来自 HowToCook 的固定版本，附来源和许可。
菜谱难度以可空整数 1–5 保存，分数越高表示操作越复杂；原演示标签按简单→1、中等→3、较难→5 转换，未填写的仍为空。搜索筛选使用同一分值。
所有内容随 APK 提供，运行时不需要网络。详细声明见 `THIRD_PARTY_NOTICES.md`。

`tools/prepare_content.py` 是内容维护工具，使用 Python + Pillow；运行它会有意更新示例 JSON 和图片。
普通构建不运行该工具，也不需要 Python。

## 测试

JVM 单元测试覆盖搜索组合、缺失元数据、收藏顺序、浏览去重/上限、序列化和损坏数据。
Android 测试覆盖真实 DataStore 写入/重新打开/独立清理，以及搜索、阅读翻步和恢复、收藏撤销、主题与字号。
同时验证搜索框尺寸、平级转场中导航栏保持原位、多级转场方向、搜索条件恢复和空历史页的返回导航。
`tools/measure_navigation.py --apk <APK> --label <名称>` 可在专用模拟器重复导航并保存 Android `gfxinfo` 帧耗时报告；脚本会清理该模拟器中的 DeepCook 测试数据，拒绝物理设备。
真实文件持久化放在 Android 测试中，以使用 Android 文件系统的替换语义。
