@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deepcook.app.feature.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import com.deepcook.app.feature.cooking.TtsVoiceOption
import com.deepcook.app.feature.cooking.installedChineseVoices
import com.deepcook.app.feature.cooking.openSystemTtsSettings
import com.deepcook.app.feature.cooking.openVoiceDownload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

@Composable
fun SettingsScreen(
    section: String,
    preferences: UserPreferences,
    favorites: Int,
    recent: Int,
    notes: Int,
    recipes: List<Recipe>,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onPreferences: ((UserPreferences) -> UserPreferences) -> Unit,
    onClearRecent: () -> Unit,
    onClearFavorites: () -> Unit,
    onReset: () -> Unit,
    onClearAiOverlay: () -> Unit,
    onNotes: () -> Unit,
) {
    val context = LocalContext.current
    val sourceEntries by produceState<List<String>>(emptyList(), section) {
        if (section == "photo_sources") {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val manifest = context.assets.open("catalog_sources.json").bufferedReader().use { it.readText() }
                    Json.parseToJsonElement(manifest).jsonObject.getValue("recipes").jsonArray.map { entry ->
                        entry.jsonObject.let { row ->
                            listOf(
                                "recipeId", "repository", "commit", "path", "license", "url",
                                "imagePath", "imageUrl", "imageLicense", "originalSha256",
                            )
                                .joinToString("\n") { key -> "$key: ${row[key]?.jsonPrimitive?.contentOrNull ?: "无"}" }
                        }
                    }
                }.getOrElse { listOf("暂时无法读取来源清单，请返回重试。") }
            }
        }
    }
    val title = when (section) {
        "appearance" -> "外观"
        "reading" -> "阅读"
        "data" -> "本地数据"
        "sources" -> "内容与来源"
        "about" -> "关于 DeepCook"
        "licenses" -> "开源许可"
        "photo_sources" -> "菜谱与图片来源清单"
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
                                "暖黄厨房使用淡黄色底色与焦糖金棕强调色。系统动态色跟随壁纸配色，支持 Android 12 及以上系统。",
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
                                "在菜谱详情与烹饪模式中显示步骤配图。",
                                Icons.Rounded.Image,
                                preferences.showStepImages,
                            ) { value ->
                                onPreferences { it.copy(showStepImages = value) }
                            }
                            PreferenceGroupHeader("专注阅读")
                            SwitchPreference(
                                "阅读时保持亮屏",
                                "仅在烹饪模式页处于前台时生效。",
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
                        PreferenceGroupHeader("收录内容")
                        PreferenceRow(
                            "${recipes.size} 道菜谱 · ${recipes.count { it.id.startsWith("world-") }} 道国际菜中文译文",
                            "HowToCook 中文原文、Based Cooking 中文译文，以及 ${recipes.count {
                                it.collection == "DeepCook 自编菜谱"
                            }} 道项目自编菜谱。用时、难度等未提供或口径不明确的参数显示“无”。",
                            Icons.AutoMirrored.Rounded.MenuBook,
                        )
                        PreferenceRow(
                            "图片与来源",
                            "来源菜谱按 HowToCook、Based Cooking 的 Unlicense 声明使用。自编菜谱的配图来自 FoodiesFeed（CC0 等效许可）和 HowToCook。所有配图随包提供；未找到图片时使用餐具占位。具体授权、固定版本与校验值见清单。",
                            Icons.Rounded.PhotoLibrary,
                        )
                        PreferenceRow("原始份量", "默认展示菜谱原始份量；有明确份量时可按人数换算材料用量，调味请按口味调整。", Icons.Rounded.Restaurant)
                        PreferenceRow("菜谱与配图来源清单", "查看每道菜谱、图片的来源路径与固定版本。", Icons.Rounded.Description, onClick = {
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
                        PreferenceRow("内容与来源", "收录菜谱及图片使用说明", Icons.Rounded.Info, onClick = { onNavigate("sources") })
                    }
                    "licenses" -> item { LicenseContent() }
                    "photo_sources" -> {
                        if (sourceEntries.isEmpty()) item { Text("正在读取…", Modifier.padding(16.dp)) }
                        items(sourceEntries.size) { index ->
                            SelectionContainer {
                                Text(
                                    sourceEntries[index],
                                    Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
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
                        PreferenceGroupHeader("AI 与语音")
                        SwitchPreference(
                            "语音输入",
                            "做菜时可点一下说话。关闭后只用键盘输入，AI 仍然可用。",
                            Icons.Rounded.Mic,
                            preferences.voiceInputEnabled,
                        ) { value ->
                            onPreferences { it.copy(voiceInputEnabled = value) }
                        }
                        SwitchPreference(
                            "朗读回答",
                            "用系统语音把回答读出来，语速 1.3 倍；关掉后只看屏幕。",
                            Icons.Rounded.GraphicEq,
                            preferences.speakAnswers,
                        ) { value ->
                            onPreferences { it.copy(speakAnswers = value) }
                        }
                        var showVoices by remember { mutableStateOf(false) }
                        SwitchPreference(
                            "唤醒词「小厨小厨」",
                            "做菜页常听这四个字，听到就直接开始录音提问。离线识别、不要网；耗电会多一些。",
                            Icons.Rounded.SettingsVoice,
                            preferences.wakeWordEnabled,
                        ) { value ->
                            onPreferences { it.copy(wakeWordEnabled = value) }
                        }
                        PreferenceRow(
                            "朗读声音",
                            preferences.ttsVoiceLabel ?: preferences.ttsVoice ?: "系统默认",
                            Icons.Rounded.RecordVoiceOver,
                            onClick = { showVoices = true },
                        )
                        if (showVoices) {
                            VoicePickerDialog(
                                current = preferences.ttsVoice,
                                onSelect = { name, label ->
                                    onPreferences { it.copy(ttsVoice = name, ttsVoiceLabel = label) }
                                    showVoices = false
                                },
                                onDismiss = { showVoices = false },
                            )
                        }
                        PreferenceRow(
                            "请求去向",
                            "问题、当前这道菜的信息和口味备注会发到智谱（bigmodel.cn）的模型；新增菜谱时补全资料只发送菜名。录音与朗读都在本机完成，音频不上传。",
                            Icons.Rounded.Cloud,
                        )
                        var confirmClear by remember { mutableStateOf(false) }
                        PreferenceRow(
                            "AI 补充数据",
                            "模型按菜补的用时、难度与用量，只影响回答，不改菜谱。点按清空。",
                            Icons.Rounded.AutoAwesome,
                            onClick = { confirmClear = true },
                        )
                        PreferenceRow(
                            "口味备注",
                            if (notes == 0) {
                                "没有备注。做完一道菜说一句，下次做时会提醒你。"
                            } else {
                                "$notes 条。按菜记录，做菜页顶部会提示；点按可查看、修改或删除。"
                            },
                            Icons.Rounded.EditNote,
                            onClick = onNotes,
                        )
                        if (confirmClear) {
                            AlertDialog(
                                onDismissRequest = { confirmClear = false },
                                title = { Text("清空 AI 补充数据？") },
                                text = { Text("已缓存的补充信息会被删掉，下次做菜时会重新联网补全。菜谱本身不会改动。") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        confirmClear = false
                                        onClearAiOverlay()
                                    }) { Text("清空") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { confirmClear = false }) { Text("取消") }
                                },
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        PreferenceGroupHeader("数据与说明")
                        PreferenceRow("本地数据", "$favorites 道收藏 · $recent 条浏览记录", Icons.Rounded.Storage, onClick = {
                            onNavigate("data")
                        })
                        PreferenceRow("内容与来源", "收录菜谱、图片与授权说明", Icons.Rounded.Description, onClick = {
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
    PreferenceRow("云端同步", "登录后同步昵称、头像、收藏、菜谱、偏好和备注。内置菜谱仍可离线翻阅。", Icons.Rounded.PhoneAndroid)
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
    PreferenceGroupHeader("HowToCook 菜谱与图片 · Unlicense")
    AssetText("HOWTOCOOK-LICENSE.txt")
    PreferenceGroupHeader("Based Cooking 菜谱与图片 · Unlicense")
    AssetText("BASED-COOKING-LICENSE.txt")
    PreferenceGroupHeader("FoodiesFeed 菜谱配图")
    PreferenceRow(
        "CC0 等效许可",
        "允许复制、修改和随应用分发；不将图片作为图库或独立素材售卖。授权：https://www.foodiesfeed.com/license",
        Icons.Rounded.PhotoLibrary,
    )
    PreferenceGroupHeader("语音与唤醒词")
    PreferenceRow(
        "sherpa-onnx · Apache License 2.0",
        "离线关键词识别引擎（k2-fsa/sherpa-onnx）。中文 KWS 模型约 5 MB 随包提供，只用于识别「小厨小厨」，音频不出设备。",
        Icons.Rounded.RecordVoiceOver,
    )
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

/**
 * Lists the voices the system engine already has and links to the engine's own download page.
 * Voice data is installed by the engine, so the APK never carries it.
 */
@Composable
private fun VoicePickerDialog(
    current: String?,
    onSelect: (name: String?, label: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var options by remember { mutableStateOf<List<TtsVoiceOption>?>(null) }
    LaunchedEffect(Unit) {
        options = withContext(Dispatchers.IO) {
            withTimeoutOrNull(3_000) { installedChineseVoices(context) } ?: emptyList()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("朗读声音") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "声音来自系统语音引擎；语音包在引擎里下载，不占安装包。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                VoiceChoice("系统默认", current == null) { onSelect(null, null) }
                when (val list = options) {
                    null -> Text("正在读取…", style = MaterialTheme.typography.bodySmall)
                    else -> {
                        if (list.isEmpty()) {
                            Text(
                                "当前引擎没有可切换的中文语音，可以用下面的按钮下载。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        list.forEach { option ->
                            VoiceChoice(option.label, current == option.name) {
                                onSelect(option.name, option.label)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        if (!openVoiceDownload(context)) {
                            Toast.makeText(context, "打不开语音下载页，试试系统语音设置", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("下载语音包") }
                    TextButton(onClick = {
                        if (!openSystemTtsSettings(context)) {
                            Toast.makeText(context, "打不开系统语音设置", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("系统语音设置") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun VoiceChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
