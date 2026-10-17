# DeepCook

Kotlin 原生 Android 菜谱应用：暖黄 Material 3 主题，112 道菜谱按 7 个地域与菜式分类，离线找菜、组合搜索、收藏、完整菜谱、分步阅读、我的厨房与本地设置；还可以在「我的」里写下自己的菜谱，联网时按菜名自动补全材料与步骤。

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

本机工程位于含中文的路径（`D:\项目\deepcook`），AGP 默认拒绝这种路径；`gradle.properties` 已设置 `android.overridePathCheck=true`，可直接构建 Debug 与 Preview 包。同一路径下 `:app:testDebugUnitTest` 的测试进程在 Windows 上无法启动（路径编码问题，与代码无关）；需要跑 JVM 测试时，把工程复制到纯 ASCII 路径（例如 `D:\deepcook-build`）再执行，43 项测试即可通过。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。最低系统 Android 8.0（API 26），目标与编译 SDK 36。

日常体验与性能检查使用 `app/build/outputs/apk/preview/app-preview.apk`：关闭调试、开启 R8 与资源缩减，使用本机 Debug 密钥签名，方便直接安装覆盖开发版。正式 `release` 的发布签名另行配置。Compose 与 AndroidX 附带的基线配置会合并进入优化构建；Debug 构建保留调试工具，仅用于开发与自动测试。

使用 AGP 9.3 / Gradle 9.5、内置 Kotlin 和 Kotlin 2.4.10 Compose/Serialization 插件。
Compose BOM 2025.12.01、Lifecycle 2.10、Coil 3.3 保持与 SDK 36 兼容；较新版本要求 SDK 37，因此本阶段未升级。

## 页面与行为

- 找菜：112 道菜谱（中式 52、日式 15、韩式 12、美式 10、欧式 10、东南亚 8、其他 5），6 道精选、分类、双列自适应网格。
- 地域：每道菜带地域标签。卡片封面左上角显示地域徽标，列表与详情在用时、难度之后显示地域；首页分类 chip 与搜索页分类下拉都把 7 个地域列在菜式分类之前，选中即按地域筛选。
- 首页卡片保留圆角；菜名 14sp 常规字重。用时与难度以 12sp 白字放在封面底部渐变上，用时省略“分钟”，难度采用 1–5 分制。
- 卡片收藏图标默认 16dp，随字体缩放，并做向右的视觉对齐；点击区域保持 48dp。
- 首页顶部仅保留 DeepCook 文字，右上角搜索；搜索页使用 52dp 紧凑输入框。
- 搜索：菜名、材料关键词与地域名，250ms 防抖，多词全部匹配，分类（含地域）/用时/难度联合筛选。
- 详情：封面、原始份量、完整内容、吸顶分段定位、来源；材料采用紧凑对齐列表。
- 分步阅读：指定步骤、目录、翻步、重建恢复；返回详情定位阅读步骤。
- 收藏：持久化、按时间倒序，取消后可以撤销。
- 我的：收藏摘要、最近浏览、我的菜谱、设置；浏览记录按菜谱去重，最多 50 条。
- 我的菜谱：自己加菜。填写菜名后联网（HowToCook，Unlicense）自动补全简介、材料与用量、步骤、注意事项、用时与难度，所有字段可改；封面可从相册选择并裁成 4:3。自建菜谱与内置菜谱一起进入首页与搜索，卡片带「我的」标记，可收藏、可编辑、可删除。无网络时退回纯手动填写，功能不受影响。详见 `docs/我的菜谱.md`。
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

用户自建的菜谱存在另一个 DataStore（`user_recipes.json`），运行期与内置目录合并成同一个列表；
封面复制到应用私有目录。联网补全由 `data/lookup` 承担：随包的菜名索引做离线匹配，匹配到才去
raw.githubusercontent.com 取那一页正文并解析，失败、无网、未匹配都只是回到手动填写。

## 示例内容与素材

112 道自编演示菜谱未经独立试做验证，参考公开菜谱资料整理。配图共 38 道：最初 12 道与 4 道扩充菜谱使用 HowToCook 固定版本照片（Unlicense），另外 22 道扩充菜谱使用 FoodiesFeed 的 CC0 照片；其余 74 道没有找到授权明确且确实对应该菜品的照片，界面显示占位图。
内容格式为第 3 版：新增地域字段（中式/日式/韩式/美式/欧式/东南亚/其他）与“无”占位约定——应用要展示而菜谱没有提供的内容统一写“无”，界面隐藏该项，不渲染单独的“无”。
菜谱难度以可空整数 1–5 保存，分数越高表示操作越复杂；原演示标签按简单→1、中等→3、较难→5 转换，未填写的仍为空。搜索筛选使用同一分值。
所有内容随 APK 提供；除「我的菜谱」的联网补全外，运行时不需要网络。补全只读取 HowToCook 固定 commit 的公开文本（Unlicense），不上传任何数据；该来源与最初 12 张配图同源。详细声明见 `THIRD_PARTY_NOTICES.md`。

`tools/prepare_content.py` 是内容维护工具，使用 Python + Pillow；它只重建最初 12 道菜谱，运行会覆盖 `recipes.json` 中的扩充内容，因此要求先设置 `DEEPCOOK_REBUILD_CONTENT=1`。
`tools/validate_content.py` 只读校验随包内容：ID 唯一、地域与分类白名单、步骤与材料引用、封面文件、地域分布与精选数量，以及 `howtocook_index.json` 的来源、许可、固定 commit、路径与菜名一致性。
`tools/build_howtocook_index.py` 重新生成联网补全用的菜名索引（默认钉在当前 master 的 commit）。
普通构建不运行这些工具，也不需要 Python。

## 测试

JVM 单元测试覆盖搜索组合、地域与菜式分开筛选、地域名搜索、“无”占位隐藏、缺失元数据、收藏顺序、浏览去重/上限、序列化和损坏数据；以及联网补全的 Markdown 解析、菜名匹配与同义词归一、草稿校验与保存转换、用户菜谱的原地替换/删除/损坏文件处理（共 43 项）。
Android 测试覆盖真实 DataStore 写入/重新打开/独立清理，以及搜索、阅读翻步和恢复、收藏撤销、主题与字号。
同时验证搜索框尺寸、平级转场中导航栏保持原位、多级转场方向、搜索条件恢复、空历史页的返回导航，以及地域徽标与分类下拉的地域筛选。
`tools/measure_navigation.py --apk <APK> --label <名称>` 可在专用模拟器重复导航并保存 Android `gfxinfo` 帧耗时报告；脚本会清理该模拟器中的 DeepCook 测试数据，拒绝物理设备。
真实文件持久化放在 Android 测试中，以使用 Android 文件系统的替换语义。
