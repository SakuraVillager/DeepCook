# DeepCook

Kotlin 原生 Android 菜谱应用：暖黄 Material 3 主题，离线找菜、组合搜索、收藏、分步阅读、个人菜谱编辑、资料补全与本地设置。

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
- 搜索：菜名、材料、原名别名和风味标签，250ms 防抖，多词全部匹配，分类/用时/难度联合筛选。
- 详情：封面、原始份量、完整内容、吸顶分段定位、来源；材料采用紧凑对齐列表。
- 分步阅读：指定步骤、目录、翻步、重建恢复；返回详情定位阅读步骤。
- 收藏：持久化、按时间倒序，取消后可以撤销。
- 我的：收藏摘要、最近浏览、设置；浏览记录按菜谱去重，最多 50 条。
- 我的菜谱：自建、编辑、删除和相册封面；补全优先复用本地资料，不覆盖已填写字段。保存失败保留草稿，取消编辑不删除原封面。
- 编辑草稿通过 SavedStateHandle 恢复；只有持久化成功后退出，图片导入完成前不能保存。个人菜谱 ID、材料与步骤 ID 在编辑时保留。
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

## 菜谱与素材

0.3.0 合并后随包收录 494 道菜谱：HowToCook 中文原文整理 372 道、Based Cooking 国际菜中文译文 22 道、云端分支的项目自编示例 100 道。219 道有来源配图，其余使用统一占位。国际菜的名称、材料、步骤和注意事项均为中文，保留原文名称供搜索。
两个分支的菜谱 ID 均保留；同名菜的不同来源版本可以并存，以详情来源说明区分。云端原有收藏与本地原有收藏都能继续找到对应内容。项目示例不是来源译文，详情中单独标识；其总用时和难度未核实，显示“无”。

难度以可空整数 1–5 保存，取原文明确标注的星级；总用时只取明确的整道菜参考用时，国际菜会检查腌制、静置与字段冲突。未提供或无法可靠确定的用时、难度、份量、材料用量显示“无”，不根据步骤数猜测。原文给出的“适量”等说明予以保留。
全部内置内容随 APK 提供。浏览、收藏、自建及已有菜谱补全均可离线使用；本地没有匹配资料时，才尝试从固定版本的 HowToCook 联网获取，不上传用户资料。地域只使用明确提供的值，未知为“无”。来源固定版本、路径及校验值记录在 `app/src/main/assets/catalog_sources.json`，设置中可查看；授权声明见 `THIRD_PARTY_NOTICES.md`。

内容维护使用 Python + Pillow：`python tools/import_catalog.py fetch` 下载固定版本源文件，`python tools/import_catalog.py build` 根据原文与 `tools/world_recipes_zh.json` 重建内容和配图。首次下载需要网络，缓存存入忽略的 artifacts 目录。`tools/write_world_translations.py` 可重建已校对的中文翻译输入。
`tools/cloud_recipes.json` 保存云端 100 道示例的固定输入，重建内容时一并合入。`python tools/validate_content.py` 检查真实内容契约，不硬编码地域数量、步骤上限或固定版本号。`tools/build_howtocook_index.py` 默认与随包内容使用同一固定 commit。
旧的 `tools/prepare_content.py` 只用于重建初版 12 道演示内容，要求显式设置 `DEEPCOOK_REBUILD_CONTENT=1` 才运行，会覆盖当前内容，请勿用于日常更新。普通 Android 构建不运行这些工具，也不需要 Python。

## 测试

JVM 单元测试覆盖搜索组合、缺失元数据、国际菜别名、真实菜谱包完整性、来源清单、收藏顺序、浏览去重/上限、序列化和损坏数据。
Android 测试覆盖真实 DataStore 写入/重新打开/独立清理，以及搜索、阅读翻步和恢复、收藏撤销、主题与字号。
新增回归覆盖保存失败重试、补全期间改名、手写资料保护、草稿恢复、封面取消/提交时机、EXIF 镜像与坏图、自建编辑删除和收藏清理。
同时验证搜索框尺寸、平级转场中导航栏保持原位、多级转场方向、搜索条件恢复和空历史页的返回导航。
`tools/measure_navigation.py --apk <APK> --label <名称>` 可在专用模拟器重复导航并保存 Android `gfxinfo` 帧耗时报告；脚本会清理该模拟器中的 DeepCook 测试数据，拒绝物理设备。
真实文件持久化放在 Android 测试中，以使用 Android 文件系统的替换语义。
`python -m unittest discover -s tools/tests -v` 检查导入器的原文公式、嵌套步骤、缺失用量及总用时提取规则。

当前合并说明见 `docs/合并审查与交付.md`，个人菜谱说明见 `docs/我的菜谱.md`；其他带旧版数量的历史文档仅记录当时的实现。
