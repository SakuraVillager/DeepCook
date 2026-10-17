@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deepcook.app.feature.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deepcook.app.BuildConfig
import com.deepcook.app.core.designsystem.*
import com.deepcook.app.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    section: String,
    preferences: UserPreferences,
    favorites: Int,
    recent: Int,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onPreferences: ((UserPreferences) -> UserPreferences) -> Unit,
    onClearRecent: () -> Unit,
    onClearFavorites: () -> Unit,
    onReset: () -> Unit,
) {
    val title = when (section) {
        "appearance" -> "外观"
        "reading" -> "阅读"
        "data" -> "本地数据"
        "sources" -> "内容与来源"
        "about" -> "关于 DeepCook"
        "licenses" -> "开源许可"
        "photo_sources" -> "图片来源清单"
        else -> "设置"
    }
    Scaffold(topBar = { PageTitle(title, onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.dp)) {
                when (section) {
                    "appearance" -> {
                        item {
                            PreferenceGroupHeader("明暗模式")
                            SingleChoiceSegmentedButtonRow(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                ThemeMode.entries.forEachIndexed { index, mode ->
                                    SegmentedButton(selected = preferences.themeMode == mode, onClick = {
                                        onPreferences { it.copy(themeMode = mode) }
                                    }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(mode.label) }
                                }
                            }
                            PreferenceGroupHeader("主题配色")
                            LazyRow(
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                item {
                                    ThemePreview(ColorSource.KITCHEN, preferences) {
                                        onPreferences { it.copy(colorSource = ColorSource.KITCHEN) }
                                    }
                                }
                                if (Build.VERSION.SDK_INT >=
                                    31
                                ) {
                                    item {
                                        ThemePreview(ColorSource.DYNAMIC, preferences) {
                                            onPreferences { it.copy(colorSource = ColorSource.DYNAMIC) }
                                        }
                                    }
                                }
                            }
                            PreferenceRow(
                                "即时预览",
                                "暖黄厨房使用奶油底色与焦糖金棕。系统动态色会跟随壁纸配色，支持 Android 12 及以上系统。",
                                Icons.Rounded.Palette,
                            )
                        }
                    }
                    "reading" -> {
                        item {
                            PreferenceGroupHeader("文字与图片")
                            var chooseSize by remember { mutableStateOf(false) }
                            PreferenceRow("正文字号", preferences.readingSize.label, Icons.Rounded.FormatSize, onClick = {
                                chooseSize =
                                    true
                            })
                            if (chooseSize) {
                                AlertDialog(
                                    onDismissRequest = { chooseSize = false },
                                    title = { Text("正文字号") },
                                    text = {
                                        Column {
                                            ReadingSize.entries.forEach { size ->
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(selected = preferences.readingSize == size, onClick = {
                                                        onPreferences { it.copy(readingSize = size) }
                                                        chooseSize =
                                                            false
                                                    })
                                                    TextButton(onClick = {
                                                        onPreferences { it.copy(readingSize = size) }
                                                        chooseSize =
                                                            false
                                                    }) { Text(size.label) }
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = { TextButton(onClick = { chooseSize = false }) { Text("关闭") } },
                                )
                            }
                            SwitchPreference(
                                "显示步骤图片",
                                "在菜谱详情与分步阅读中显示步骤配图。",
                                Icons.Rounded.Image,
                                preferences.showStepImages,
                            ) { value ->
                                onPreferences { it.copy(showStepImages = value) }
                            }
                            PreferenceGroupHeader("专注阅读")
                            SwitchPreference(
                                "阅读时保持亮屏",
                                "仅在分步阅读页处于前台时生效。",
                                Icons.Rounded.LightMode,
                                preferences.keepScreenOn,
                            ) { value ->
                                onPreferences { it.copy(keepScreenOn = value) }
                            }
                            PreferenceRow("跟随系统文字缩放", "所选字号会叠加系统字体大小，保留无障碍阅读设置。", Icons.Rounded.AccessibilityNew)
                        }
                    }
                    "data" -> item {
                        DataPreferences(favorites, recent, onClearRecent, onClearFavorites, onReset)
                    }
                    "sources" -> item {
                        PreferenceGroupHeader("示例内容")
                        PreferenceRow(
                            "112 道菜谱 · 7 个地域",
                            "按中式、日式、韩式、美式、欧式、东南亚与其他 7 个地域分类，" +
                                "首页与搜索页的分类入口都支持按地域浏览。" +
                                "菜谱文字为 DeepCook 自编的演示内容，参考公开菜谱资料整理，未经独立试做验证。" +
                                "用时为内容中的参考值，请结合食材大小、锅具和实际状态判断。",
                            Icons.AutoMirrored.Rounded.MenuBook,
                        )
                        PreferenceRow(
                            "图片与来源",
                            "最初 12 道与 4 道扩充菜谱的配图来自 Anduin2017/HowToCook 固定版本，按其 Unlicense 声明使用；" +
                                "另有 22 道扩充菜谱使用 FoodiesFeed 的 CC0 照片，可商用、免署名。" +
                                "其余菜谱没有找到授权明确且对应该菜品的照片，界面显示占位图；具体来源保存在随包清单中。",
                            Icons.Rounded.PhotoLibrary,
                        )
                        PreferenceRow("原始份量", "材料用量仅展示菜谱原始份量。本版不进行人数换算，也不修改配方。", Icons.Rounded.Restaurant)
                        PreferenceRow(
                            "地域标签",
                            "卡片与详情显示菜品地域；分类入口把地域列在菜式分类之前，选中即按地域筛选。",
                            Icons.Rounded.Public,
                        )
                        PreferenceRow("配图授权清单", "查看每张图片的来源路径与固定版本。", Icons.Rounded.Description, onClick = {
                            onNavigate("photo_sources")
                        })
                    }
                    "about" -> item {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ChefMark(Modifier.size(88.dp))
                            Text("DeepCook", style = MaterialTheme.typography.headlineLarge)
                            Text("版本 ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("收藏好菜，慢慢下厨。", style = MaterialTheme.typography.titleMedium)
                        }
                        PreferenceRow("属于日常的小厨房", "离线浏览菜谱、收藏喜欢的味道，以更舒适的方式阅读每一个步骤。", Icons.Rounded.SoupKitchen)
                        PreferenceRow("开源许可", "组件来源与第三方软件说明", Icons.Rounded.Code, onClick = { onNavigate("licenses") })
                        PreferenceRow("内容与来源", "示例菜谱及图片使用说明", Icons.Rounded.Info, onClick = { onNavigate("sources") })
                    }
                    "licenses" -> item { LicenseContent() }
                    "photo_sources" -> item { AssetText("photo_sources.json") }
                    else -> item {
                        PreferenceGroupHeader("使用偏好")
                        PreferenceRow(
                            "外观",
                            "${preferences.themeMode.label} · ${preferences.colorSource.label}",
                            Icons.Rounded.Palette,
                            onClick = {
                                onNavigate("appearance")
                            },
                        )
                        PreferenceRow("阅读", "字号、步骤图片、保持亮屏", Icons.AutoMirrored.Rounded.MenuBook, onClick = {
                            onNavigate("reading")
                        })
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        PreferenceGroupHeader("数据与说明")
                        PreferenceRow("本地数据", "$favorites 道收藏 · $recent 条浏览记录", Icons.Rounded.Storage, onClick = {
                            onNavigate("data")
                        })
                        PreferenceRow("内容与来源", "示例菜谱、图片与授权说明", Icons.Rounded.Description, onClick = {
                            onNavigate("sources")
                        })
                        PreferenceRow(
                            "关于 DeepCook",
                            "版本 ${BuildConfig.VERSION_NAME} · 开源许可",
                            Icons.Rounded.Info,
                            onClick = {
                                onNavigate("about")
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePreview(source: ColorSource, preferences: UserPreferences, onClick: () -> Unit) {
    val selected = preferences.colorSource == source
    Column(
        Modifier.width(144.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DeepCookTheme(preferences.copy(colorSource = source)) {
            OutlinedCard(
                onClick = onClick,
                border = BorderStroke(
                    if (selected) 3.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().height(220.dp).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChefMark(Modifier.size(24.dp))
                        Spacer(Modifier.weight(1f))
                        if (selected) Icon(Icons.Rounded.CheckCircle, "已选择", tint = MaterialTheme.colorScheme.primary)
                    }
                    Box(
                        Modifier.fillMaxWidth().height(
                            18.dp,
                        ).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp)),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(2) {
                            RecipeImage(
                                "photos/${if (it == 0) "braised-pork" else "three-treasures"}.jpg",
                                Modifier.weight(1f).height(68.dp).clip(RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth(
                            .7f,
                        ).height(
                            8.dp,
                        ).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Icon(Icons.Rounded.Home, null, tint = MaterialTheme.colorScheme.primary)
                        Icon(Icons.Rounded.StarBorder, null)
                        Icon(Icons.Rounded.PersonOutline, null)
                    }
                }
            }
        }
        Text(source.label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DataPreferences(
    favorites: Int,
    recent: Int,
    onClearRecent: () -> Unit,
    onClearFavorites: () -> Unit,
    onReset: () -> Unit,
) {
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    PreferenceGroupHeader("当前设备")
    PreferenceRow("清除最近浏览", "共 $recent 条，仅清除浏览记录。", Icons.Rounded.History, onClick = { confirmation = "recent" })
    PreferenceRow("清空收藏", "共 $favorites 道，仅删除收藏关系。", Icons.Rounded.StarOutline, onClick = {
        confirmation = "favorites"
    })
    PreferenceRow("恢复默认设置", "重置外观和阅读偏好，保留收藏与浏览记录。", Icons.Rounded.RestartAlt, onClick = {
        confirmation = "preferences"
    })
    PreferenceRow("本地保存", "本版没有账号与云端同步。卸载或清除应用数据后无法恢复。", Icons.Rounded.PhoneAndroid)
    if (confirmation != null) {
        val title = when (confirmation) {
            "recent" -> "清除最近浏览？"
            "favorites" -> "清空收藏？"
            else -> "恢复默认设置？"
        }
        val text = when (confirmation) {
            "recent" -> "将移除全部 $recent 条浏览记录。收藏和设置不受影响。"
            "favorites" -> "将移除全部 $favorites 道收藏。菜谱内容、浏览记录和设置不受影响。"
            else -> "外观和阅读选项恢复默认值，收藏与浏览记录保留。"
        }
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    when (confirmation) {
                        "recent" -> onClearRecent()
                        "favorites" -> onClearFavorites()
                        else -> onReset()
                    }
                    confirmation = null
                }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun LicenseContent() {
    PreferenceGroupHeader("界面组件")
    PreferenceRow("Mihon · Apache License 2.0", "设置行布局与主题预览参考 Mihon 组件，已为 DeepCook 修改。", Icons.Rounded.Code)
    AssetText("MIHON-LICENSE.txt")
    PreferenceGroupHeader("图片")
    AssetText("HOWTOCOOK-LICENSE.txt")
    PreferenceGroupHeader("第三方软件")
    PreferenceRow(
        "AndroidX / Kotlin / Coroutines / Serialization / Coil / Material Motion Compose",
        "Apache License 2.0。完整依赖版本见项目版本目录；Apache License 全文同上。",
        Icons.Rounded.Code,
    )
}

@Composable
private fun AssetText(name: String) {
    val context = LocalContext.current
    val text by produceState("正在读取…", name) {
        value = withContext(Dispatchers.IO) {
            try {
                context.assets.open(name).bufferedReader().use { it.readText() }
            } catch (
                _: java.io.IOException,
            ) {
                "暂时无法读取说明文件。"
            }
        }
    }
    SelectionContainer { Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
}
