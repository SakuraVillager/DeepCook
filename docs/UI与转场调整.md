# DeepCook 0.1.1 UI 与转场调整

## 页面调整

- 首页左上角仅显示 DeepCook 文字；搜索入口移至右上角。
- 搜索结果页使用浅奶油底色、细边框的紧凑输入框，默认高 52dp，与返回按钮居中对齐；系统大字体下允许增高。
- 删除“我的厨房”底部的“保存在这台设备”说明卡片。

## Mihon 转场适配

参考本机 Mihon 的 `ui/home/HomeScreen.kt` 和 `presentation/util/Navigator.kt`，使用相同的 Material Motion Compose 2.0.1 库。

| 场景 | 转场 |
| --- | --- |
| 找菜、收藏、我的之间切换 | 200ms Fade Through，initialScale = 1f；主导航保持原位 |
| 首页进入搜索、进入详情、阅读、最近浏览及设置子页 | Shared Axis X，库默认 300ms、30dp；返回时反向 |

外层导航承载详情与设置等页面，内层承载三个主入口及搜索。进入外层页面时，首页内容和主导航一起退场。返回时恢复原入口、搜索条件与滚动状态。

最近浏览空状态的“去找菜”请求在内层导航恢复真实目的地后执行，执行后即消费，避免后续返回设置时再次跳转。旧版扁平导航的临时恢复状态使用新的根导航键隔离，收藏、浏览记录和偏好不受影响。

## 验证与产物

自动检查覆盖筛选与用户数据、真实 DataStore 持久化、浏览与阅读链路、主题设置、紧凑搜索尺寸、转场方向、主导航位置、搜索恢复和空历史页导航。

2026-10-16 验证结果：Debug 构建成功，7 项 JVM 单元测试与 9 项 Android 模拟器测试全部通过；ktlint 与 `git diff --check` 通过；Android Lint 为 0 错误、6 条 SDK/依赖升级提示。

实际页面截图通过 `tools/capture_emulator.py` 在专用模拟器生成，包含浅色、深色、手机与宽屏以及系统字体缩放。

- APK：`artifacts/DeepCook-0.1.1-debug.apk`
- 当前检查报告：`artifacts/reports/navigation-0.1.1`
- 当前截图：`artifacts/screenshots`

初版功能与验证记录见 `实现与验证.md`。第三方来源与许可证见根目录 `THIRD_PARTY_NOTICES.md`。
