# Hail 与 Mihon：Kotlin 库、Material 3 组件、架构与设计风格

整理日期：2026-10-16。分析对象为本地源码，Hail 提交 `1d72df1`，Mihon 提交 `7aacaa349`。

本文同时覆盖界面视觉风格与程序设计风格。依赖版本均取自本地版本目录，未联网核验，也未运行 Gradle 解析最终依赖树；“声明”不等于当前构建产物一定打包。组件代码均摘自本地项目，保留业务变量、资源、import 上下文的依赖，属于源码阅读片段，不是可独立编译的示例。长调用仅展示主要部分，具体边界以来源行号为准。

## 目录

1. [项目与技术概览](#1-项目与技术概览)
2. [Kotlin 使用库与构建依赖](#2-kotlin-使用库与构建依赖)
3. [Material 3 视觉与主题系统](#3-material-3-视觉与主题系统)
4. [Material 3 Compose 组件逐项代码](#4-material-3-compose-组件逐项代码)
5. [传统 Material View 组件逐项代码](#5-传统-material-view-组件逐项代码)
6. [项目自封装组件与第三方界面库](#6-项目自封装组件与第三方界面库)
7. [项目架构与主要数据流](#7-项目架构与主要数据流)
8. [程序设计风格与工程规范](#8-程序设计风格与工程规范)
9. [在新项目中的参考方式](#9-在新项目中的参考方式)
10. [完整 Material 3 API 导入索引与验证口径](#10-完整-material-3-api-导入索引与验证口径)

## 1. 项目与技术概览

| 项目 | Hail | Mihon |
|---|---|---|
| 定位 | Android 应用冻结与自动化管理 | 漫画书库、内容源、下载、阅读、进度追踪 |
| 主语言 | Kotlin | Kotlin |
| 源码规模 | 55 个 `.kt` 文件 | 898 个 `.kt` 文件 |
| 构建 | Gradle Kotlin DSL、单 app 模块 | Gradle Kotlin DSL、多模块、约定插件 |
| UI | Fragment/XML/ViewBinding + 局部 Compose | Compose 为主要页面技术，阅读器等保留 View |
| 主题 | Material 3、动态色、明暗模式 | Material 3 Expressive、多套主题、Monet、AMOLED |
| 状态 | LiveData、ViewModel、协程，局部 Flow | ViewModel、StateFlow/Flow、协程，仍有 RxJava 依赖 |
| 本地数据 | SharedPreferences、JSON 文件 | SQLDelight/SQLite、偏好设置封装 |
| 依赖组织 | object 单例与直接调用较多 | Metro 注入为重要机制，仍有 Injekt |

`.kt` 是语言判断依据；Mihon 的 `src/main/java` 中大量存放 Kotlin 文件。XML 是资源/布局语言，SQLDelight 与 GraphQL 是查询/接口描述，不应与 Kotlin 业务语言混为一谈。以上文件数基于当前目录扫描，不代表代码行数或 GitHub 语言占比。

## 2. Kotlin 使用库与构建依赖

### 2.1 关键依赖与用途

| 能力 | Hail | Mihon |
|---|---|---|
| Kotlin / Compose 编译器 | Kotlin 配置 2.3.21；Compose compiler 插件 | Kotlin 配置 2.4.20；Compose、Serialization、Metro 编译插件 |
| 异步 | kotlinx.coroutines 1.10.2 | kotlinx.coroutines 1.11.0；另有 RxJava 1.3.8 |
| Compose 版本对齐 | Compose BOM 2025.12.01 | **Compose alpha BOM 2026.09.01**，坐标是 `compose-bom-alpha` |
| 传统 Material | Material Components 1.13.0 | Material Components 1.14.0 |
| 页面与导航 | Activity Compose、Fragment Navigation | Voyager 2.2.21-1.10.3、自适应导航套件 |
| 状态与生命周期 | LiveData、AndroidViewModel、lifecycleScope | Lifecycle/ViewModel、StateFlow、Metro ViewModel 集成 |
| 后台任务 | WorkManager 2.11.0 | WorkManager 2.12.0 |
| 网络 | Android/JDK HttpURLConnection 简单封装 | OkHttp 5.5.0、Okio 3.18.2、Apollo 5.2.0、Jsoup 1.23.2 |
| 序列化 | org.json，属于平台能力 | kotlinx.serialization 1.11.0、JSON/Protobuf、xmlutil |
| 存储 | SharedPreferences + JSON 文件 | SQLDelight 2.4.0、SQLite、UniFile、磁盘缓存 |
| 图片 | AppIconLoader 1.5.0、自建图标缓存 | Coil 3.6.3、图片解码、缩放/分块查看组件 |
| 权限与系统管理 | Shizuku、Dhizuku、HiddenApiBypass、libxposed | Shizuku；用途与 Hail 的冻结权限路由不同，需按具体功能阅读 |
| 设置界面 | compose-preference 1.1.1 | 项目内部 Preference 组件与偏好设置抽象 |
| 搜索 | pinyin4j、拼音/九键/模糊匹配封装 | 查询语法与过滤逻辑、字符串相似度库 |
| 工程规范 | 配置相对集中 | Spotless + ktlint、build-logic 约定插件 |
| 测试/诊断 | 本次未发现同等规模独立测试依赖配置 | JUnit、Kotest、MockK、LeakCanary、Baseline Profile |

BOM 管理的 Material 3/Compose 子库不在版本目录中单独指定版本。不能把 BOM 版本直接写成 Material 3 的 Maven 版本。AGP/Kotlin 插件、测试库、运行库在下面分别列出；Java toolchain 也不等于 Java 业务源码。

### 2.2 Hail 完整版本目录

来源：[Hail/gradle/libs.versions.toml](<E:/yhz/Projects/Hail/gradle/libs.versions.toml>)。下表覆盖 `[libraries]`；引用位置是静态 Gradle 文本匹配（包含 bundle 展开），不是依赖解析结果。显示“目录声明”时不能据此断言未使用，尤其是 build-logic 中的动态访问。

| 别名 | Maven 坐标 | 配置版本 | 用途 | 构建引用示例 |
|---|---|---|---|---|
| `androidx-activity-compose` | `androidx.activity:activity-compose` | `BOM / 平台管理` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `androidx-compose-bom` | `androidx.compose:compose-bom` | `2025.12.01` | Compose 依赖版本对齐平台 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `androidx-constraintlayout` | `androidx.constraintlayout:constraintlayout` | `2.2.1` | 传统约束布局 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `androidx-biometric-ktx` | `androidx.biometric:biometric-ktx` | `1.2.0-alpha05` | 生物识别认证 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `androidx-appcompat` | `androidx.appcompat:appcompat` | `1.7.1` | 传统 Android UI 兼容 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `androidx-lifecycle-livedata-ktx` | `androidx.lifecycle:lifecycle-livedata-ktx` | `2.10.0` | 生命周期与界面状态容器 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `androidx-core-ktx` | `androidx.core:core-ktx` | `1.17.0` | 基础 Android/Kotlin 支持 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `androidx-material-icons-extended` | `androidx.compose.material:material-icons-extended` | `BOM / 平台管理` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `androidx-material3` | `androidx.compose.material3:material3` | `BOM / 平台管理` | Compose Material 3 组件 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `androidx-preference-ktx` | `androidx.preference:preference-ktx` | `1.2.1` | 偏好设置或设置界面 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `androidx-navigation-ui-ktx` | `androidx.navigation:navigation-ui-ktx` | `2.9.6` | 页面导航 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `androidx-navigation-fragment-ktx` | `androidx.navigation:navigation-fragment-ktx` | `2.9.6` | 页面导航 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `androidx-ui-tooling` | `androidx.compose.ui:ui-tooling` | `BOM / 平台管理` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `androidx-ui-tooling-preview` | `androidx.compose.ui:ui-tooling-preview` | `BOM / 平台管理` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `androidx-work-runtime-ktx` | `androidx.work:work-runtime-ktx` | `2.11.0` | 后台任务调度 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `androidx-swiperefreshlayout` | `androidx.swiperefreshlayout:swiperefreshlayout` | `1.2.0` | 滑动交互 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `appiconloader` | `me.zhanghai.android.appiconloader:appiconloader` | `1.5.0` | 应用图标加载 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `commons-text` | `org.apache.commons:commons-text` | `1.15.0` | 文本处理 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `compose-preference` | `me.zhanghai.compose.preference:library` | `1.1.1` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `dhizuku-api` | `io.github.iamr0s:Dhizuku-API` | `2.5.4` | 设备所有者权限代理 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `hiddenapibypass` | `org.lsposed.hiddenapibypass:hiddenapibypass` | `6.1` | 隐藏 API 访问辅助 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `insetter` | `dev.chrisbanes.insetter:insetter` | `0.6.1` | 系统窗口边距适配 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `kotlinx-coroutines-android` | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.10.2` | 协程、异步调度及 Flow；test 变体用于测试 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `material` | `com.google.android.material:material` | `1.13.0` | 传统 Android Material Components | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `pinyin4j` | `com.belerweb:pinyin4j` | `2.5.1` | 中文拼音处理 | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `shizuku-api` | `dev.rikka.shizuku:api` | `13.1.5` | 系统权限桥接或 provider | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `shizuku-provider` | `dev.rikka.shizuku:provider` | `13.1.5` | 系统权限桥接或 provider | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |
| `libxposed-api` | `io.github.libxposed:api` | `102.0.0` | Xposed 接口，Hail 为 compileOnly | [Hail/app/build.gradle.kts](<E:/yhz/Projects/Hail/app/build.gradle.kts>) |

| Gradle 插件别名 | 插件 ID | 配置版本 |
|---|---|---|
| `android-application` | `com.android.application` | `9.2.1` |
| `compose-compiler` | `org.jetbrains.kotlin.plugin.compose` | `2.3.21` |

### 2.3 mihon 完整版本目录

来源：[mihon/gradle/libs.versions.toml](<E:/yhz/Projects/mihon/gradle/libs.versions.toml>)。下表覆盖 `[libraries]`；引用位置是静态 Gradle 文本匹配（包含 bundle 展开），不是依赖解析结果。显示“目录声明”时不能据此断言未使用，尤其是 build-logic 中的动态访问。

| 别名 | Maven 坐标 | 配置版本 | 用途 | 构建引用示例 |
|---|---|---|---|---|
| `aboutLibraries-compose` | `com.mikepenz:aboutlibraries-compose-m3` | `15.2.0` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `android-desugar` | `com.android.tools:desugar_jdk_libs` | `2.1.5` | JDK API 兼容 | [mihon/gradle/build-logic/src/main/kotlin/PluginAndroidBase.kt](<E:/yhz/Projects/mihon/gradle/build-logic/src/main/kotlin/PluginAndroidBase.kt>) |
| `android-gradle` | `com.android.tools.build:gradle` | `9.4.1` | 构建插件/工具 | [mihon/gradle/build-logic/build.gradle.kts](<E:/yhz/Projects/mihon/gradle/build-logic/build.gradle.kts>) |
| `androidx-activity-compose` | `androidx.activity:activity-compose` | `1.13.0` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-annotation` | `androidx.annotation:annotation` | `1.11.0` | 注解支持 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-appCompat` | `androidx.appcompat:appcompat` | `1.8.0` | 传统 Android UI 兼容 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-benchmark-macroJunit4` | `androidx.benchmark:benchmark-macro-junit4` | `1.5.0` | 测试框架/运行器 | [mihon/baseline-profile/build.gradle.kts](<E:/yhz/Projects/mihon/baseline-profile/build.gradle.kts>) |
| `androidx-biometric` | `androidx.biometric:biometric-ktx` | `1.2.0-alpha05` | 生物识别认证 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-compose-animation` | `androidx.compose.animation:animation` | `BOM / 平台管理` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-compose-animationGraphics` | `androidx.compose.animation:animation-graphics` | `BOM / 平台管理` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-compose-bom` | `androidx.compose:compose-bom-alpha` | `2026.09.01` | Compose 依赖版本对齐平台 | [mihon/domain/build.gradle.kts](<E:/yhz/Projects/mihon/domain/build.gradle.kts>) |
| `androidx-compose-foundation` | `androidx.compose.foundation:foundation` | `BOM / 平台管理` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-compose-material3` | `androidx.compose.material3:material3` | `BOM / 平台管理` | Compose Material 3 组件 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-compose-material3NavSuite` | `androidx.compose.material3:material3-adaptive-navigation-suite` | `BOM / 平台管理` | 自适应导航套件 | [mihon/presentation-core/build.gradle.kts](<E:/yhz/Projects/mihon/presentation-core/build.gradle.kts>) |
| `androidx-compose-runtime` | `androidx.compose.runtime:runtime` | `BOM / 平台管理` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/source-api/build.gradle.kts](<E:/yhz/Projects/mihon/source-api/build.gradle.kts>) |
| `androidx-compose-runtimeAnnotation` | `androidx.compose.runtime:runtime-annotation` | `BOM / 平台管理` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/domain/build.gradle.kts](<E:/yhz/Projects/mihon/domain/build.gradle.kts>) |
| `androidx-compose-ui` | `androidx.compose.ui:ui` | `BOM / 平台管理` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/icons/material-symbols/build.gradle.kts](<E:/yhz/Projects/mihon/icons/material-symbols/build.gradle.kts>) |
| `androidx-compose-uiTooling` | `androidx.compose.ui:ui-tooling` | `BOM / 平台管理` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-compose-uiToolingPreview` | `androidx.compose.ui:ui-tooling-preview` | `BOM / 平台管理` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-compose-uiUtil` | `androidx.compose.ui:ui-util` | `BOM / 平台管理` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-constraintLayout` | `androidx.constraintlayout:constraintlayout` | `2.2.2` | 传统约束布局 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-core` | `androidx.core:core` | `1.19.1` | 基础 Android/Kotlin 支持 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-coreSplashScreen` | `androidx.core:core-splashscreen` | `1.2.0` | 基础 Android/Kotlin 支持 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-glance-appWidget` | `androidx.glance:glance-appwidget` | `1.2.0` | 桌面小组件 | [mihon/presentation-widget/build.gradle.kts](<E:/yhz/Projects/mihon/presentation-widget/build.gradle.kts>) |
| `androidx-interpolator` | `androidx.interpolator:interpolator` | `1.0.0` | 用途见坐标 artifact 名及对应模块构建配置 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-lifecycle-common` | `androidx.lifecycle:lifecycle-common` | `2.11.0` | 生命周期与界面状态容器 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-lifecycle-process` | `androidx.lifecycle:lifecycle-process` | `2.11.0` | 生命周期与界面状态容器 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-lifecycle-runtime` | `androidx.lifecycle:lifecycle-runtime` | `2.11.0` | 生命周期与界面状态容器 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-lifecycle-viewmodel` | `androidx.lifecycle:lifecycle-viewmodel` | `2.11.0` | 生命周期与界面状态容器 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-lifecycle-viewmodelCompose` | `androidx.lifecycle:lifecycle-viewmodel-compose` | `2.11.0` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-paging-compose` | `androidx.paging:paging-compose` | `3.5.1` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-paging-runtime` | `androidx.paging:paging-runtime` | `3.5.1` | 用途见坐标 artifact 名及对应模块构建配置 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-preference` | `androidx.preference:preference-ktx` | `1.2.1` | 偏好设置或设置界面 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-profileInstaller` | `androidx.profileinstaller:profileinstaller` | `1.4.1` | 性能配置安装 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-recyclerView` | `androidx.recyclerview:recyclerview` | `1.4.0` | 用途见坐标 artifact 名及对应模块构建配置 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-sqlite-bundled` | `androidx.sqlite:sqlite-bundled` | `2.7.1` | SQLite 数据库与驱动 | [mihon/data/build.gradle.kts](<E:/yhz/Projects/mihon/data/build.gradle.kts>) |
| `androidx-test-espresso-core` | `androidx.test.espresso:espresso-core` | `3.7.0` | Android 测试支持 | [mihon/baseline-profile/build.gradle.kts](<E:/yhz/Projects/mihon/baseline-profile/build.gradle.kts>) |
| `androidx-test-junit` | `androidx.test.ext:junit` | `1.3.0` | 测试框架/运行器 | [mihon/baseline-profile/build.gradle.kts](<E:/yhz/Projects/mihon/baseline-profile/build.gradle.kts>) |
| `androidx-test-uiautomator` | `androidx.test.uiautomator:uiautomator` | `2.4.0` | Android 测试支持 | [mihon/baseline-profile/build.gradle.kts](<E:/yhz/Projects/mihon/baseline-profile/build.gradle.kts>) |
| `androidx-viewPager` | `androidx.viewpager:viewpager` | `1.1.0` | 传统分页容器 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `androidx-webkit` | `androidx.webkit:webkit` | `1.17.1` | 用途见坐标 artifact 名及对应模块构建配置 | [mihon/core/common/build.gradle.kts](<E:/yhz/Projects/mihon/core/common/build.gradle.kts>) |
| `androidx-work` | `androidx.work:work-runtime` | `2.12.0` | 后台任务调度 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `apollo` | `com.apollographql.apollo:apollo-runtime` | `5.2.0` | GraphQL 客户端或适配器 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `apollo-adapters` | `com.apollographql.adapters:apollo-adapters-core` | `0.8.0` | GraphQL 客户端或适配器 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `archive` | `me.zhanghai.android.libarchive:library` | `1.1.7` | 归档文件处理 | [mihon/core/archive/build.gradle.kts](<E:/yhz/Projects/mihon/core/archive/build.gradle.kts>) |
| `coil-compose` | `io.coil-kt.coil3:coil-compose` | `3.6.3` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `coil-core` | `io.coil-kt.coil3:coil` | `3.6.3` | 图片加载/Compose/GIF/网络适配 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `coil-gif` | `io.coil-kt.coil3:coil-gif` | `3.6.3` | 图片加载/Compose/GIF/网络适配 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `coil-network-okhttp` | `io.coil-kt.coil3:coil-network-okhttp` | `3.6.3` | HTTP、日志、DNS 或压缩扩展 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `composeGrid` | `com.cheonjaeung.compose.grid:grid` | `2.9.0` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `composeMaterialMotion` | `io.github.fornewid:material-motion-compose-core` | `2.0.1` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `composeRichEditor` | `com.mohamedrejeb.richeditor:richeditor-compose` | `1.2.1` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `composeWebview` | `io.github.kevinnzou:compose-webview` | `0.33.6` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `conscrypt` | `org.conscrypt:conscrypt-android` | `2.7.0` | TLS/安全提供程序 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `directionalViewPager` | `com.github.tachiyomiorg:DirectionalViewPager` | `1.0.0` | 传统分页容器 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `diskLruCache` | `com.jakewharton:disklrucache` | `2.0.2` | 磁盘缓存 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `firebase-analytics` | `com.google.firebase:firebase-analytics` | `BOM / 平台管理` | 遥测、分析或崩溃采集；启用受构建配置影响 | [mihon/telemetry/build.gradle.kts](<E:/yhz/Projects/mihon/telemetry/build.gradle.kts>) |
| `firebase-bom` | `com.google.firebase:firebase-bom` | `34.19.0` | 遥测、分析或崩溃采集；启用受构建配置影响 | [mihon/telemetry/build.gradle.kts](<E:/yhz/Projects/mihon/telemetry/build.gradle.kts>) |
| `firebase-crashlytics` | `com.google.firebase:firebase-crashlytics` | `BOM / 平台管理` | 遥测、分析或崩溃采集；启用受构建配置影响 | [mihon/telemetry/build.gradle.kts](<E:/yhz/Projects/mihon/telemetry/build.gradle.kts>) |
| `flexibleAdapter` | `eu.davidea:flexible-adapter` | `5.1.0` | RecyclerView 列表适配 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `image-decoder` | `ca.mpreg:imagedecoder` | `16` | 图片显示、解码或缩放 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `injekt` | `uy.kohesive.injekt:injekt-core` | `1.16.1` | 既有依赖注入/服务定位 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `jsoup` | `org.jsoup:jsoup` | `1.23.2` | HTML 解析 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `junit-jupiter` | `org.junit.jupiter:junit-jupiter` | `6.1.3` | 测试框架/运行器 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `junit-platform-launcher` | `org.junit.platform:junit-platform-launcher` | `BOM / 平台管理` | 测试框架/运行器 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `kotest-assertions` | `io.kotest:kotest-assertions-core` | `6.2.5` | 测试断言 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `kotlin-compose-compiler-gradle` | `org.jetbrains.kotlin:compose-compiler-gradle-plugin` | `2.4.20` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/gradle/build-logic/build.gradle.kts](<E:/yhz/Projects/mihon/gradle/build-logic/build.gradle.kts>) |
| `kotlin-gradle` | `org.jetbrains.kotlin:kotlin-gradle-plugin` | `2.4.20` | 构建插件/工具 | [mihon/build.gradle.kts](<E:/yhz/Projects/mihon/build.gradle.kts>) |
| `kotlin-reflect` | `org.jetbrains.kotlin:kotlin-reflect` | `2.4.20` | 用途见坐标 artifact 名及对应模块构建配置 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `kotlinx-coroutines-android` | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.11.0` | 协程、异步调度及 Flow；test 变体用于测试 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `kotlinx-coroutines-core` | `org.jetbrains.kotlinx:kotlinx-coroutines-core` | `1.11.0` | 协程、异步调度及 Flow；test 变体用于测试 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `kotlinx-coroutines-guava` | `org.jetbrains.kotlinx:kotlinx-coroutines-guava` | `1.11.0` | 协程、异步调度及 Flow；test 变体用于测试 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `kotlinx-coroutines-test` | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | `1.11.0` | 协程、异步调度及 Flow；test 变体用于测试 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `kotlinx-datetime` | `org.jetbrains.kotlinx:kotlinx-datetime` | `0.8.0` | 日期与时间处理 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `kotlinx-serialization-json` | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.11.0` | 数据序列化或其编译插件 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `kotlinx-serialization-jsonOkio` | `org.jetbrains.kotlinx:kotlinx-serialization-json-okio` | `1.11.0` | 数据序列化或其编译插件 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `kotlinx-serialization-protobuf` | `org.jetbrains.kotlinx:kotlinx-serialization-protobuf` | `1.11.0` | 数据序列化或其编译插件 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `ktlint-bom` | `com.pinterest.ktlint:ktlint-bom` | `1.8.0` | Kotlin 风格检查 | 目录声明（未静态定位直接引用） |
| `leakCanary-core` | `com.squareup.leakcanary:leakcanary-android` | `2.14` | 内存泄漏诊断 | 目录声明（未静态定位直接引用） |
| `leakCanary-plumber` | `com.squareup.leakcanary:plumber-android` | `2.14` | 内存泄漏诊断 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `logcat` | `com.squareup.logcat:logcat` | `0.4` | 日志封装 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `markdown-coil` | `com.mikepenz:multiplatform-markdown-renderer-coil3` | `0.45.0` | 图片加载/Compose/GIF/网络适配 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `markdown-core` | `com.mikepenz:multiplatform-markdown-renderer` | `0.45.0` | Markdown 展示 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `material` | `com.google.android.material:material` | `1.14.0` | 传统 Android Material Components | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `materialKolor` | `com.materialkolor:material-kolor` | `5.0.1` | 配色生成/转换 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `metro-runtime` | `dev.zacsweers.metro:runtime` | `1.4.5` | 依赖注入与 ViewModel 集成 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `metrox-viewmodel` | `dev.zacsweers.metro:metrox-viewmodel` | `1.4.5` | 依赖注入与 ViewModel 集成 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `metrox-viewmodel-compose` | `dev.zacsweers.metro:metrox-viewmodel-compose` | `1.4.5` | Compose 界面、编译或相关扩展；具体能力见 artifact 名 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `mockk` | `io.mockk:mockk` | `1.14.11` | 测试替身 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `moko-resources` | `dev.icerock.moko:resources` | `0.26.4` | 国际化/资源管理 | [mihon/i18n/build.gradle.kts](<E:/yhz/Projects/mihon/i18n/build.gradle.kts>) |
| `natural-comparator` | `com.github.gpanther:java-nat-sort` | `natural-comparator-1.1` | 用途见坐标 artifact 名及对应模块构建配置 | [mihon/core/common/build.gradle.kts](<E:/yhz/Projects/mihon/core/common/build.gradle.kts>) |
| `okhttp-brotli` | `com.squareup.okhttp3:okhttp-brotli` | `5.5.0` | HTTP、日志、DNS 或压缩扩展 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `okhttp-core` | `com.squareup.okhttp3:okhttp` | `5.5.0` | HTTP、日志、DNS 或压缩扩展 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `okhttp-dnsOverHttps` | `com.squareup.okhttp3:okhttp-dnsoverhttps` | `5.5.0` | HTTP、日志、DNS 或压缩扩展 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `okhttp-logging` | `com.squareup.okhttp3:logging-interceptor` | `5.5.0` | HTTP、日志、DNS 或压缩扩展 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `okhttp-zstd` | `com.squareup.okhttp3:okhttp-zstd` | `5.5.0` | HTTP、日志、DNS 或压缩扩展 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `okio` | `com.squareup.okio:okio` | `3.18.2` | I/O 与缓冲读写 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `photoView` | `com.github.chrisbanes:PhotoView` | `2.3.0` | 图片缩放查看 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `quickJs` | `com.github.zhanghai.quickjs-java:quickjs-android` | `547f5b1597` | JavaScript 引擎 | [mihon/core/common/build.gradle.kts](<E:/yhz/Projects/mihon/core/common/build.gradle.kts>) |
| `reorderable` | `sh.calvin.reorderable:reorderable` | `3.1.0` | 拖拽排序 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `rxJava` | `io.reactivex:rxjava` | `1.3.8` | 用途见坐标 artifact 名及对应模块构建配置 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `shizuku-api` | `dev.rikka.shizuku:api` | `13.1.5` | 系统权限桥接或 provider | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `shizuku-provider` | `dev.rikka.shizuku:provider` | `13.1.5` | 系统权限桥接或 provider | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `spotless-gradle` | `com.diffplug.spotless:spotless-plugin-gradle` | `8.10.3` | 代码格式化 | [mihon/gradle/build-logic/build.gradle.kts](<E:/yhz/Projects/mihon/gradle/build-logic/build.gradle.kts>) |
| `sqldelight-androidxDriver` | `com.eygraber:sqldelight-androidx-driver` | `0.2.2` | 类型安全 SQL、异步查询与迁移 | [mihon/data/build.gradle.kts](<E:/yhz/Projects/mihon/data/build.gradle.kts>) |
| `sqldelight-androidxPaging` | `app.cash.sqldelight:androidx-paging3-extensions` | `2.4.0` | 类型安全 SQL、异步查询与迁移 | [mihon/data/build.gradle.kts](<E:/yhz/Projects/mihon/data/build.gradle.kts>) |
| `sqldelight-async` | `app.cash.sqldelight:async-extensions` | `2.4.0` | 类型安全 SQL、异步查询与迁移 | [mihon/data/build.gradle.kts](<E:/yhz/Projects/mihon/data/build.gradle.kts>) |
| `sqldelight-coroutines` | `app.cash.sqldelight:coroutines-extensions` | `2.4.0` | 协程、异步调度及 Flow；test 变体用于测试 | [mihon/data/build.gradle.kts](<E:/yhz/Projects/mihon/data/build.gradle.kts>) |
| `sqldelight-sqliteDialect338` | `app.cash.sqldelight:sqlite-3-38-dialect` | `2.4.0` | 类型安全 SQL、异步查询与迁移 | [mihon/data/build.gradle.kts](<E:/yhz/Projects/mihon/data/build.gradle.kts>) |
| `stringSimilarity` | `com.aallam.similarity:string-similarity-kotlin` | `0.1.0` | 字符串相似度 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `subsamplingScaleImageView` | `com.github.mihonapp:subsampling-scale-image-view` | `1b283e2bc4` | 图片显示、解码或缩放 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `swipe` | `me.saket.swipe:swipe` | `1.3.0` | 滑动交互 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `tapmoc-gradle` | `com.gradleup.tapmoc:tapmoc-gradle-plugin` | `0.4.2` | 构建插件/工具 | [mihon/gradle/build-logic/build.gradle.kts](<E:/yhz/Projects/mihon/gradle/build-logic/build.gradle.kts>) |
| `unifile` | `com.github.mihon:unifile` | `08f224c8f9` | 文件/文档 URI 抽象 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `voyager-navigator` | `cafe.adriel.voyager:voyager-navigator` | `2.2.21-1.10.3` | Compose 导航与切换 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `voyager-tabNavigator` | `cafe.adriel.voyager:voyager-tab-navigator` | `2.2.21-1.10.3` | Compose 导航与切换 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `voyager-transitions` | `cafe.adriel.voyager:voyager-transitions` | `2.2.21-1.10.3` | Compose 导航与切换 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `webgpuviewer` | `ca.mpreg:webgpuviewer` | `51` | 用途见坐标 artifact 名及对应模块构建配置 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `xmlutil-core` | `io.github.pdvrieze.xmlutil:core-android` | `1.0.2` | 基础 Android/Kotlin 支持 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |
| `xmlutil-serialization` | `io.github.pdvrieze.xmlutil:serialization` | `1.0.2` | 数据序列化或其编译插件 | [mihon/app/build.gradle.kts](<E:/yhz/Projects/mihon/app/build.gradle.kts>) |

| Gradle 插件别名 | 插件 ID | 配置版本 |
|---|---|---|
| `aboutLibraries` | `com.mikepenz.aboutlibraries.plugin.android` | `15.2.0` |
| `android-application` | `com.android.application` | `9.4.1` |
| `android-kmp-library` | `com.android.kotlin.multiplatform.library` | `9.4.1` |
| `android-library` | `com.android.library` | `9.4.1` |
| `android-test` | `com.android.test` | `9.4.1` |
| `androidx-baselineProfile` | `androidx.baselineprofile` | `1.5.0` |
| `apollo` | `com.apollographql.apollo` | `5.2.0` |
| `firebase-crashlytics` | `com.google.firebase.crashlytics` | `3.0.8` |
| `google-services` | `com.google.gms.google-services` | `4.5.0` |
| `kotlin-compose-compiler` | `org.jetbrains.kotlin.plugin.compose` | `2.4.20` |
| `kotlin-jvm` | `org.jetbrains.kotlin.jvm` | `2.4.20` |
| `kotlin-multiplatform` | `org.jetbrains.kotlin.multiplatform` | `2.4.20` |
| `kotlin-samWithReceiver` | `org.jetbrains.kotlin.plugin.sam.with.receiver` | `2.4.20` |
| `kotlin-serialization` | `org.jetbrains.kotlin.plugin.serialization` | `2.4.20` |
| `moko-resources` | `dev.icerock.mobile.multiplatform-resources` | `0.26.4` |
| `metro` | `dev.zacsweers.metro` | `1.4.5` |
| `spotless` | `com.diffplug.spotless` | `8.10.3` |
| `sqldelight` | `app.cash.sqldelight` | `2.4.0` |
| `valkyrie` | `io.github.composegears.valkyrie` | `0.5.2` |

依赖组合：

- `androidx-lifecycle`：`androidx-lifecycle-common`, `androidx-lifecycle-process`, `androidx-lifecycle-runtime`, `androidx-lifecycle-viewmodel`, `androidx-lifecycle-viewmodelCompose`
- `coil`：`coil-core`, `coil-gif`, `coil-compose`, `coil-network-okhttp`
- `kotlinx-coroutines`：`kotlinx-coroutines-core`, `kotlinx-coroutines-android`, `kotlinx-coroutines-guava`
- `markdown`：`markdown-core`, `markdown-coil`
- `okhttp`：`okhttp-core`, `okhttp-logging`, `okhttp-brotli`, `okhttp-dnsOverHttps`, `okhttp-zstd`
- `serialization`：`kotlinx-serialization-json`, `kotlinx-serialization-jsonOkio`, `kotlinx-serialization-protobuf`, `xmlutil-core`, `xmlutil-serialization`
- `shizuku`：`shizuku-api`, `shizuku-provider`
- `sqldelight`：`sqldelight-androidxDriver`, `sqldelight-async`, `sqldelight-coroutines`, `sqldelight-androidxPaging`
- `test`：`junit-jupiter`, `kotest-assertions`, `mockk`
- `voyager`：`voyager-navigator`, `voyager-tabNavigator`, `voyager-transitions`

Mihon 还通过 [mihon/gradle/mihon.versions.toml](<E:/yhz/Projects/mihon/gradle/mihon.versions.toml>) 注册内部约定插件。它们的职责是统一模块构建规则，而不是额外的 Kotlin 运行库。

### 2.4 构建中容易遗漏的细节

- Hail 的 `viewBinding = true` 与 `compose = true` 同时开启，说明混合 UI 是实际配置。
- Mihon 在 `PluginComposeAndroid` 中统一添加 BOM、编译插件及 Preview 工具，不能仅查看 `app/build.gradle.kts` 判断 Compose 配置。
- Hail 的 libxposed 为 `compileOnly`；Mihon 的调试、测试、benchmark 和 telemetry 依赖有各自作用域或启用条件。
- 平台 API（如 `SharedPreferences`、`org.json`、`HttpURLConnection`）无需独立 Maven 库，不应漏掉，也不应编造版本。

来源：[mihon/gradle/build-logic/src/main/kotlin/PluginComposeAndroid.kt:16](<E:/yhz/Projects/mihon/gradle/build-logic/src/main/kotlin/PluginComposeAndroid.kt:16>)（第 16–39 行）。

```kotlin
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.kotlin.compose.compiler)
        }

        android {
            buildFeatures {
                compose = true
            }
        }

        dependencies {
            implementation(platform(libs.androidx.compose.bom))

            // Compose @Preview tooling
            api(libs.androidx.compose.uiToolingPreview)
            debugApi(libs.androidx.compose.uiTooling)
        }
    }
}

private fun CommonExtension.buildFeatures(block: BuildFeatures.() -> Unit) {
    buildFeatures.apply(block)
}
```

## 3. Material 3 视觉与主题系统

### 3.1 Hail：紧凑的系统工具界面

应用图标网格与管理列表构成主要内容；底部导航与大屏导航栏提供页面切换；设置以分组偏好项组织。Material 3 的颜色角色、字体层级和动态色用于保持平台一致性，明暗模式可跟随系统。Compose 页面由 `AppTheme` 包裹，传统 View 继承 Material 3 动态色主题。大屏导航在源码中存在，不能只把 Hail 描述成固定手机布局。

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/theme/Theme.kt:85](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/theme/Theme.kt:85>)（第 85–104 行）。

```kotlin
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true, content: @Composable() () -> Unit
) {
    val colorScheme = when {
        dynamicColor && HTarget.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkScheme
        else -> lightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme, typography = AppTypography, content = content
    )
}
```

来源：[Hail/app/src/main/res/values/themes.xml:3](<E:/yhz/Projects/Hail/app/src/main/res/values/themes.xml:3>)（第 3–14 行）。

```xml
    <style name="Theme.Base" parent="Theme.Material3.DynamicColors.DayNight.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">?isLightTheme</item>
        <item name="android:windowLightNavigationBar" tools:targetApi="o_mr1">?isLightTheme</item>
        <item name="android:windowLayoutInDisplayCutoutMode" tools:targetApi="o_mr1">shortEdges</item>
        <item name="android:forceDarkAllowed" tools:targetApi="q">false</item>
        <item name="android:enforceStatusBarContrast" tools:targetApi="q">false</item>
        <item name="android:enforceNavigationBarContrast" tools:targetApi="q">false</item>
    </style>

    <style name="Theme.Hail" parent="Theme.Base">
```

固定色表位于 [Hail/app/src/main/kotlin/com/aistra/hail/ui/theme/Color.kt](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/theme/Color.kt>)，Compose 字体位于 [Hail/app/src/main/kotlin/com/aistra/hail/ui/theme/Type.kt](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/theme/Type.kt>)。使用 `primary`、`surface`、`onSurface` 等语义角色，减少在业务组件中直接硬编码颜色。

### 3.2 Mihon：可定制的内容阅读界面

主导航按书库、更新、历史、浏览、更多组织，手机使用 NavigationBar，平板使用 NavigationRail。主题根使用 `MaterialExpressiveTheme`；Monet 获取系统动态色，其他主题通过颜色方案映射生成，AMOLED 设置参与深色方案。图像封面、分类和阅读状态是内容组织中心。主题资源存在并不表示每个传统 View 都自动采用 Compose Expressive 行为。

来源：[mihon/app/src/main/java/eu/kanade/presentation/theme/TachiyomiTheme.kt:51](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/theme/TachiyomiTheme.kt:51>)（第 51–77 行）。

```kotlin
@Composable
private fun BaseTachiyomiTheme(
    appTheme: AppTheme,
    isAmoled: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    MaterialExpressiveTheme(
        colorScheme = remember(appTheme, isDark, isAmoled) {
            getThemeColorScheme(
                context = context,
                appTheme = appTheme,
                isDark = isDark,
                isAmoled = isAmoled,
            )
        },
        content = content,
    )
}

private fun getThemeColorScheme(
    context: Context,
    appTheme: AppTheme,
    isDark: Boolean,
    isAmoled: Boolean,
): ColorScheme {
```

来源：[mihon/app/src/main/java/eu/kanade/presentation/theme/TachiyomiTheme.kt:78](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/theme/TachiyomiTheme.kt:78>)（第 78–92 行）。

```kotlin
    val colorScheme = if (appTheme == AppTheme.MONET) {
        MonetColorScheme(context)
    } else {
        colorSchemes.getOrDefault(appTheme, TachiyomiColorScheme)
    }
    return colorScheme.getColorScheme(
        isDark = isDark,
        isAmoled = isAmoled,
        overrideDarkSurfaceContainers = appTheme != AppTheme.MONET,
    )
}

private val colorSchemes: Map<AppTheme, BaseColorScheme> = mapOf(
    AppTheme.DEFAULT to TachiyomiColorScheme,
    AppTheme.CATPPUCCIN to CatppuccinColorScheme,
```

### 3.3 共通的组件设计规则

- 颜色：从 `MaterialTheme.colorScheme` 获取语义色；文字/图标使用对应的内容色。
- 排版：使用 `MaterialTheme.typography`，避免每个页面各自设计字号。
- 交互：可点击控件需要禁用态、选中态和操作回调；装饰图标可使用空内容描述，有意义的操作需可访问标签。
- 布局：由 Scaffold 统筹顶部栏、底部栏、FAB、Snackbar 与 Insets；内容需要消费传入 padding。
- 状态：输入值、选中态与业务事件通常向外暴露；不要把数据写入行为散落到无状态展示组件中。
- 技术边界：`Row`、`Column`、`LazyColumn`、`LazyVerticalGrid`、`AnimatedVisibility`、`ComposeView` 并非 Material 3 组件；`Icons` 常来自 material 图标库，不能仅凭名称归入 Material 3 控件。

## 4. Material 3 Compose 组件逐项代码

范围是两个仓库实际出现的 Material 3 组件调用，不是 Material 3 全部官方组件目录。每个组件给出用途、项目分布和一段主要源码；同一组件的不同颜色/尺寸配置不重复抄录。辅助状态、Defaults、主题 API 在第 10 节完整索引。

### 组件快速索引

[Scaffold](#41-scaffold) | [TopAppBar](#42-topappbar) | [NavigationSuiteScaffold](#43-navigationsuitescaffold) | [NavigationSuiteItem](#44-navigationsuiteitem) | [PrimaryTabRow](#45-primarytabrow) | [PrimaryScrollableTabRow](#46-primaryscrollabletabrow) | [Tab](#47-tab) | [Text](#48-text) | [Icon](#49-icon) | [Surface](#410-surface) | [Button](#411-button) | [FilledTonalButton](#412-filledtonalbutton) | [OutlinedButton](#413-outlinedbutton) | [TextButton](#414-textbutton) | [IconButton](#415-iconbutton) | [FilledIconButton](#416-fillediconbutton) | [FilledTonalIconButton](#417-filledtonaliconbutton) | [FilledIconToggleButton](#418-filledicontogglebutton) | [SmallExtendedFloatingActionButton](#419-smallextendedfloatingactionbutton) | [SplitButtonLayout](#420-splitbuttonlayout) | [SplitButtonDefaults.TonalLeadingButton](#421-splitbuttondefaultstonalleadingbutton) | [SplitButtonDefaults.TonalTrailingButton](#422-splitbuttondefaultstonaltrailingbutton) | [Card](#423-card) | [ElevatedCard](#424-elevatedcard) | [OutlinedCard](#425-outlinedcard) | [ListItem](#426-listitem) | [AlertDialog](#427-alertdialog) | [ModalBottomSheet](#428-modalbottomsheet) | [OutlinedTextField](#429-outlinedtextfield) | [OutlinedSecureTextField](#430-outlinedsecuretextfield) | [ExposedDropdownMenuBox](#431-exposeddropdownmenubox) | [ExposedDropdownMenu](#432-exposeddropdownmenu) | [DropdownMenu](#433-dropdownmenu) | [DropdownMenuItem](#434-dropdownmenuitem) | [Checkbox](#435-checkbox) | [TriStateCheckbox](#436-tristatecheckbox) | [RadioButton](#437-radiobutton) | [Switch](#438-switch) | [Slider](#439-slider) | [VerticalSlider](#440-verticalslider) | [FilterChip](#441-filterchip) | [SuggestionChip](#442-suggestionchip) | [MultiChoiceSegmentedButtonRow](#443-multichoicesegmentedbuttonrow) | [SegmentedButton](#444-segmentedbutton) | [DatePicker](#445-datepicker) | [CircularProgressIndicator](#446-circularprogressindicator) | [LinearProgressIndicator](#447-linearprogressindicator) | [SnackbarHost](#448-snackbarhost) | [Badge](#449-badge) | [BadgedBox](#450-badgedbox) | [TooltipBox](#451-tooltipbox) | [PlainTooltip](#452-plaintooltip) | [HorizontalDivider](#453-horizontaldivider) | [VerticalDivider](#454-verticaldivider) | [下拉刷新：Material 3 状态与 Modifier 组合](#455-下拉刷新material-3-状态与-modifier-组合)

### 4.1 Scaffold

页面骨架，整合栏位、Snackbar、FAB 与内容边距。

项目分布：mihon（3 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Scaffold.kt:46](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Scaffold.kt:46>)（第 46–67 行）。

```kotlin
    androidx.compose.material3.Scaffold(
        modifier = modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        topBar = { topBar(topBarScrollBehavior) },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = {
            Box(Modifier.onSizeChanged { fabHeight = it.height }) {
                floatingActionButton()
            }
        },
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
    ) { contentPadding ->
        val paddingValues = remember(contentPadding) {
            FabAwarePaddingValues(contentPadding) {
                if (fabHeight == 0) 0.dp else with(density) { fabHeight.toDp() } + FabSpacing
            }
        }
        content(paddingValues)
    }
```

此名称在项目中存在同名封装。以上来源应结合 import 与完整限定名阅读，避免把项目封装直接当作官方签名。

Mihon 的公共 Scaffold 在 `presentation-core` 中封装了官方 Scaffold，额外处理滚动连接和 FAB 遮挡。多数业务页调用的是该封装。

### 4.2 TopAppBar

顶部应用栏，承载标题、返回和页面操作；滚动行为由外部配置。

项目分布：mihon（3 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt:137](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt:137>)（第 137–162 行）。

```kotlin
        TopAppBar(
            navigationIcon = {
                if (isActionMode) {
                    IconButton(onClick = onCancelActionMode) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Close,
                            contentDescription = stringResource(MR.strings.action_cancel),
                        )
                    }
                } else {
                    navigateUp?.let {
                        IconButton(onClick = it) {
                            UpIcon(navigationIcon = navigationIcon)
                        }
                    }
                }
            },
            title = titleContent,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = backgroundColor ?: MaterialTheme.colorScheme.surfaceColorAtElevation(
                    elevation = if (isActionMode) 3.dp else 0.dp,
                ),
            ),
            scrollBehavior = scrollBehavior,
        )
```

### 4.3 NavigationSuiteScaffold

自适应导航骨架，按设备选择底部或侧边导航。

项目分布：mihon（1 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt:101](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt:101>)（第 101–127 行）。

```kotlin
                NavigationSuiteScaffold(
                    navigationSuiteType = navigationSuiteType,
                    state = navigationSuiteState,
                    navigationSuiteColors = NavigationSuiteDefaults.colors(
                        navigationRailContainerColor = MaterialTheme.colorScheme
                            .surfaceColorAtElevation(3.dp),
                    ),
                    navigationItemVerticalArrangement = Arrangement.Center,
                    navigationItems = {
                        TABS.fastForEach { NavigationSuiteItem(it, navigationSuiteType) }
                    },
                ) {
                    AnimatedContent(
                        targetState = tabNavigator.current,
                        transitionSpec = {
                            materialFadeThroughIn(
                                initialScale = 1f,
                                durationMillis = TabFadeDuration,
                            ) togetherWith materialFadeThroughOut(durationMillis = TabFadeDuration)
                        },
                        label = "tabContent",
                    ) {
                        tabNavigator.saveableState(key = "currentTab", it) {
                            it.Content()
                        }
                    }
                }
```

### 4.4 NavigationSuiteItem

导航条目，连接当前 tab、图标、标签与切换事件。

项目分布：mihon（2 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt:110](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt:110>)（第 110–110 行）。

```kotlin
                        TABS.fastForEach { NavigationSuiteItem(it, navigationSuiteType) }
```

此名称在项目中存在同名封装。以上来源应结合 import 与完整限定名阅读，避免把项目封装直接当作官方签名。

HomeScreen 中还定义了同名成员辅助函数；本文扫描记录的是对应调用位置，官方导入与成员封装需要按作用域区分。

### 4.5 PrimaryTabRow

主级固定标签栏，选中索引与页面状态同步。

项目分布：mihon（2 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/components/TabbedDialog.kt:56](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/TabbedDialog.kt:56>)（第 56–69 行）。

```kotlin
                PrimaryTabRow(
                    modifier = Modifier.weight(1f),
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    tabTitles.fastForEachIndexed { index, tab ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { TabText(text = tab) },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
```

### 4.6 PrimaryScrollableTabRow

主级可滚动标签栏，用于较多分类。

项目分布：mihon（1 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/library/components/LibraryTabs.kt:21](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/library/components/LibraryTabs.kt:21>)（第 21–38 行）。

```kotlin
    PrimaryScrollableTabRow(
        selectedTabIndex = currentPageIndex,
        edgePadding = 0.dp,
    ) {
        categories.forEachIndexed { index, category ->
            Tab(
                selected = currentPageIndex == index,
                onClick = { onTabItemClick(index) },
                text = {
                    TabText(
                        text = category.visualName,
                        badgeCount = getItemCountForCategory(category),
                    )
                },
                unselectedContentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
```

### 4.7 Tab

标签条目，处理选中状态与点击。

项目分布：mihon（3 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/library/components/LibraryTabs.kt:26](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/library/components/LibraryTabs.kt:26>)（第 26–36 行）。

```kotlin
            Tab(
                selected = currentPageIndex == index,
                onClick = { onTabItemClick(index) },
                text = {
                    TabText(
                        text = category.visualName,
                        badgeCount = getItemCountForCategory(category),
                    )
                },
                unselectedContentColor = MaterialTheme.colorScheme.onSurface,
            )
```

### 4.8 Text

文字展示，结合主题排版和语义色。

项目分布：Hail（24 处静态调用匹配）；mihon（448 处静态调用匹配）。

**Hail 主要代码**

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/api/ApiActivity.kt:118](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/api/ApiActivity.kt:118>)（第 118–125 行）。

```kotlin
            Text(
                text = HPackages.getApplicationInfoOrNull(pkg)?.loadLabel(packageManager)?.toString() ?: pkg,
                modifier = Modifier.padding(
                    horizontal = dimensionResource(R.dimen.padding_medium),
                    vertical = dimensionResource(R.dimen.padding_small)
                ),
                style = MaterialTheme.typography.headlineSmall
            )
```

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Tabs.kt:20](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Tabs.kt:20>)（第 20–24 行）。

```kotlin
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
```

### 4.9 Icon

图标展示，区分装饰与操作的可访问描述。

项目分布：Hail（8 处静态调用匹配）；mihon（134 处静态调用匹配）。

**Hail 主要代码**

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/api/ApiActivity.kt:150](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/api/ApiActivity.kt:150>)（第 150–154 行）。

```kotlin
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(dimensionResource(R.dimen.padding_medium))
        )
```

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/IconToggleButton.kt:40](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/IconToggleButton.kt:40>)（第 40–43 行）。

```kotlin
            Icon(
                imageVector = imageVector,
                contentDescription = null,
            )
```

### 4.10 Surface

表面容器，统一背景、内容色、形状或高度。

项目分布：mihon（28 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/Pill.kt:25](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/Pill.kt:25>)（第 25–43 行）。

```kotlin
    Surface(
        modifier = modifier
            .padding(start = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = color,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier
                .padding(6.dp, 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                maxLines = 1,
                style = style,
            )
        }
    }
```

### 4.11 Button

主要操作按钮，事件通过 onClick 传递。

项目分布：mihon（12 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Button.kt:67](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Button.kt:67>)（第 67–79 行）。

```kotlin
) = Button(
    onClick = onClick,
    modifier = modifier,
    onLongClick = onLongClick,
    enabled = enabled,
    interactionSource = interactionSource,
    elevation = elevation,
    shape = shape,
    border = border,
    colors = colors,
    contentPadding = contentPadding,
    content = content,
)
```

此名称在项目中存在同名封装。以上来源应结合 import 与完整限定名阅读，避免把项目封装直接当作官方签名。

### 4.12 FilledTonalButton

使用色调背景的次级操作按钮。

项目分布：mihon（4 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/reader/components/ModeSelectionDialog.kt:51](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/reader/components/ModeSelectionDialog.kt:51>)（第 51–64 行）。

```kotlin
                FilledTonalButton(
                    onClick = onApply,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Check,
                            contentDescription = null,
                        )
                        Text(text = stringResource(MR.strings.action_apply))
                    }
                }
```

### 4.13 OutlinedButton

描边按钮，适合较低强调级别的操作。

项目分布：mihon（8 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/reader/components/ModeSelectionDialog.kt:44](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/reader/components/ModeSelectionDialog.kt:44>)（第 44–46 行）。

```kotlin
                    OutlinedButton(onClick = it) {
                        Text(text = stringResource(MR.strings.action_revert_to_default))
                    }
```

### 4.14 TextButton

文字按钮，常用于对话框确认/取消。

项目分布：Hail（2 处静态调用匹配）；mihon（76 处静态调用匹配）。

**Hail 主要代码**

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/api/ApiActivity.kt:163](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/api/ApiActivity.kt:163>)（第 163–165 行）。

```kotlin
            TextButton(onClick = ::finish) {
                Text(text = stringResource(android.R.string.ok))
            }
```

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/ActionButton.kt:23](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/ActionButton.kt:23>)（第 23–40 行）。

```kotlin
    TextButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
            )
            Text(
                text = title,
                textAlign = TextAlign.Center,
            )
        }
    }
```

### 4.15 IconButton

图标操作按钮。

项目分布：mihon（45 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/LinkIcon.kt:21](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/LinkIcon.kt:21>)（第 21–30 行）。

```kotlin
    IconButton(
        modifier = modifier.padding(4.dp),
        onClick = { uriHandler.openUri(url) },
    ) {
        Icon(
            imageVector = icon,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = label,
        )
    }
```

### 4.16 FilledIconButton

填充背景的图标按钮。

项目分布：mihon（5 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt:183](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt:183>)（第 183–194 行）。

```kotlin
            FilledIconButton(
                enabled = if (isRtl) enabledNext else enabledPrevious,
                onClick = if (isRtl) onNextChapter else onPreviousChapter,
                colors = buttonColor,
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.SkipPrevious,
                    contentDescription = stringResource(
                        if (isRtl) MR.strings.action_next_chapter else MR.strings.action_previous_chapter,
                    ),
                )
            }
```

### 4.17 FilledTonalIconButton

色调背景的图标按钮。

项目分布：mihon（3 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionDetailsScreen.kt:439](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionDetailsScreen.kt:439>)（第 439–444 行）。

```kotlin
                    FilledTonalIconButton(onClick = { onClickSourcePreferences(source.source.id) }) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Settings,
                            contentDescription = stringResource(MR.strings.label_settings),
                        )
                    }
```

### 4.18 FilledIconToggleButton

具有 checked 状态的图标切换按钮。

项目分布：mihon（1 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/IconToggleButton.kt:27](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/IconToggleButton.kt:27>)（第 27–51 行）。

```kotlin
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
            .height(48.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.small),
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
            )

            Text(
                text = title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
```

### 4.19 SmallExtendedFloatingActionButton

带标签的小型扩展 FAB。

项目分布：mihon（7 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/category/components/CategoryFloatingActionButton.kt:21](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/category/components/CategoryFloatingActionButton.kt:21>)（第 21–27 行）。

```kotlin
    SmallExtendedFloatingActionButton(
        text = { Text(text = stringResource(MR.strings.action_add)) },
        icon = { Icon(imageVector = MaterialSymbols.Rounded.Add, contentDescription = null) },
        onClick = onCreate,
        expanded = lazyListState.shouldExpandFAB(),
        modifier = modifier,
    )
```

### 4.20 SplitButtonLayout

主操作与附属操作组合的分裂按钮容器。

项目分布：mihon（1 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt:619](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt:619>)（第 619–648 行）。

```kotlin
    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.TonalLeadingButton(
                onClick = onClick,
                modifier = Modifier.height(size),
                shapes = SplitButtonDefaults.leadingButtonShapesFor(size),
                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(size),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(SplitButtonDefaults.leadingButtonIconSizeFor(size)),
                )
            }
        },
        trailingButton = {
            Box {
                SplitButtonDefaults.TonalTrailingButton(
                    checked = expanded,
                    onCheckedChange = { expanded = it },
                    modifier = Modifier.height(size),
                    shapes = SplitButtonDefaults.trailingButtonShapesFor(size),
                    contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(size),
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.ExpandMore,
                        contentDescription = stringResource(MR.strings.action_menu),
                        modifier = Modifier.size(SplitButtonDefaults.trailingButtonIconSizeFor(size)),
                    )
                }
```

此处仅截取主要调用，完整调用继续至第 666 行；未展示的闭合括号和业务内容请查看源文件。

### 4.21 SplitButtonDefaults.TonalLeadingButton

分裂按钮的色调主操作部分。

项目分布：mihon（1 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt:621](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt:621>)（第 621–632 行）。

```kotlin
            SplitButtonDefaults.TonalLeadingButton(
                onClick = onClick,
                modifier = Modifier.height(size),
                shapes = SplitButtonDefaults.leadingButtonShapesFor(size),
                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(size),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(SplitButtonDefaults.leadingButtonIconSizeFor(size)),
                )
            }
```

### 4.22 SplitButtonDefaults.TonalTrailingButton

分裂按钮的附属下拉/切换部分。

项目分布：mihon（1 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt:636](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt:636>)（第 636–648 行）。

```kotlin
                SplitButtonDefaults.TonalTrailingButton(
                    checked = expanded,
                    onCheckedChange = { expanded = it },
                    modifier = Modifier.height(size),
                    shapes = SplitButtonDefaults.trailingButtonShapesFor(size),
                    contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(size),
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.ExpandMore,
                        contentDescription = stringResource(MR.strings.action_menu),
                        modifier = Modifier.size(SplitButtonDefaults.trailingButtonIconSizeFor(size)),
                    )
                }
```

### 4.23 Card

内容卡片容器。

项目分布：Hail（1 处静态调用匹配）；mihon（1 处静态调用匹配）。

**Hail 主要代码**

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/about/AboutFragment.kt:65](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/about/AboutFragment.kt:65>)（第 65–88 行）。

```kotlin
            Card(
                onClick = { HUI.openLink(HailData.URL_WHY_FREE_SOFTWARE) },
                modifier = Modifier.height(dimensionResource(R.dimen.header_height))
                    .padding(horizontal = dimensionResource(R.dimen.padding_medium))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(
                        dimensionResource(R.dimen.padding_extra_small), Alignment.CenterVertically
                    ), horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp).background(Color.White, CircleShape),
                        contentScale = ContentScale.None
                    )
                    Text(
                        text = stringResource(R.string.app_name), style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.app_slogan), style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
```

**mihon 主要代码**

来源：[mihon/app/src/main/java/mihon/feature/support/SupportUsScreen.kt:129](<E:/yhz/Projects/mihon/app/src/main/java/mihon/feature/support/SupportUsScreen.kt:129>)（第 129–141 行）。

```kotlin
        Card {
            TextPreferenceWidget(
                title = title,
                icon = icon,
                widget = {
                    Icon(
                        imageVector = MaterialSymbols.AutoMirroredRounded.OpenInNew,
                        contentDescription = null,
                    )
                },
                onPreferenceClick = onClick,
            )
        }
```

### 4.24 ElevatedCard

带高度感的内容卡片。

项目分布：mihon（4 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SectionCard.kt:30](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SectionCard.kt:30>)（第 30–42 行）。

```kotlin
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.padding.medium)) {
            content()
        }
    }
```

### 4.25 OutlinedCard

描边卡片。

项目分布：Hail（3 处静态调用匹配）；mihon（2 处静态调用匹配）。

**Hail 主要代码**

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/about/AboutFragment.kt:90](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/about/AboutFragment.kt:90>)（第 90–99 行）。

```kotlin
            OutlinedCard(modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_medium))) {
                ClickableItem(
                    icon = Icons.Outlined.Update, title = R.string.label_version, desc = HailData.VERSION
                ) { HUI.openLink(HailData.URL_RELEASES) }
                ClickableItem(
                    icon = Icons.Outlined.InstallMobile,
                    title = R.string.label_time,
                    desc = SimpleDateFormat.getDateInstance().format(installTime)
                ) { HUI.showToast("\uD83E\uDD76\uD83D\uDCA8\uD83D\uDC09") }
            }
```

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/reader/ChapterTransition.kt:148](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/reader/ChapterTransition.kt:148>)（第 148–169 行）。

```kotlin
    OutlinedCard(
        modifier = modifier,
        colors = CardColor,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Info,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = null,
            )

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
```

### 4.26 ListItem

统一单行/多行条目的主文案、辅助文案及前后内容。

项目分布：mihon（6 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/more/settings/screen/appearance/AppLanguageScreen.kt:70](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/more/settings/screen/appearance/AppLanguageScreen.kt:70>)（第 70–89 行）。

```kotlin
                    ListItem(
                        modifier = Modifier.clickable {
                            currentLanguage = it.langTag
                        },
                        trailingContent = {
                            if (currentLanguage == it.langTag) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        supportingContent = {
                            it.localizedDisplayName?.let {
                                Text(it)
                            }
                        },
                        content = { Text(it.displayName) },
                    )
```

### 4.27 AlertDialog

标准对话框，管理关闭、确认、取消和提示内容。

项目分布：Hail（2 处静态调用匹配）；mihon（38 处静态调用匹配）。

**Hail 主要代码**

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/api/ApiActivity.kt:159](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/api/ApiActivity.kt:159>)（第 159–166 行）。

```kotlin
    private fun ErrorDialog(t: Throwable) = AlertDialog(
        text = { Text(text = t.message ?: t.stackTraceToString()) },
        onDismissRequest = ::finish,
        confirmButton = {
            TextButton(onClick = ::finish) {
                Text(text = stringResource(android.R.string.ok))
            }
        })
```

**mihon 主要代码**

来源：[mihon/app/src/main/java/mihon/feature/migration/list/components/MigrationExitDialog.kt:15](<E:/yhz/Projects/mihon/app/src/main/java/mihon/feature/migration/list/components/MigrationExitDialog.kt:15>)（第 15–30 行）。

```kotlin
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(MR.strings.migrationListScreen_exitDialogTitle))
        },
        confirmButton = {
            TextButton(onClick = exitMigration) {
                Text(text = stringResource(MR.strings.migrationListScreen_exitDialog_stopLabel))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.migrationListScreen_exitDialog_cancelLabel))
            }
        },
    )
```

### 4.28 ModalBottomSheet

模态底部面板；注意 dismiss 与生命周期。

项目分布：Hail（1 处静态调用匹配）。

**Hail 主要代码**

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/api/ApiActivity.kt:114](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/api/ApiActivity.kt:114>)（第 114–139 行）。

```kotlin
    private fun RedirectBottomSheet(pkg: String) = ModalBottomSheet(
        onDismissRequest = ::finish, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column {
            Text(
                text = HPackages.getApplicationInfoOrNull(pkg)?.loadLabel(packageManager)?.toString() ?: pkg,
                modifier = Modifier.padding(
                    horizontal = dimensionResource(R.dimen.padding_medium),
                    vertical = dimensionResource(R.dimen.padding_small)
                ),
                style = MaterialTheme.typography.headlineSmall
            )
            ClickableItem(
                icon = Icons.AutoMirrored.Outlined.Launch, title = R.string.action_launch
            ) { launchApp(pkg) }
            ClickableItem(
                icon = Icons.Rounded.AcUnit, title = R.string.action_freeze
            ) {
                if (!HailData.isChecked(pkg)) HailData.addCheckedApp(pkg)
                setAppFrozen(pkg, true)
            }
            ClickableItem(
                icon = Icons.Rounded.BrightnessLow, title = R.string.action_unfreeze
            ) { setAppFrozen(pkg, false) }
        }
    }
```

### 4.29 OutlinedTextField

描边输入框，输入值由外部状态驱动。

项目分布：mihon（9 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt:294](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt:294>)（第 294–313 行）。

```kotlin
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(
                    horizontal = SettingsItemsPaddings.Horizontal,
                    vertical = SettingsItemsPaddings.Vertical,
                ),
            label = { Text(text = label) },
            value = options[selectedIndex].toString(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                )
            },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
        )
```

### 4.30 OutlinedSecureTextField

安全文本输入，区别于普通文本框。

项目分布：mihon（1 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTrackingScreen.kt:243](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTrackingScreen.kt:243>)（第 243–271 行）。

```kotlin
                    OutlinedSecureTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentType = ContentType.Password },
                        state = password,
                        label = { Text(text = stringResource(MR.strings.password)) },
                        trailingIcon = {
                            IconButton(onClick = { hidePassword = !hidePassword }) {
                                Icon(
                                    imageVector = if (hidePassword) {
                                        MaterialSymbols.Rounded.Visibility
                                    } else {
                                        MaterialSymbols.Rounded.VisibilityOff
                                    },
                                    contentDescription = null,
                                )
                            }
                        },
                        textObfuscationMode = if (hidePassword) {
                            TextObfuscationMode.Hidden
                        } else {
                            TextObfuscationMode.Visible
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        isError = inputError && !processing,
                    )
```

### 4.31 ExposedDropdownMenuBox

输入框关联的下拉选择容器。

项目分布：mihon（1 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt:290](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt:290>)（第 290–319 行）。

```kotlin
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(
                    horizontal = SettingsItemsPaddings.Horizontal,
                    vertical = SettingsItemsPaddings.Vertical,
                ),
            label = { Text(text = label) },
            value = options[selectedIndex].toString(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                )
            },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
        )

        ExposedDropdownMenu(
            modifier = Modifier.exposedDropdownSize(),
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
```

此处仅截取主要调用，完整调用继续至第 331 行；未展示的闭合括号和业务内容请查看源文件。

### 4.32 ExposedDropdownMenu

关联输入框的下拉菜单，与 anchor 配合。

项目分布：mihon（1 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt:315](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt:315>)（第 315–330 行）。

```kotlin
        ExposedDropdownMenu(
            modifier = Modifier.exposedDropdownSize(),
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, text ->
                DropdownMenuItem(
                    text = { Text(text.toString()) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
```

### 4.33 DropdownMenu

锚定式弹出菜单，由 expanded 状态控制。

项目分布：mihon（1 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/mihon/feature/migration/list/MigrationListScreenContent.kt:343](<E:/yhz/Projects/mihon/app/src/main/java/mihon/feature/migration/list/MigrationListScreenContent.kt:343>)（第 343–372 行）。

```kotlin
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = closeMenu,
                    offset = DpOffset(8.dp, (-56).dp),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.migrationListScreen_searchManuallyActionLabel)) },
                        onClick = {
                            closeMenu()
                            onSearchManually()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.migrationListScreen_skipActionLabel)) },
                        onClick = {
                            closeMenu()
                            onSkip()
                        },
                    )
                    if (result is MigratingManga.SearchResult.Success) {
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.migrationListScreen_migrateNowActionLabel)) },
                            onClick = {
                                closeMenu()
                                onMigrate()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.migrationListScreen_copyNowActionLabel)) },
                            onClick = {
```

此处仅截取主要调用，完整调用继续至第 378 行；未展示的闭合括号和业务内容请查看源文件。

### 4.34 DropdownMenuItem

下拉菜单选项，处理选择并关闭菜单。

项目分布：mihon（31 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt:321](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt:321>)（第 321–328 行）。

```kotlin
                DropdownMenuItem(
                    text = { Text(text.toString()) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
```

### 4.35 Checkbox

二态复选框。

项目分布：mihon（8 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/LabeledCheckbox.kt:43](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/LabeledCheckbox.kt:43>)（第 43–47 行）。

```kotlin
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
```

### 4.36 TriStateCheckbox

支持中间态，适合组选择。

项目分布：Hail（1 处静态调用匹配）；mihon（1 处静态调用匹配）。

**Hail 主要代码**

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/home/PagerFragment.kt:397](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/home/PagerFragment.kt:397>)（第 397–401 行）。

```kotlin
                TriStateCheckbox(
                    state = states[index],
                    onClick = null,
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.secondary)
                )
```

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/category/components/CategoryDialogs.kt:273](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/category/components/CategoryDialogs.kt:273>)（第 273–276 行）。

```kotlin
                                TriStateCheckbox(
                                    state = checkbox.asToggleableState(),
                                    onClick = { onChange(checkbox) },
                                )
```

### 4.37 RadioButton

单选项；组内互斥由外部状态保证。

项目分布：mihon（3 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt:167](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt:167>)（第 167–170 行）。

```kotlin
            RadioButton(
                selected = selected,
                onClick = null,
            )
```

### 4.38 Switch

开关，用于即时启停或设置项。

项目分布：mihon（7 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/more/settings/widget/SwitchPreferenceWidget.kt:30](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/more/settings/widget/SwitchPreferenceWidget.kt:30>)（第 30–34 行）。

```kotlin
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.padding(start = TrailingWidgetBuffer),
            )
```

### 4.39 Slider

连续或离散数值选择器。

项目分布：mihon（2 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Slider.kt:46](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Slider.kt:46>)（第 46–56 行）。

```kotlin
    Slider(
        state = state,
        modifier = modifier,
        enabled = enabled,
        onValueChange = { onValueChange(it.roundToInt()) },
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource,
        thumb = thumb,
        track = track,
    )
```

此名称在项目中存在同名封装。以上来源应结合 import 与完整限定名阅读，避免把项目封装直接当作官方签名。

### 4.40 VerticalSlider

垂直数值选择器，项目中用于阅读相关控制。

项目分布：mihon（1 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt:291](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt:291>)（第 291–299 行）。

```kotlin
                VerticalSlider(
                    state = state,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    onValueChange = { onPageChange(it.roundToInt()) },
                    onValueChangeFinished = onPageChangeFinished,
                    interactionSource = interactionSource,
                )
```

### 4.41 FilterChip

具有选中态的筛选标签。

项目分布：mihon（25 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchToolbar.kt:80](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchToolbar.kt:80>)（第 80–94 行）。

```kotlin
                FilterChip(
                    selected = sourceFilter == SourceFilter.PinnedOnly,
                    onClick = { onChangeSearchFilter(SourceFilter.PinnedOnly) },
                    leadingIcon = {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.PushPin,
                            contentDescription = null,
                            modifier = Modifier
                                .size(FilterChipDefaults.IconSize),
                        )
                    },
                    label = {
                        Text(text = stringResource(MR.strings.pinned_sources))
                    },
                )
```

### 4.42 SuggestionChip

建议操作标签。

项目分布：mihon（1 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt:713](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt:713>)（第 713–717 行）。

```kotlin
        SuggestionChip(
            modifier = modifier,
            onClick = onClick,
            label = { Text(text = text, style = MaterialTheme.typography.bodySmall) },
        )
```

### 4.43 MultiChoiceSegmentedButtonRow

支持多选的分段按钮行。

项目分布：mihon（2 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/more/settings/widget/AppThemeModePreferenceWidget.kt:28](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/more/settings/widget/AppThemeModePreferenceWidget.kt:28>)（第 28–45 行）。

```kotlin
            MultiChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PrefsHorizontalPadding),
            ) {
                options.onEachIndexed { index, (mode, labelRes) ->
                    SegmentedButton(
                        checked = mode == value,
                        onCheckedChange = { onItemClick(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index,
                            options.size,
                        ),
                    ) {
                        Text(stringResource(labelRes))
                    }
                }
            }
```

### 4.44 SegmentedButton

分段按钮条目，结合行容器和选中态。

项目分布：mihon（3 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/more/settings/widget/AppThemeModePreferenceWidget.kt:34](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/more/settings/widget/AppThemeModePreferenceWidget.kt:34>)（第 34–43 行）。

```kotlin
                    SegmentedButton(
                        checked = mode == value,
                        onCheckedChange = { onItemClick(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index,
                            options.size,
                        ),
                    ) {
                        Text(stringResource(labelRes))
                    }
```

### 4.45 DatePicker

日期选择器，结合 rememberDatePickerState 与日期约束。

项目分布：mihon（1 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/track/TrackInfoDialogSelector.kt:156](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/track/TrackInfoDialogSelector.kt:156>)（第 156–161 行）。

```kotlin
                DatePicker(
                    state = pickerState,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                )
```

### 4.46 CircularProgressIndicator

环形进度或加载反馈。

项目分布：mihon（13 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/CircularProgressIndicator.kt:52](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/CircularProgressIndicator.kt:52>)（第 52–52 行）。

```kotlin
            CircularProgressIndicator()
```

### 4.47 LinearProgressIndicator

线性任务进度。

项目分布：mihon（5 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/mihon/feature/migration/list/components/MigrationProgressDialog.kt:34](<E:/yhz/Projects/mihon/app/src/main/java/mihon/feature/migration/list/components/MigrationProgressDialog.kt:34>)（第 34–36 行）。

```kotlin
                LinearProgressIndicator(
                    progress = { progressAnimated },
                )
```

### 4.48 SnackbarHost

轻量消息展示宿主；消息由 SnackbarHostState 发起。

项目分布：mihon（9 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/components/TabbedScreen.kt:54](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/TabbedScreen.kt:54>)（第 54–54 行）。

```kotlin
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
```

### 4.49 Badge

计数或状态徽标。

项目分布：mihon（3 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt:231](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt:231>)（第 231–253 行）。

```kotlin
            Badge {
                val desc = when (tab) {
                    is UpdatesTab -> pluralStringResource(
                        MR.plurals.notification_chapters_generic,
                        count = count,
                        count,
                    )

                    is BrowseTab -> pluralStringResource(
                        MR.plurals.update_check_notification_ext_updates,
                        count = count,
                        count,
                    )

                    else -> null
                }
                Text(
                    text = count.toString(),
                    modifier = Modifier.semantics {
                        if (desc != null) contentDescription = desc
                    },
                )
            }
```

### 4.50 BadgedBox

将徽标与目标内容组合。

项目分布：mihon（1 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/track/TrackInfoDialogHome.kt:169](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/track/TrackInfoDialogHome.kt:169>)（第 169–191 行）。

```kotlin
            BadgedBox(
                badge = {
                    if (private) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.absoluteOffset(x = (-5).dp),
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.VisibilityOff,
                                contentDescription = stringResource(MR.strings.tracked_privately),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                },
            ) {
                TrackLogoIcon(
                    tracker = tracker,
                    onClick = onOpenInBrowser,
                    onLongClick = onCopyLink,
                )
            }
```

### 4.51 TooltipBox

提示气泡的锚点、位置和状态容器。

项目分布：mihon（4 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt:201](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt:201>)（第 201–221 行）。

```kotlin
        TooltipBox(
            positionProvider = rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = {
                PlainTooltip {
                    Text(it.title)
                }
            },
            state = rememberTooltipState(),
            focusable = false,
        ) {
            IconButton(
                onClick = it.onClick,
                enabled = it.enabled,
            ) {
                Icon(
                    imageVector = it.icon,
                    tint = it.iconTint ?: LocalContentColor.current,
                    contentDescription = it.title,
                )
            }
        }
```

### 4.52 PlainTooltip

简洁文字提示内容。

项目分布：mihon（4 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt:204](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt:204>)（第 204–206 行）。

```kotlin
                PlainTooltip {
                    Text(it.title)
                }
```

### 4.53 HorizontalDivider

水平分隔线。

项目分布：Hail（1 处静态调用匹配）；mihon（34 处静态调用匹配）。

**Hail 主要代码**

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/settings/SettingsFragment.kt:270](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/settings/SettingsFragment.kt:270>)（第 270–270 行）。

```kotlin
    private fun LazyListScope.horizontalDivider() = item { HorizontalDivider() }
```

**mihon 主要代码**

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/LazyColumnWithAction.kt:37](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/LazyColumnWithAction.kt:37>)（第 37–37 行）。

```kotlin
        HorizontalDivider()
```

### 4.54 VerticalDivider

垂直分隔线。

项目分布：mihon（7 处静态调用匹配）。

**mihon 主要代码**

来源：[mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchToolbar.kt:111](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchToolbar.kt:111>)（第 111–111 行）。

```kotlin
                VerticalDivider(modifier = Modifier.height(FilterChipDefaults.Height))
```

### 4.55 下拉刷新：Material 3 状态与 Modifier 组合

下拉刷新在 Mihon 中主要通过 `rememberPullToRefreshState`、`pullToRefresh` 与 `PullToRefreshDefaults` 组合，自封装为项目组件。它不是从函数名就能识别的普通 Material 3 控件调用。

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/PullRefresh.kt:22](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/PullRefresh.kt:22>)（第 22–53 行）。

```kotlin
@Composable
fun PullRefresh(
    refreshing: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    Box(
        modifier = modifier
            .pullToRefresh(
                isRefreshing = refreshing,
                state = state,
                enabled = enabled,
                onRefresh = onRefresh,
            ),
    ) {
        content()

        PullToRefreshDefaults.Indicator(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(indicatorPadding),
            isRefreshing = refreshing,
            state = state,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

## 5. 传统 Material View 组件逐项代码

下面的类来自 `com.google.android.material`，是传统 View 体系的 Material Components。它们在 Material 3 主题下使用，但不等同于 Compose Material 3 API。`TabLayoutMediator`、`MaterialColors`、`DynamicColors`、`MaterialContainerTransform` 属于连接/颜色/过渡辅助，单独说明。XML 中源码闭合标签前的片段只用于展示关键配置。

### 5.1 AppBarLayout

顶部栏滚动容器。类：`com.google.android.material.appbar.AppBarLayout`。

**Hail**

来源：[Hail/app/src/main/res/layout-land/app_bar_main.xml:9](<E:/yhz/Projects/Hail/app/src/main/res/layout-land/app_bar_main.xml:9>)（第 9–13 行）。

```xml
    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/app_bar_layout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:theme="@style/Theme.Hail.AppBarOverlay">
```

### 5.2 MaterialToolbar

传统工具栏。类：`com.google.android.material.appbar.MaterialToolbar`。

**Hail**

来源：[Hail/app/src/main/res/layout-land/app_bar_main.xml:15](<E:/yhz/Projects/Hail/app/src/main/res/layout-land/app_bar_main.xml:15>)（第 15–20 行）。

```xml
        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?actionBarSize"
            android:background="@null"
            app:popupTheme="@style/Theme.Hail.PopupOverlay" />
```

### 5.3 BottomNavigationView

手机底部导航。类：`com.google.android.material.bottomnavigation.BottomNavigationView`。

**Hail**

来源：[Hail/app/src/main/res/layout/activity_main.xml:15](<E:/yhz/Projects/Hail/app/src/main/res/layout/activity_main.xml:15>)（第 15–21 行）。

```xml
    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottom_nav"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:labelVisibilityMode="labeled"
        app:layout_constraintBottom_toBottomOf="parent"
        app:menu="@menu/nav_main" />
```

### 5.4 MaterialButton

传统按钮。类：`com.google.android.material.button.MaterialButton`。

**mihon**

来源：[mihon/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ReaderButton.kt:18](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ReaderButton.kt:18>)（第 18–29 行）。

```kotlin
) : MaterialButton(context, attrs, defStyleAttr) {

    var viewer: PagerViewer? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        viewer?.pager?.setGestureDetectorEnabled(false)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            viewer?.pager?.setGestureDetectorEnabled(true)
        }
        return super.onTouchEvent(event)
    }
}
```

### 5.5 MaterialCardView

传统卡片。类：`com.google.android.material.card.MaterialCardView`。

**mihon**

来源：[mihon/app/src/main/res/layout/download_header.xml:2](<E:/yhz/Projects/mihon/app/src/main/res/layout/download_header.xml:2>)（第 2–11 行）。

```xml
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/container"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="8dp"
    app:cardBackgroundColor="?android:attr/colorBackground"
    app:cardElevation="0dp"
    app:cardForegroundColor="@color/draggable_card_foreground">
```

### 5.6 MaterialCheckBox

复选框。类：`com.google.android.material.checkbox.MaterialCheckBox`。

**Hail**

来源：[Hail/app/src/main/res/layout/item_apps.xml:42](<E:/yhz/Projects/Hail/app/src/main/res/layout/item_apps.xml:42>)（第 42–45 行）。

```xml
    <com.google.android.material.checkbox.MaterialCheckBox
            android:id="@+id/app_star"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"/>
```

### 5.7 MaterialAlertDialogBuilder

构造传统 Material 对话框。类：`com.google.android.material.dialog.MaterialAlertDialogBuilder`。

**Hail**

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/about/AboutFragment.kt:177](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/about/AboutFragment.kt:177>)（第 177–193 行）。

```kotlin
        MaterialAlertDialogBuilder(activity).setTitle(R.string.title_donate)
            .setSingleChoiceItems(R.array.donate_payment_entries, 0) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> if (HUI.openLink(HailData.URL_ALIPAY_API).not()) {
                        HUI.openLink(HailData.URL_ALIPAY)
                    }

                    1 -> MaterialAlertDialogBuilder(activity).setTitle(R.string.title_donate)
                        .setView(ShapeableImageView(activity).apply {
                            val padding = resources.getDimensionPixelOffset(R.dimen.padding_large)
                            setPadding(0, padding, 0, padding)
                            setImageResource(R.mipmap.qr_wechat)
                        }).setPositiveButton(R.string.donate_wechat_scan) { _, _ ->
                            app.packageManager.getLaunchIntentForPackage("com.tencent.mm")?.let {
                                it.putExtra("LauncherUI.From.Scaner.Shortcut", true)
                                startActivity(it)
```

### 5.8 ExtendedFloatingActionButton

扩展浮动操作按钮。类：`com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton`。

**Hail**

来源：[Hail/app/src/main/res/layout-land/app_bar_main.xml:28](<E:/yhz/Projects/Hail/app/src/main/res/layout-land/app_bar_main.xml:28>)（第 28–35 行）。

```xml
    <com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
        android:id="@+id/fab"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_margin="@dimen/fab_margin"
        android:text="@string/action_freeze_current"
        app:icon="@drawable/ic_round_frozen" />
```

### 5.9 ShapeableImageView

可设形状的图片。类：`com.google.android.material.imageview.ShapeableImageView`。

**Hail**

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/about/AboutFragment.kt:186](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/about/AboutFragment.kt:186>)（第 186–202 行）。

```kotlin
                        .setView(ShapeableImageView(activity).apply {
                            val padding = resources.getDimensionPixelOffset(R.dimen.padding_large)
                            setPadding(0, padding, 0, padding)
                            setImageResource(R.mipmap.qr_wechat)
                        }).setPositiveButton(R.string.donate_wechat_scan) { _, _ ->
                            app.packageManager.getLaunchIntentForPackage("com.tencent.mm")?.let {
                                it.putExtra("LauncherUI.From.Scaner.Shortcut", true)
                                startActivity(it)
                            } ?: HUI.showToast(R.string.app_not_installed)
                        }.setNegativeButton(android.R.string.cancel, null).show()

                    2 -> HUI.openLink(HailData.URL_KOFI)
                    3 -> HUI.openLink(HailData.URL_LIBERAPAY)
                    4 -> HUI.openLink(HailData.URL_PAYPAL)
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
```

### 5.10 MaterialSwitch

传统开关。类：`com.google.android.material.materialswitch.MaterialSwitch`。

**mihon**

来源：[mihon/app/src/main/res/layout/pref_widget_switch_material.xml:2](<E:/yhz/Projects/mihon/app/src/main/res/layout/pref_widget_switch_material.xml:2>)（第 2–9 行）。

```xml
<com.google.android.material.materialswitch.MaterialSwitch
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/switchWidget"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:focusable="false"
    android:clickable="false"
    android:background="@null"/>
```

### 5.11 NavigationRailView

大屏侧边导航。类：`com.google.android.material.navigationrail.NavigationRailView`。

**Hail**

来源：[Hail/app/src/main/res/layout-land/activity_main.xml:15](<E:/yhz/Projects/Hail/app/src/main/res/layout-land/activity_main.xml:15>)（第 15–22 行）。

```xml
    <com.google.android.material.navigationrail.NavigationRailView
        android:id="@+id/nav_rail"
        android:layout_width="wrap_content"
        android:layout_height="match_parent"
        app:labelVisibilityMode="labeled"
        app:layout_constraintStart_toStartOf="parent"
        app:menu="@menu/nav_main"
        app:menuGravity="center" />
```

### 5.12 CircularProgressIndicator

传统环形进度。类：`com.google.android.material.progressindicator.CircularProgressIndicator`。

**mihon**

来源：[mihon/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ReaderProgressIndicator.kt:21](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ReaderProgressIndicator.kt:21>)（第 21–37 行）。

```kotlin
 * A wrapper for [CircularProgressIndicator] that always rotates.
 *
 * By always rotating we give the feedback to the user that the application isn't 'stuck'.
 */
class ReaderProgressIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    init {
        layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    private var progress by mutableFloatStateOf(0f)

```

### 5.13 LinearProgressIndicator

传统线性进度。类：`com.google.android.material.progressindicator.LinearProgressIndicator`。

**mihon**

来源：[mihon/app/src/main/res/layout/download_item.xml:61](<E:/yhz/Projects/mihon/app/src/main/res/layout/download_item.xml:61>)（第 61–70 行）。

```xml
        <com.google.android.material.progressindicator.LinearProgressIndicator
            android:id="@+id/download_progress"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:layout_marginBottom="4dp"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintEnd_toStartOf="@+id/menu"
            app:layout_constraintStart_toEndOf="@+id/reorder"
            app:layout_constraintTop_toBottomOf="@+id/chapter_title" />
```

### 5.14 Snackbar

临时消息反馈。类：`com.google.android.material.snackbar.Snackbar`。

**Hail**

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/home/PagerFragment.kt:162](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/home/PagerFragment.kt:162>)（第 162–178 行）。

```kotlin
            Snackbar.make(activity.fab, R.string.app_not_installed, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_remove_home) { removeCheckedApp(info.packageName) }.show()
            return
        }
        launchApp(info.packageName)
    }

    override fun onItemLongClick(info: AppInfo): Boolean {
        if (info.applicationInfo == null && (!multiselect || info !in selectedList)) {
            exportToClipboard(listOf(info))
            return true
        }
        if (info in selectedList) {
            onMultiSelect()
            return true
        }
        val pkg = info.packageName
```

### 5.15 TabLayout

标签栏。类：`com.google.android.material.tabs.TabLayout`。

**Hail**

来源：[Hail/app/src/main/res/layout/fragment_home.xml:8](<E:/yhz/Projects/Hail/app/src/main/res/layout/fragment_home.xml:8>)（第 8–13 行）。

```xml
    <com.google.android.material.tabs.TabLayout
        android:id="@+id/tabs"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:clipToPadding="false"
        app:tabMode="scrollable" />
```

### 5.16 TextInputEditText

与输入容器结合的文本编辑。类：`com.google.android.material.textfield.TextInputEditText`。

**Hail**

来源：[Hail/app/src/main/res/layout/dialog_input.xml:14](<E:/yhz/Projects/Hail/app/src/main/res/layout/dialog_input.xml:14>)（第 14–18 行）。

```xml
        <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/edit_text"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:maxLength="16"/>
```

**mihon**

来源：[mihon/app/src/main/java/eu/kanade/tachiyomi/widget/TachiyomiTextInputEditText.kt:20](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/tachiyomi/widget/TachiyomiTextInputEditText.kt:20>)（第 20–36 行）。

```kotlin
 * A custom [TextInputEditText] that sets [EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING] to imeOptions
 * if [BasePreferences.incognitoMode] is true. Some IMEs may not respect this flag.
 *
 * @see setIncognito
 */
class TachiyomiTextInputEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.editTextStyle,
) : TextInputEditText(context, attrs, defStyleAttr) {

    private var scope: CoroutineScope? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val basePreferences = context.appGraph.basePreferences
```

### 5.17 TextInputLayout

输入装饰与容器。类：`com.google.android.material.textfield.TextInputLayout`。

**Hail**

来源：[Hail/app/src/main/res/layout/dialog_input.xml:6](<E:/yhz/Projects/Hail/app/src/main/res/layout/dialog_input.xml:6>)（第 6–12 行）。

```xml
    <com.google.android.material.textfield.TextInputLayout
            android:id="@+id/input_layout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/padding_large"
            android:layout_marginTop="@dimen/padding_medium"
            android:layout_marginEnd="@dimen/padding_large">
```

### 5.18 MaterialTextView

遵循主题排版的文字。类：`com.google.android.material.textview.MaterialTextView`。

**Hail**

来源：[Hail/app/src/main/res/layout/dialog_progress.xml:15](<E:/yhz/Projects/Hail/app/src/main/res/layout/dialog_progress.xml:15>)（第 15–18 行）。

```xml
    <com.google.android.material.textview.MaterialTextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/app_slogan"/>
```

### 5.19 Material 辅助类

**TabLayoutMediator**：将 TabLayout 与 ViewPager2 同步，不是独立可视组件。

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/home/HomeFragment.kt:30](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/home/HomeFragment.kt:30>)（第 30–38 行）。

```kotlin
        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.text = tags[position].first
        }.attach()
        binding.tabs.applyDefaultInsetter { paddingRelative(isRtl, start = !activity.isLandscape, end = true) }
        return binding.root
    }

    override fun onDestroyView() {
        multiselect = false
```

**MaterialColors**：从主题属性提取颜色。

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/apps/AppsAdapter.kt:82](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/apps/AppsAdapter.kt:82>)（第 82–90 行）。

```kotlin
                    MaterialColors.getColor(
                        this, androidx.appcompat.R.attr.colorError
                    )
                )
                else setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            }
            binding.appDesc.apply {
                text = pkg
                isEnabled = !HailData.grayscaleIcon || !frozen
```

来源：[mihon/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webgpu/WebGpuViewer.kt:108](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webgpu/WebGpuViewer.kt:108>)（第 108–116 行）。

```kotlin
    private fun readerOnBackgroundColor(): Int = cachedOnBackgroundColor ?: MaterialColors.getColor(
        activity.createReaderThemeContext(),
        com.google.android.material.R.attr.colorOnBackground,
        Color.WHITE,
    ).also { cachedOnBackgroundColor = it }

    protected val scope = MainScope()

    // Dedicated thread for decode worker to avoid blocking Dispatchers.Default pool
```

**DynamicColors**：传统 View 动态配色支持。

来源：[mihon/app/src/main/java/eu/kanade/tachiyomi/util/system/DeviceUtilExtensions.kt:7](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/tachiyomi/util/system/DeviceUtilExtensions.kt:7>)（第 7–8 行）。

```kotlin
    DynamicColors.isDynamicColorAvailable() || (DeviceUtil.isSamsung && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
}
```

**MaterialContainerTransform**：容器共享元素/页面过渡效果。

来源：[mihon/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt:543](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt:543>)（第 543–551 行）。

```kotlin
        if (window.sharedElementEnterTransition is MaterialContainerTransform) {
            // Wait until transition is complete to avoid crash on API 26
            window.sharedElementEnterTransition.doOnEnd {
                setOrientation(viewModel.getMangaOrientation())
            }
        } else {
            setOrientation(viewModel.getMangaOrientation())
        }

```

## 6. 项目自封装组件与第三方界面库

### 6.1 Hail 的设置 DSL 与混合 UI

`ProvidePreferenceLocals`、`listPreference`、`switchPreference` 来自 `me.zhanghai.compose.preference`；这些并非官方 Material 3 组件。它们把偏好设置读写与 Material 风格列表组合。Fragment 使用 `ComposeView` 接入 Compose，并指定跟随 ViewTree 生命周期销毁 Composition。

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/settings/SettingsFragment.kt:67](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/settings/SettingsFragment.kt:67>)（第 67–80 行）。

```kotlin
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    ProvidePreferenceLocals {
                        SettingsScreen()
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsScreen() {
```

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/settings/SettingsFragment.kt:79](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/settings/SettingsFragment.kt:79>)（第 79–106 行）。

```kotlin
    @Composable
    private fun SettingsScreen() {
        val autoFreezeAfterLock = rememberPreferenceState(HailData.AUTO_FREEZE_AFTER_LOCK, false)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            listPreference(
                key = HailData.WORKING_MODE,
                defaultValue = HailData.MODE_DEFAULT,
                onValueChange = ::onWorkingModeChange,
                values = HailData.WORKING_MODE_VALUES,
                entriesId = R.array.working_mode_entries,
                titleId = R.string.working_mode,
                icon = Icons.Outlined.Adb,
                type = ListPreferenceType.ALERT_DIALOG
            )
            switchPreference(
                key = HailData.BIOMETRIC_LOGIN,
                defaultValue = false,
                onValueChange = { _, value ->
                    if (value) resetDynamicShortcuts()
                    true
                },
                titleId = R.string.action_biometric,
                icon = Icons.Outlined.Fingerprint
            )
            horizontalDivider()
            preferenceCategory(key = "customize", title = { Text(text = stringResource(R.string.title_customize)) })
            listPreference(
                key = HailData.APP_THEME,
```

### 6.2 Mihon 的公共组件层

Mihon 的 `presentation-core/.../components/material` 包含 Scaffold、AlertDialog、Button、Slider、Tabs、PullRefresh、IconToggleButton 等封装。封装统一间距、布局、交互与主题，业务页面通过参数和槽位复用。另有 rich editor、Markdown、WebView、拖拽排序等第三方 UI 能力，它们也不应被误写为官方 Material 3。

来源：[mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Scaffold.kt:27](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Scaffold.kt:27>)（第 27–67 行）。

```kotlin
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBarScrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        rememberTopAppBarState(),
    ),
    topBar: @Composable (TopAppBarScrollBehavior) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    var fabHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    androidx.compose.material3.Scaffold(
        modifier = modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        topBar = { topBar(topBarScrollBehavior) },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = {
            Box(Modifier.onSizeChanged { fabHeight = it.height }) {
                floatingActionButton()
            }
        },
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
    ) { contentPadding ->
        val paddingValues = remember(contentPadding) {
            FabAwarePaddingValues(contentPadding) {
                if (fabHeight == 0) 0.dp else with(density) { fabHeight.toDp() } + FabSpacing
            }
        }
        content(paddingValues)
    }
```

实际阅读时先检查 import：`tachiyomi.presentation.core.components.material.*` 是项目组件，`androidx.compose.material3.*` 才是官方 API。同名函数不保证参数和行为完全相同。

## 7. 项目架构与主要数据流

### 7.1 Hail：单模块、按职责分包、局部 MVVM

```text
app
├─ ui/          Activity、Fragment、Adapter、局部 ViewModel、Compose 页面
├─ app/         AppManager、AppInfo、HailData、外部调用 API
├─ utils/       系统能力、权限桥接、搜索、文件、主题等封装
├─ work/        WorkManager 任务
├─ services/    自动冻结、快捷设置等服务
├─ receiver/    锁屏、设备管理、暂停状态等事件
├─ extensions/  Kotlin 扩展函数
└─ xposed/      应用启动 Hook
```

主要命令路径：`Fragment / Activity / Worker / Receiver → AppManager → 根据 workingMode 选择后端 → Android 系统 API / Shizuku / Root / Dhizuku / DevicePolicy`。这是统一入口加条件路由的设计；源码未把所有后端抽象成统一接口，因此不宜称为完整的接口式策略架构。

来源：[Hail/app/src/main/kotlin/com/aistra/hail/app/AppManager.kt:57](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/app/AppManager.kt:57>)（第 57–78 行）。

```kotlin
    fun setAppFrozen(packageName: String, frozen: Boolean): Boolean =
        packageName != BuildConfig.APPLICATION_ID && when (HailData.workingMode) {
            HailData.MODE_OWNER_HIDE -> HPolicy.setAppHidden(packageName, frozen)
            HailData.MODE_OWNER_SUSPEND -> HPolicy.setAppSuspended(packageName, frozen)
            HailData.MODE_DHIZUKU_HIDE -> HDhizuku.setAppHidden(packageName, frozen)
            HailData.MODE_DHIZUKU_SUSPEND -> HDhizuku.setAppSuspended(packageName, frozen)
            HailData.MODE_SU_STOP -> !frozen || HShell.forceStopApp(packageName)
            HailData.MODE_SU_DISABLE -> HShell.setAppDisabled(packageName, frozen)
            HailData.MODE_SU_HIDE -> HShell.setAppHidden(packageName, frozen)
            HailData.MODE_SU_SUSPEND -> HShell.setAppSuspended(packageName, frozen)
            HailData.MODE_SHIZUKU_STOP -> !frozen || HShizuku.forceStopApp(packageName)
            HailData.MODE_SHIZUKU_DISABLE -> HShizuku.setAppDisabled(packageName, frozen)
            HailData.MODE_SHIZUKU_HIDE -> HShizuku.setAppHidden(packageName, frozen)
            HailData.MODE_SHIZUKU_SUSPEND -> HShizuku.setAppSuspended(packageName, frozen)
            HailData.MODE_ISLAND_HIDE -> HIsland.setAppHidden(packageName, frozen)
            HailData.MODE_ISLAND_SUSPEND -> HIsland.setAppSuspended(packageName, frozen)
            HailData.MODE_PRIVAPP_STOP -> !frozen || HPackages.forceStopApp(packageName)
            HailData.MODE_JAILBREAK_DISABLE, HailData.MODE_PRIVAPP_DISABLE ->
                HPackages.setAppDisabled(packageName, frozen)

            else -> false
        }
```

列表路径：`AppsFragment → AppsViewModel → 包管理器及 AppManager → LiveData → Adapter`。ViewModel 负责搜索防抖、过滤、排序和刷新状态；业务数据也会通过全局单例读取，分层边界较宽松。

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/apps/AppsViewModel.kt:14](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/apps/AppsViewModel.kt:14>)（第 14–26 行）。

```kotlin
class AppsViewModel(application: Application) : AndroidViewModel(application) {
    val apps = MutableLiveData<List<ApplicationInfo>>()
    val isRefreshing = MutableLiveData(false)
    val query = MutableLiveData("")
    val displayApps = MutableLiveData<List<ApplicationInfo>>()

    init {
        updateAppList()
    }

    private var refreshJob: Job? = null
    private var refreshStateJob: Job? = null

```

来源：[Hail/app/src/main/kotlin/com/aistra/hail/ui/apps/AppsViewModel.kt:58](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/apps/AppsViewModel.kt:58>)（第 58–77 行）。

```kotlin
    fun updateAppList() {
        viewModelScope.launch {
            postRefreshState(true)
            apps.postValue(HPackages.getInstalledApplications())
        }
    }

    /**
     * The list that the user actually sees.
     *
     * This method is different from `updateAppList()` in that it filters and rearranges the data
     * from `apps` and places it in `displayApps`.
     * */
    fun updateDisplayAppList() {
        apps.value?.let {
            viewModelScope.launch {
                postRefreshState(true)
                displayApps.postValue(filterList(it, query.value))
                postRefreshState(false)
            }
```

数据存储：`HailData` 以 SharedPreferences 保存设置，以 JSON 文件保存应用集合及标签。`HRepository` 主要是简单 HTTP 请求封装，不等于覆盖应用全部数据操作的 Repository 层。

来源：[Hail/app/src/main/kotlin/com/aistra/hail/utils/HRepository.kt:8](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/utils/HRepository.kt:8>)（第 8–15 行）。

```kotlin
object HRepository {
    suspend fun request(url: String): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            (URL(url).openConnection() as HttpURLConnection).inputStream.bufferedReader()
                .use { it.readText() }
        }
    }
}
```

### 7.2 Mihon：多模块分层、业务用例与响应式状态

| 模块 | 职责与边界 |
|---|---|
| `app` | 应用装配、页面、导航、下载/备份/追踪等大量功能；仍保留部分 domain/data 包 |
| `domain` | 模型、Interactor、Repository 接口、偏好/服务抽象；不是纯粹无 Android 依赖的业务层 |
| `data` | SQLDelight、Repository 实现、映射与数据库迁移 |
| `source-api` | 内容源接口和兼容模型 |
| `source-local` | 本地内容源实现 |
| `presentation-core` | 公共 Compose 控件、Material 包装、屏幕与工具 |
| `presentation-widget` | 桌面小组件相关实现，不宜与通用 Compose 控件层混淆 |
| `core:common` | 通用基础能力 |
| `core:archive` | 归档处理 |
| `core:metro` | 注入基础设施 |
| `core-metadata` | 元数据相关能力 |
| `i18n` / `icons:*` | 国际化和图标资源 |
| `telemetry` | 可按发行配置启用的遥测 |
| `baseline-profile` | 启动/交互性能配置生成 |
| `gradle/build-logic` | 构建约定插件，不是运行时业务层 |

逻辑调用链与编译依赖要区分：界面调用 `ViewModel → Interactor → Repository 接口`，实际查询由 `RepositoryImpl → SQLDelight` 完成；Gradle 中 `data` 依赖 `domain` 来实现接口，`domain` 不需要反向依赖 `data`。`app` 同时依赖两者完成装配。

来源：[mihon/domain/src/main/java/tachiyomi/domain/manga/interactor/GetLibraryManga.kt:14](<E:/yhz/Projects/mihon/domain/src/main/java/tachiyomi/domain/manga/interactor/GetLibraryManga.kt:14>)（第 14–36 行）。

```kotlin
@Inject
class GetLibraryManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): List<LibraryManga> {
        return mangaRepository.getLibraryManga()
    }

    fun subscribe(): Flow<List<LibraryManga>> {
        return mangaRepository.getLibraryMangaAsFlow()
            .retry {
                if (it is NullPointerException) {
                    delay(0.5.seconds)
                    true
                } else {
                    false
                }
            }.catch {
                this@GetLibraryManga.logcat(LogPriority.ERROR, it)
            }
    }
}
```

来源：[mihon/data/src/main/java/tachiyomi/data/manga/MangaRepositoryImpl.kt:30](<E:/yhz/Projects/mihon/data/src/main/java/tachiyomi/data/manga/MangaRepositoryImpl.kt:30>)（第 30–53 行）。

```kotlin
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class MangaRepositoryImpl(
    private val database: Database,
) : MangaRepository {

    override suspend fun getMangaById(id: Long): Manga {
        return database.mangaQueries
            .getMangaById(id, MangaMapper::mapManga)
            .awaitAsOne()
    }

    override fun getMangaByIdAsFlow(id: Long): Flow<Manga> {
        return database.mangaQueries
            .getMangaById(id, MangaMapper::mapManga)
            .subscribeToOne()
    }

    override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? {
        return database.mangaQueries
            .getMangaByUrlAndSource(sourceId = sourceId, remoteUrl = url, mapper = MangaMapper::mapManga)
            .awaitAsOneOrNull()
    }
```

书库由多个 Flow 合成：查询、分类、追踪状态、过滤偏好共同决定界面状态。数据库或偏好变化会推动状态更新，Compose 根据状态重组。用户操作经 ViewModel 调用用例/管理器再写回数据。整体呈现单向状态流倾向，但没有证据表明全项目严格使用同一种 MVI 契约。

来源：[mihon/app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryViewModel.kt:132](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryViewModel.kt:132>)（第 132–151 行）。

```kotlin
    private val library = combine(
        searchQuery.debounce(0.25.seconds),
        getCategories.subscribe(),
        getFavoritesFlow(),
        combine(getTracksPerManga.subscribe(), getTrackingFiltersFlow(), ::Pair),
        getLibraryItemPreferencesFlow(),
    ) { searchQuery, categories, favorites, (tracksMap, trackingFilters), itemPreferences ->
        val showSystemCategory = favorites.any { it.libraryManga.categories.contains(0) }
        val filteredFavorites = favorites
            .applyFilters(tracksMap, trackingFilters, itemPreferences)
            .let { libraryItems ->
                if (searchQuery.isNullOrEmpty()) {
                    libraryItems
                } else {
                    val queryNode = QueryNode.from(searchQuery)
                    libraryItems.filter { queryNode.matches(it) }
                }
            }

        LibraryData(
```

依赖注入以 Metro 为重要机制：`@Inject` 构造注入、`@ContributesBinding` 绑定实现、`@DependencyGraph` 汇总应用图；项目仍有 Injekt 依赖和使用，说明不能只用一种注入框架描述全部代码。

来源：[mihon/app/src/main/java/mihon/app/di/AppGraph.kt:60](<E:/yhz/Projects/mihon/app/src/main/java/mihon/app/di/AppGraph.kt:60>)（第 60–79 行）。

```kotlin
@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [AppBindings::class],
)
interface AppGraph : ViewModelGraph {
    fun inject(app: App)
    fun inject(mainActivity: MainActivity)
    fun inject(readerActivity: ReaderActivity)
    fun inject(webViewActivity: WebViewActivity)
    fun inject(baseOAuthLoginActivity: BaseOAuthLoginActivity)
    fun inject(libraryUpdateWorker: LibraryUpdateWorker)
    fun inject(metadataUpdateWorker: MetadataUpdateWorker)
    fun inject(backupRestoreWorker: BackupRestoreWorker)
    fun inject(backupCreateWorker: BackupCreateWorker)
    fun inject(delayedTrackingUpdateWorker: DelayedTrackingUpdateWorker)
    fun inject(downloadWorker: DownloadWorker)
    fun inject(notificationReceiver: NotificationReceiver)
    fun inject(notificationReceiver: SecureActivityDelegateImpl)
    fun inject(extensionInstallActivity: ExtensionInstallActivity)

```

### 7.3 架构边界与演进状态

- Hail 易于追踪单个系统操作，但全局单例与 UI 直接调用管理器使隔离测试和替换实现的成本上升。
- Mihon 的业务接口与数据实现分离更清晰，适合较多功能；但 `app` 仍很大，不能把模块目录直接理解为严格完整的 Clean Architecture。
- 两个项目都是混合 UI。Mihon 的阅读器仍使用 ViewBinding/传统 View，并叠加 Compose；源码中的 presenter 注释并不能证明所有相关实现仍是统一的旧 MVP。
- `domain` 以 Android library 构建并依赖若干平台相关能力，因此并不是完全平台无关的纯 Kotlin 模块。

## 8. 程序设计风格与工程规范

### 8.1 Hail 的代码风格

| 习惯 | 具体表现 | 影响 |
|---|---|---|
| 单例集中管理 | `object AppManager`、`HailData`、`HShizuku` | 调用直接，但隐藏了依赖关系 |
| 前缀式工具命名 | `HFiles`、`HPackages`、`HPolicy` | 容易识别项目工具，职责需继续看文件内容 |
| Kotlin 简洁表达 | 扩展属性/函数、when、with/run、表达式函数、runCatching | 减少样板；长表达式需注意可读性 |
| 协程切换线程 | viewModelScope/lifecycleScope、Dispatchers.IO/Default | 让后台工作与生命周期关联 |
| 局部响应式 | MutableLiveData、观察列表/查询状态，设置中也有 Flow | 保留传统 Android 页面组织 |
| 直接处理系统功能 | 权限通道分支集中于管理器和工具层 | 适合系统工具；与平台耦合较高 |
| import 风格 | 一些文件使用通配导入 | 简洁，但不利于辨别同名 API 来源 |

上表是源码观察，不是项目明文规定。未发现 Hail 根目录有与 Mihon 同等明确的 `.editorconfig` 风格约束，不能据此推断作者没有其他编辑器规范。

### 8.2 Mihon 的代码风格

| 习惯 | 具体表现 | 影响 |
|---|---|---|
| 按业务分组 | manga/category/chapter 等下再分 model/interactor/repository | 查找业务入口更明确 |
| 用例式命名 | GetLibraryManga、SetReadStatus、UpdateManga | 一类对象表达一项业务动作 |
| 构造注入 | ViewModel 和 Repository 显式列出依赖 | 便于替换实现；参数多也反映类的职责范围 |
| Flow 组合 | combine、map、flatMapLatest、stateIn、debounce | 状态来源与转换更集中 |
| 数据与状态建模 | data class、sealed 类型、@Immutable 等按场景使用 | 便于表达状态和驱动重组 |
| Compose 参数化 | Modifier、回调、槽位、主题 token | 展示组件更容易复用 |
| 统一公共封装 | Scaffold、Button、AlertDialog 等 | 统一 Insets、间距和交互 |
| 兼容渐进演进 | 传统 View、旧包名、RxJava/Injekt 与新机制共存 | 真实项目存在多代技术边界 |
| 构建规则集中 | build-logic、版本目录、Spotless | 降低多模块配置重复 |

明确的格式规则来自 `.editorconfig`：Kotlin/KTS 缩进 4 空格、最长行 120、允许尾随逗号、几乎禁止星号 import、Composable 函数名规则例外。Spotless 对 Kotlin、Gradle 脚本及 XML 分别配置，ktlint 使用目录声明版本；部分具体规则被禁用，不能简单等同于全部默认 ktlint。

来源：[mihon/.editorconfig:1](<E:/yhz/Projects/mihon/.editorconfig:1>)（第 1–32 行）。

```kotlin
root = true

[*]
charset = utf-8
indent_size = 2
indent_style = space
insert_final_newline = true
trim_trailing_whitespace = true

[*.{xml,sq,sqm,aidl}]
indent_size = 4

# noinspection EditorConfigKeyCorrectness
[*.{kt,kts}]
indent_size = 4
max_line_length = 120

ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true
ij_kotlin_name_count_to_use_star_import = 2147483647
ij_kotlin_name_count_to_use_star_import_for_members = 2147483647

ktlint_code_style = intellij_idea
ktlint_function_naming_ignore_when_annotated_with = Composable
ktlint_standard_class-signature = disabled
ktlint_standard_comment-wrapping = disabled
ktlint_standard_discouraged-comment-location = disabled
ktlint_standard_function-expression-body = disabled
ktlint_standard_function-signature = disabled
ktlint_standard_type-argument-comment = disabled
ktlint_standard_type-parameter-comment = disabled
ktlint_standard_blank-line-between-when-conditions = disabled
```

来源：[mihon/gradle/build-logic/src/main/kotlin/PluginSpotless.kt:18](<E:/yhz/Projects/mihon/gradle/build-logic/src/main/kotlin/PluginSpotless.kt:18>)（第 18–43 行）。

```kotlin
        spotless {
            kotlin {
                target("src/**/*.kt")
                ktlint(ktlintVersion)
                trimTrailingWhitespace()
                endWithNewline()
            }

            kotlinGradle {
                target("*.kts")
                ktlint(ktlintVersion)
                trimTrailingWhitespace()
                endWithNewline()
            }

            format("xml") {
                target("src/**/*.xml")
                trimTrailingWhitespace()
                endWithNewline()
            }
        }
    }
}

private fun Project.spotless(block: SpotlessExtension.() -> Unit) {
    extensions.configure(block)
```

### 8.3 异常、线程、状态的对照

| 主题 | Hail 观察 | Mihon 观察 |
|---|---|---|
| 异常反馈 | runCatching、Result、布尔/空值结果、错误对话框 | Flow catch/retry、日志、业务操作反馈；模式因功能而异 |
| 线程 | UI 生命周期协程中按任务切换 IO/Default | 协程 + Flow 调度、异步 SQLDelight 查询 |
| 状态来源 | 直接读取单例配置并更新 LiveData | 业务数据与偏好变化合成状态 |
| 可替换性 | 静态单例依赖较多 | 接口与构造注入提高可替换性，但平台耦合仍存在 |
| 可维护性成本 | 上手路径短，规模扩大后需要关注全局耦合 | 模块/抽象较多，学习成本与编译配置复杂度更高 |

## 9. 在新项目中的参考方式

下面是基于源码的设计建议，不是 Hail/Mihon 已经具备的实现，也不要求当前 DeepCook 已采用这些结构。

1. 小型原生工具可以参考 Hail：一个 app 模块、清楚的功能分包、统一系统能力入口；业务扩大后再提取接口和数据层。
2. 内容或数据较复杂的应用可参考 Mihon：区分界面状态、业务用例、数据接口与实现；先按变化边界拆分，避免照搬全部模块。
3. 新建 Compose 页面可采用“主题根 → Scaffold → 无状态展示组件 → 外部 ViewModel/状态源”的组织方式。
4. 建立少量公共组件：页面骨架、加载/空/错误状态、确认对话框、设置条目；只封装项目实际重复的行为。
5. Material 3 配色使用语义 token；主题切换、字体与窗口边距放在统一层处理。
6. 依赖升级以最终解析版本和构建验证为准，尤其 Mihon 使用 alpha BOM 和实验性 Expressive API，不能把本文中的本地版本当作稳定兼容组合建议。
7. 移植组件前同时查看 import、外围状态、资源与生命周期；保留项目许可证要求。Hail 为 GPL-3.0，Mihon 为 Apache-2.0，具体以各仓库 LICENSE 全文为准。

## 10. 完整 Material 3 API 导入索引与验证口径

### 10.1 官方 API 与辅助对象索引

下表覆盖源码中显式导入的 Material 3 路径，包含主题、颜色、默认值、状态、手势及实验 API；这些不是都属于可视组件。“文件数”按导入该路径的文件计数。通配导入另列，通配文件里的实际可视调用已计入第 4 节。

**Hail**

| API 路径 | 文件数 | 代表来源 |
|---|---|---|
| `androidx.compose.material3.*` | 3 | [Hail/app/src/main/kotlin/com/aistra/hail/ui/about/AboutFragment.kt](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/about/AboutFragment.kt>) |
| `androidx.compose.material3.CheckboxDefaults` | 1 | [Hail/app/src/main/kotlin/com/aistra/hail/ui/home/PagerFragment.kt](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/home/PagerFragment.kt>) |
| `androidx.compose.material3.HorizontalDivider` | 1 | [Hail/app/src/main/kotlin/com/aistra/hail/ui/settings/SettingsFragment.kt](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/settings/SettingsFragment.kt>) |
| `androidx.compose.material3.Icon` | 1 | [Hail/app/src/main/kotlin/com/aistra/hail/ui/settings/SettingsFragment.kt](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/settings/SettingsFragment.kt>) |
| `androidx.compose.material3.MaterialTheme` | 1 | [Hail/app/src/main/kotlin/com/aistra/hail/ui/home/PagerFragment.kt](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/home/PagerFragment.kt>) |
| `androidx.compose.material3.Text` | 2 | [Hail/app/src/main/kotlin/com/aistra/hail/ui/home/PagerFragment.kt](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/home/PagerFragment.kt>) |
| `androidx.compose.material3.TriStateCheckbox` | 1 | [Hail/app/src/main/kotlin/com/aistra/hail/ui/home/PagerFragment.kt](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/home/PagerFragment.kt>) |
| `androidx.compose.material3.Typography` | 1 | [Hail/app/src/main/kotlin/com/aistra/hail/ui/theme/Type.kt](<E:/yhz/Projects/Hail/app/src/main/kotlin/com/aistra/hail/ui/theme/Type.kt>) |

**mihon**

| API 路径 | 文件数 | 代表来源 |
|---|---|---|
| `androidx.compose.material3.AlertDialog` | 27 | [mihon/app/src/main/java/eu/kanade/presentation/browse/components/BrowseSourceDialogs.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/components/BrowseSourceDialogs.kt>) |
| `androidx.compose.material3.Badge` | 3 | [mihon/app/src/main/java/eu/kanade/presentation/track/TrackInfoDialogHome.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/track/TrackInfoDialogHome.kt>) |
| `androidx.compose.material3.BadgedBox` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/track/TrackInfoDialogHome.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/track/TrackInfoDialogHome.kt>) |
| `androidx.compose.material3.Button` | 9 | [mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt>) |
| `androidx.compose.material3.ButtonDefaults` | 4 | [mihon/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTrackingScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTrackingScreen.kt>) |
| `androidx.compose.material3.Card` | 1 | [mihon/app/src/main/java/mihon/feature/support/SupportUsScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/mihon/feature/support/SupportUsScreen.kt>) |
| `androidx.compose.material3.CardColors` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/reader/ChapterTransition.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/reader/ChapterTransition.kt>) |
| `androidx.compose.material3.CardDefaults` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/reader/ChapterTransition.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/reader/ChapterTransition.kt>) |
| `androidx.compose.material3.Checkbox` | 6 | [mihon/app/src/main/java/eu/kanade/presentation/browse/SourcesFilterScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/SourcesFilterScreen.kt>) |
| `androidx.compose.material3.CircularProgressIndicator` | 11 | [mihon/app/src/main/java/eu/kanade/presentation/browse/components/BrowseSourceLoadingItem.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/components/BrowseSourceLoadingItem.kt>) |
| `androidx.compose.material3.ColorScheme` | 6 | [mihon/app/src/main/java/eu/kanade/presentation/theme/colorscheme/BaseColorScheme.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/theme/colorscheme/BaseColorScheme.kt>) |
| `androidx.compose.material3.DatePicker` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/track/TrackInfoDialogSelector.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/track/TrackInfoDialogSelector.kt>) |
| `androidx.compose.material3.DividerDefaults` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/more/settings/widget/AppThemePreferenceWidget.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/more/settings/widget/AppThemePreferenceWidget.kt>) |
| `androidx.compose.material3.DropdownMenu` | 2 | [mihon/app/src/main/java/eu/kanade/presentation/components/DropdownMenu.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/DropdownMenu.kt>) |
| `androidx.compose.material3.DropdownMenuItem` | 14 | [mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt>) |
| `androidx.compose.material3.ElevatedButton` | 1 | [mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Button.kt](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Button.kt>) |
| `androidx.compose.material3.ElevatedCard` | 4 | [mihon/app/src/main/java/eu/kanade/presentation/category/components/CategoryListItem.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/category/components/CategoryListItem.kt>) |
| `androidx.compose.material3.ExposedDropdownMenu` | 1 | [mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt>) |
| `androidx.compose.material3.ExposedDropdownMenuAnchorType` | 1 | [mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt>) |
| `androidx.compose.material3.ExposedDropdownMenuBox` | 1 | [mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt>) |
| `androidx.compose.material3.ExposedDropdownMenuDefaults` | 1 | [mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt>) |
| `androidx.compose.material3.FabPosition` | 1 | [mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Scaffold.kt](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Scaffold.kt>) |
| `androidx.compose.material3.FilledIconButton` | 2 | [mihon/app/src/main/java/eu/kanade/presentation/library/components/CommonMangaItem.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/library/components/CommonMangaItem.kt>) |
| `androidx.compose.material3.FilledIconToggleButton` | 1 | [mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/IconToggleButton.kt](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/IconToggleButton.kt>) |
| `androidx.compose.material3.FilledTonalButton` | 3 | [mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionDetailsScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionDetailsScreen.kt>) |
| `androidx.compose.material3.FilledTonalIconButton` | 2 | [mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionDetailsScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionDetailsScreen.kt>) |
| `androidx.compose.material3.FilterChip` | 7 | [mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchToolbar.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchToolbar.kt>) |
| `androidx.compose.material3.FilterChipDefaults` | 2 | [mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchToolbar.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchToolbar.kt>) |
| `androidx.compose.material3.HorizontalDivider` | 25 | [mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchToolbar.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchToolbar.kt>) |
| `androidx.compose.material3.Icon` | 68 | [mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchResultItems.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchResultItems.kt>) |
| `androidx.compose.material3.IconButton` | 23 | [mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchResultItems.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchResultItems.kt>) |
| `androidx.compose.material3.IconButtonColors` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt>) |
| `androidx.compose.material3.IconButtonDefaults` | 2 | [mihon/app/src/main/java/eu/kanade/presentation/library/components/CommonMangaItem.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/library/components/CommonMangaItem.kt>) |
| `androidx.compose.material3.LinearProgressIndicator` | 4 | [mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchToolbar.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchToolbar.kt>) |
| `androidx.compose.material3.ListItem` | 4 | [mihon/app/src/main/java/eu/kanade/presentation/more/onboarding/PermissionStep.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/more/onboarding/PermissionStep.kt>) |
| `androidx.compose.material3.ListItemDefaults` | 3 | [mihon/app/src/main/java/eu/kanade/presentation/more/onboarding/PermissionStep.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/more/onboarding/PermissionStep.kt>) |
| `androidx.compose.material3.LocalAbsoluteTonalElevation` | 1 | [mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Surface.kt](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Surface.kt>) |
| `androidx.compose.material3.LocalContentColor` | 18 | [mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt>) |
| `androidx.compose.material3.LocalMinimumInteractiveComponentSize` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt>) |
| `androidx.compose.material3.LocalTextStyle` | 7 | [mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt>) |
| `androidx.compose.material3.MaterialExpressiveTheme` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/theme/TachiyomiTheme.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/theme/TachiyomiTheme.kt>) |
| `androidx.compose.material3.MaterialTheme` | 138 | [mihon/app/src/main/java/eu/kanade/presentation/browse/components/BaseBrowseItem.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/components/BaseBrowseItem.kt>) |
| `androidx.compose.material3.MultiChoiceSegmentedButtonRow` | 2 | [mihon/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt>) |
| `androidx.compose.material3.NavigationBarDefaults` | 1 | [mihon/presentation-core/src/main/java/tachiyomi/presentation/core/screens/InfoScreen.kt](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/screens/InfoScreen.kt>) |
| `androidx.compose.material3.OutlinedButton` | 7 | [mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionDetailsScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionDetailsScreen.kt>) |
| `androidx.compose.material3.OutlinedCard` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/reader/ChapterTransition.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/reader/ChapterTransition.kt>) |
| `androidx.compose.material3.OutlinedSecureTextField` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTrackingScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTrackingScreen.kt>) |
| `androidx.compose.material3.OutlinedTextField` | 6 | [mihon/app/src/main/java/eu/kanade/presentation/category/components/CategoryDialogs.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/category/components/CategoryDialogs.kt>) |
| `androidx.compose.material3.PlainTooltip` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt>) |
| `androidx.compose.material3.PrimaryScrollableTabRow` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/library/components/LibraryTabs.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/library/components/LibraryTabs.kt>) |
| `androidx.compose.material3.PrimaryTabRow` | 2 | [mihon/app/src/main/java/eu/kanade/presentation/components/TabbedDialog.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/TabbedDialog.kt>) |
| `androidx.compose.material3.ProgressIndicatorDefaults` | 3 | [mihon/app/src/main/java/eu/kanade/presentation/manga/components/ChapterDownloadIndicator.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/manga/components/ChapterDownloadIndicator.kt>) |
| `androidx.compose.material3.ProvideTextStyle` | 5 | [mihon/app/src/main/java/eu/kanade/presentation/manga/components/MangaChapterListItem.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/manga/components/MangaChapterListItem.kt>) |
| `androidx.compose.material3.RadioButton` | 3 | [mihon/app/src/main/java/eu/kanade/presentation/more/settings/widget/ListPreferenceWidget.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/more/settings/widget/ListPreferenceWidget.kt>) |
| `androidx.compose.material3.Scaffold` | 2 | [mihon/app/src/main/java/mihon/feature/support/SupportUsScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/mihon/feature/support/SupportUsScreen.kt>) |
| `androidx.compose.material3.ScaffoldDefaults` | 1 | [mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Scaffold.kt](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Scaffold.kt>) |
| `androidx.compose.material3.SegmentedButton` | 2 | [mihon/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt>) |
| `androidx.compose.material3.SegmentedButtonDefaults` | 2 | [mihon/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt>) |
| `androidx.compose.material3.SelectableDates` | 2 | [mihon/app/src/main/java/eu/kanade/presentation/track/TrackInfoDialogSelector.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/track/TrackInfoDialogSelector.kt>) |
| `androidx.compose.material3.Slider` | 2 | [mihon/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt>) |
| `androidx.compose.material3.SliderColors` | 1 | [mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Slider.kt](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Slider.kt>) |
| `androidx.compose.material3.SliderDefaults` | 1 | [mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Slider.kt](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Slider.kt>) |
| `androidx.compose.material3.SliderState` | 2 | [mihon/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt>) |
| `androidx.compose.material3.SmallExtendedFloatingActionButton` | 6 | [mihon/app/src/main/java/eu/kanade/presentation/category/components/CategoryFloatingActionButton.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/category/components/CategoryFloatingActionButton.kt>) |
| `androidx.compose.material3.SnackbarDuration` | 2 | [mihon/app/src/main/java/eu/kanade/presentation/browse/BrowseSourceScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/BrowseSourceScreen.kt>) |
| `androidx.compose.material3.SnackbarHost` | 8 | [mihon/app/src/main/java/eu/kanade/presentation/components/TabbedScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/TabbedScreen.kt>) |
| `androidx.compose.material3.SnackbarHostState` | 14 | [mihon/app/src/main/java/eu/kanade/presentation/browse/BrowseSourceScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/BrowseSourceScreen.kt>) |
| `androidx.compose.material3.SnackbarResult` | 2 | [mihon/app/src/main/java/eu/kanade/presentation/browse/BrowseSourceScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/BrowseSourceScreen.kt>) |
| `androidx.compose.material3.SplitButtonDefaults` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt>) |
| `androidx.compose.material3.SplitButtonLayout` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt>) |
| `androidx.compose.material3.SuggestionChip` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt>) |
| `androidx.compose.material3.Surface` | 19 | [mihon/app/src/main/java/eu/kanade/presentation/components/EmptyScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/EmptyScreen.kt>) |
| `androidx.compose.material3.Switch` | 6 | [mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionDetailsScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/ExtensionDetailsScreen.kt>) |
| `androidx.compose.material3.Tab` | 3 | [mihon/app/src/main/java/eu/kanade/presentation/components/TabbedDialog.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/TabbedDialog.kt>) |
| `androidx.compose.material3.Text` | 123 | [mihon/app/src/main/java/eu/kanade/presentation/browse/components/BaseSourceItem.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/components/BaseSourceItem.kt>) |
| `androidx.compose.material3.TextButton` | 31 | [mihon/app/src/main/java/eu/kanade/presentation/browse/components/BrowseSourceDialogs.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/components/BrowseSourceDialogs.kt>) |
| `androidx.compose.material3.TextFieldDefaults` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt>) |
| `androidx.compose.material3.TooltipAnchorPosition` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt>) |
| `androidx.compose.material3.TooltipBox` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt>) |
| `androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt>) |
| `androidx.compose.material3.TopAppBar` | 3 | [mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt>) |
| `androidx.compose.material3.TopAppBarDefaults` | 4 | [mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt>) |
| `androidx.compose.material3.TopAppBarScrollBehavior` | 6 | [mihon/app/src/main/java/eu/kanade/presentation/browse/components/BrowseSourceToolbar.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/components/BrowseSourceToolbar.kt>) |
| `androidx.compose.material3.TriStateCheckbox` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/category/components/CategoryDialogs.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/category/components/CategoryDialogs.kt>) |
| `androidx.compose.material3.Typography` | 2 | [mihon/app/src/main/java/eu/kanade/presentation/manga/DuplicateMangaDialog.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/manga/DuplicateMangaDialog.kt>) |
| `androidx.compose.material3.VerticalDivider` | 4 | [mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchToolbar.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchToolbar.kt>) |
| `androidx.compose.material3.VerticalSlider` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt>) |
| `androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults` | 1 | [mihon/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt>) |
| `androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem` | 1 | [mihon/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt>) |
| `androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold` | 1 | [mihon/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt>) |
| `androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType` | 1 | [mihon/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt>) |
| `androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState` | 1 | [mihon/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt>) |
| `androidx.compose.material3.animateFloatingActionButton` | 4 | [mihon/app/src/main/java/eu/kanade/presentation/manga/MangaScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/manga/MangaScreen.kt>) |
| `androidx.compose.material3.contentColorFor` | 4 | [mihon/app/src/main/java/eu/kanade/presentation/library/components/CommonMangaItem.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/library/components/CommonMangaItem.kt>) |
| `androidx.compose.material3.darkColorScheme` | 15 | [mihon/app/src/main/java/eu/kanade/presentation/theme/colorscheme/CatppuccinColorScheme.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/theme/colorscheme/CatppuccinColorScheme.kt>) |
| `androidx.compose.material3.dynamicDarkColorScheme` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/theme/colorscheme/MonetColorScheme.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/theme/colorscheme/MonetColorScheme.kt>) |
| `androidx.compose.material3.dynamicLightColorScheme` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/theme/colorscheme/MonetColorScheme.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/theme/colorscheme/MonetColorScheme.kt>) |
| `androidx.compose.material3.lightColorScheme` | 15 | [mihon/app/src/main/java/eu/kanade/presentation/theme/colorscheme/CatppuccinColorScheme.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/theme/colorscheme/CatppuccinColorScheme.kt>) |
| `androidx.compose.material3.minimumInteractiveComponentSize` | 4 | [mihon/app/src/main/java/eu/kanade/presentation/manga/components/ScanlatorFilterDialog.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/manga/components/ScanlatorFilterDialog.kt>) |
| `androidx.compose.material3.pulltorefresh.PullToRefreshDefaults` | 1 | [mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/PullRefresh.kt](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/PullRefresh.kt>) |
| `androidx.compose.material3.pulltorefresh.pullToRefresh` | 1 | [mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/PullRefresh.kt](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/PullRefresh.kt>) |
| `androidx.compose.material3.pulltorefresh.rememberPullToRefreshState` | 1 | [mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/PullRefresh.kt](<E:/yhz/Projects/mihon/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/PullRefresh.kt>) |
| `androidx.compose.material3.rememberDatePickerState` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/track/TrackInfoDialogSelector.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/track/TrackInfoDialogSelector.kt>) |
| `androidx.compose.material3.rememberSliderState` | 2 | [mihon/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt>) |
| `androidx.compose.material3.rememberTooltipState` | 1 | [mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt>) |
| `androidx.compose.material3.rememberTopAppBarState` | 3 | [mihon/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsMainScreen.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsMainScreen.kt>) |
| `androidx.compose.material3.ripple` | 3 | [mihon/app/src/main/java/eu/kanade/presentation/manga/components/ChapterDownloadIndicator.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/manga/components/ChapterDownloadIndicator.kt>) |
| `androidx.compose.material3.surfaceColorAtElevation` | 5 | [mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt](<E:/yhz/Projects/mihon/app/src/main/java/eu/kanade/presentation/components/AppBar.kt>) |

### 10.2 关键辅助 API 的阅读方式

| 类别 | API 示例 | 阅读重点 |
|---|---|---|
| 主题 | MaterialTheme、MaterialExpressiveTheme、Typography、ColorScheme | 样式来源和层级作用域 |
| 系统色 | dynamicLightColorScheme、dynamicDarkColorScheme | Android 版本和回退方案 |
| 主题内容 | LocalContentColor、LocalTextStyle、ProvideTextStyle | 父容器如何影响子内容 |
| 控件默认值 | ButtonDefaults、CardDefaults、SliderDefaults、TextFieldDefaults 等 | 颜色、形状、尺寸与默认交互策略 |
| 输入/日期状态 | SliderState、rememberSliderState、rememberDatePickerState、SelectableDates | 状态持有、合法值和允许日期 |
| 瞬时反馈 | SnackbarHostState、SnackbarDuration、SnackbarResult | 协程调用、展示时长与操作结果 |
| 浮层状态 | rememberModalBottomSheetState、rememberTooltipState | 显示/隐藏及生命周期 |
| 栏滚动 | TopAppBarScrollBehavior、rememberTopAppBarState | nestedScroll 与内容滚动联动 |
| 交互与高度 | minimumInteractiveComponentSize、ripple、surfaceColorAtElevation | 点击目标、反馈和表面层级 |
| FAB 动效 | animateFloatingActionButton | 滚动/状态变化时的显隐或尺寸过渡 |
| 下拉刷新 | pullToRefresh、rememberPullToRefreshState、PullToRefreshDefaults | 手势、刷新状态和指示器组合 |

Hail 通配导入使用的辅助 API 包括 `rememberModalBottomSheetState`、`lightColorScheme`、`darkColorScheme` 和动态配色函数等；显式 import 索引本身不能覆盖通配导入的所有符号。第 4 节以实际调用补充了可视组件，但本报告不是 Kotlin 编译器级符号解析结果。`ElevatedButton` 在 Mihon 中存在导入和文档引用，本次没有定位到实际构造调用，因此只收录在索引，不编造项目使用示例。

### 10.3 分析与验证范围

- 已检查两个项目根目录、settings/build Gradle、版本目录、关键主题、核心业务类与组件调用。
- 依赖表完整覆盖两个 `libs.versions.toml` 的 libraries/plugins；引用定位包含 bundles 的静态展开。
- 组件扫描覆盖 `.kt` 中显式 Material 3 导入、Hail 的通配导入、Material View 的导入与 XML 类标签，并包含辅助 API 索引。
- 扫描次数是文本匹配数，不是运行时组件数量；同名封装、局部作用域和扩展函数仍需结合源码判断。
- 代码来源链接指向本机绝对路径和起始行；源码变更后行号可能失效。
- 未构建 APK、未启动应用、未进行截图验证，因此视觉结论来自主题/布局/页面组织，运行效果以实际构建为准。
- 本文只新增文档，不修改 Hail 或 Mihon 源码。

