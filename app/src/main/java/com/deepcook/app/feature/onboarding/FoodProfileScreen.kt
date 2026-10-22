package com.deepcook.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepcook.app.core.designsystem.EmptyState
import com.deepcook.app.core.model.FoodChoices

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodProfileScreen(vm: FoodProfileViewModel, state: FoodProfileUiState, onCancel: () -> Unit = {}) {
    val titles = listOf("你喜欢吃些什么？", "有什么需要避开？", "平时怎么下厨？", "想收获些什么？")
    val descriptions = listOf("选一点你喜欢的，让找菜更合口味。", "忌口和不喜欢的食材，可以分开告诉我们。", "按你的节奏，找到更容易上手的菜。", "选几个小愿望，从今天的一餐开始。")
    val profile = state.profile
    val largeText = LocalDensity.current.fontScale > 1.3f
    BackHandler(enabled = state.saving) {}
    BackHandler(enabled = state.page > 0 && !state.saving) { vm.page(state.page - 1) }
    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Box(
                Modifier.fillMaxWidth().windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.page > 0 || vm.editing) {
                        IconButton(enabled = !state.saving, onClick = {
                            if (state.page > 0) vm.page(state.page - 1) else onCancel()
                        }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "上一步") }
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }
                    Row(
                        Modifier.weight(1f).semantics {
                            contentDescription = "第 ${state.page + 1} 页，共 4 页"
                            progressBarRangeInfo = ProgressBarRangeInfo((state.page + 1).toFloat(), 0f..4f, 3)
                        },
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        repeat(4) { index ->
                            Surface(
                                Modifier.weight(1f).height(5.dp),
                                shape = RoundedCornerShape(3.dp),
                                color = if (index <=
                                    state.page
                                ) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)
                                },
                            ) {}
                        }
                    }
                    TextButton(enabled = (vm.editing || state.ready) && !state.saving, onClick = {
                        if (vm.editing) onCancel() else vm.finish()
                    }) { Text(if (vm.editing) "取消" else "跳过设置") }
                }
            }
        },
        bottomBar = {
            if (state.ready) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(
                        Modifier.fillMaxWidth().windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            if (state.error != null) {
                                Text(
                                    state.error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TextButton(enabled = !state.saving, onClick = {
                                    if (state.page < 3) vm.page(state.page + 1) else vm.finish()
                                }) { Text("暂不填写") }
                                Button(
                                    enabled = !state.saving,
                                    onClick = { if (state.page < 3) vm.page(state.page + 1) else vm.finish() },
                                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("food-next"),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    if (state.saving) {
                                        CircularProgressIndicator(
                                            Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(
                                        if (state.saving) {
                                            "保存中"
                                        } else if (state.page <
                                            3
                                        ) {
                                            "下一步"
                                        } else if (vm.editing) {
                                            "保存偏好"
                                        } else {
                                            "开始找菜"
                                        },
                                    )
                                    if (!state.saving && !largeText) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
                                    }
                                }
                            }
                            Text(
                                "随时可在「我的 · 饮食偏好」中修改",
                                Modifier.fillMaxWidth().padding(top = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (!state.ready) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState("暂时无法读取饮食偏好", state.error.orEmpty(), "重试", vm::load)
            }
        } else {
            Box(
                Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                AnimatedContent(
                    targetState = state.page,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { direction * it / 12 }) togetherWith
                            (fadeOut(tween(100)) + slideOutHorizontally(tween(220)) { -direction * it / 12 })
                    },
                    label = "food-question",
                ) { page ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(
                            rememberScrollState(),
                        ).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    if (vm.editing) "我的饮食偏好 · ${page + 1}/4" else "认识你的口味 · ${page + 1}/4",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(titles[page], style = MaterialTheme.typography.headlineMedium)
                                Text(
                                    descriptions[page],
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            KitchenFriends(page, Modifier.size(width = 96.dp, height = 90.dp))
                        }
                        when (page) {
                            0 -> {
                                Question("喜欢哪些菜系？", "可多选") {
                                    Chips(FoodChoices.cuisines, profile.cuisines, Icons.Rounded.Restaurant) { value ->
                                        vm.change { it.copy(cuisines = it.cuisines.toggled(value)) }
                                    }
                                }
                                Question("平时吃多辣？", "选一项，也可以不选") {
                                    Chips(
                                        FoodChoices.spices,
                                        setOfNotNull(profile.spice),
                                        Icons.Rounded.LocalFireDepartment,
                                    ) { value ->
                                        vm.change { it.copy(spice = if (value == it.spice) null else value) }
                                    }
                                }
                                Question("偏爱哪些味道？", "可多选") {
                                    Chips(FoodChoices.tastes, profile.tastes, Icons.Rounded.Spa) { value ->
                                        vm.change { it.copy(tastes = it.tastes.exclusiveToggle(value, "都喜欢")) }
                                    }
                                }
                            }
                            1 -> {
                                Question("有固定的饮食方式吗？") {
                                    Chips(FoodChoices.diets, setOfNotNull(profile.diet), Icons.Rounded.Eco) { value ->
                                        vm.change { it.copy(diet = if (value == it.diet) null else value) }
                                    }
                                }
                                Question("有过敏或不耐受的食材吗？", "与普通忌口分开记录") {
                                    Chips(
                                        FoodChoices.allergens +
                                            profile.allergens.orEmpty().filterNot { it in FoodChoices.allergens } +
                                            "没有已知项",
                                        profile.allergens?.ifEmpty {
                                            setOf("没有已知项")
                                        }.orEmpty(),
                                        Icons.Rounded.Shield,
                                    ) { value ->
                                        vm.change {
                                            it.copy(
                                                allergens = if (value == "没有已知项") {
                                                    if (it.allergens == emptySet<String>()) null else emptySet()
                                                } else {
                                                    it.allergens.orEmpty().toggled(
                                                        value,
                                                    ).takeIf(Set<String>::isNotEmpty)
                                                },
                                            )
                                        }
                                    }
                                    AddIngredient("allergen", "添加需要避开的食材", profile.allergens.orEmpty(), { value ->
                                        vm.change {
                                            it.copy(
                                                allergens =
                                                it.allergens.orEmpty() + value,
                                            )
                                        }
                                    })
                                }
                                Question("有不喜欢或不吃的食材吗？", "可多选，也可以自己添加") {
                                    Chips(
                                        FoodChoices.avoided + profile.avoided.filterNot {
                                            it in FoodChoices.avoided
                                        },
                                        profile.avoided,
                                        Icons.Rounded.Grass,
                                    ) { value ->
                                        vm.change { it.copy(avoided = it.avoided.toggled(value)) }
                                    }
                                    AddIngredient("avoided", "添加其他忌口", profile.avoided, { value ->
                                        vm.change {
                                            it.copy(
                                                avoided =
                                                it.avoided + value,
                                            )
                                        }
                                    })
                                }
                                Text(
                                    "推荐会按已记录的材料避开相关菜谱，材料信息可能不完整，下厨前请核对实际食材及调料。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            2 -> {
                                Question("你的下厨经验？") {
                                    FoodChoices.experience.forEachIndexed { index, value ->
                                        ChoiceRow(
                                            value,
                                            listOf("希望步骤清楚，操作简单", "熟悉基础做法，想换些花样", "愿意尝试更复杂的做法")[index],
                                            listOf(
                                                Icons.Rounded.EggAlt,
                                                Icons.Rounded.SoupKitchen,
                                                Icons.Rounded.Restaurant,
                                            )[index],
                                            profile.experience == value,
                                            single = true,
                                        ) {
                                            vm.change {
                                                it.copy(experience = if (value == it.experience) null else value)
                                            }
                                        }
                                    }
                                }
                                Question("通常愿意花多久？") {
                                    val labels = listOf("15 min 内", "30 min 内", "60 min 内", "时间不限")
                                    val values = listOf(15, 30, 60, 0)
                                    Chips(
                                        labels,
                                        profile.minutes?.let {
                                            setOf(labels[values.indexOf(it).coerceAtLeast(0)])
                                        }.orEmpty(),
                                        Icons.Rounded.Schedule,
                                    ) { label ->
                                        val value = values[labels.indexOf(label)]
                                        vm.change { it.copy(minutes = if (value == it.minutes) null else value) }
                                    }
                                }
                                Question("通常给几个人做饭？") {
                                    Chips(
                                        listOf("1 人", "2 人", "3～4 人", "5 人及以上", "不固定"),
                                        setOfNotNull(profile.servings),
                                        Icons.Rounded.PeopleOutline,
                                    ) { value ->
                                        vm.change { it.copy(servings = if (value == it.servings) null else value) }
                                    }
                                }
                            }
                            3 -> {
                                Question("你想从 DeepCook 收获什么？", "可多选") {
                                    val icons =
                                        listOf(
                                            Icons.Rounded.Lightbulb,
                                            Icons.AutoMirrored.Rounded.MenuBook,
                                            Icons.Rounded.Timer,
                                            Icons.Rounded.Public,
                                            Icons.Rounded.EditNote,
                                        )
                                    FoodChoices.goals.forEachIndexed { index, value ->
                                        ChoiceRow(
                                            value,
                                            FoodChoices.goalNotes[index],
                                            icons[index],
                                            value in profile.goals,
                                        ) {
                                            vm.change { it.copy(goals = it.goals.toggled(value)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Set<String>.toggled(value: String): Set<String> = if (value in this) this - value else this + value

private fun foodIcon(option: String, fallback: ImageVector): ImageVector = when (option) {
    "中式家常" -> Icons.Rounded.SoupKitchen
    "川湘风味", "中辣", "重辣" -> Icons.Rounded.LocalFireDepartment
    "粤式风味", "鱼类", "虾蟹", "贝类" -> Icons.Rounded.SetMeal
    "江浙风味" -> Icons.Rounded.RiceBowl
    "日韩料理", "东南亚菜" -> Icons.Rounded.RamenDining
    "西式料理" -> Icons.Rounded.LocalPizza
    "其他风味" -> Icons.Rounded.Public
    "鸡蛋" -> Icons.Rounded.EggAlt
    "牛奶及乳制品" -> Icons.Rounded.WaterDrop
    "小麦" -> Icons.Rounded.BakeryDining
    "花生", "坚果", "芝麻", "大豆" -> Icons.Rounded.Spa
    "香菜", "芹菜", "葱", "蒜" -> Icons.Rounded.Grass
    else -> fallback
}
private fun Set<String>.exclusiveToggle(value: String, exclusive: String): Set<String> = if (value == exclusive) {
    if (value in this) emptySet() else setOf(value)
} else {
    (this - exclusive).toggled(value)
}

@Composable
private fun Question(title: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (hint !=
            null
        ) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(options: List<String>, selected: Set<String>, icon: ImageVector, onChoose: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.distinct().forEach { option ->
            val active = option in selected
            FilterChip(
                selected = active,
                onClick = { onChoose(option) },
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
                label = { Text(option, style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = {
                    Icon(if (active) Icons.Rounded.Check else foodIcon(option, icon), null, Modifier.size(17.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    note: String,
    icon: ImageVector,
    selected: Boolean,
    single: Boolean = false,
    onClick: () -> Unit,
) {
    val selection = if (single) {
        Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick)
    } else {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
    }
    Surface(
        Modifier.fillMaxWidth().then(selection),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(
                alpha = .5f,
            )
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = .6f,
                )
            },
        ),
    ) {
        Row(
            Modifier.padding(12.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (single) RadioButton(selected, onClick = null) else Checkbox(selected, onCheckedChange = null)
        }
    }
}

@Composable
private fun AddIngredient(key: String, title: String, existing: Set<String>, onAdd: (String) -> Unit) {
    var open by rememberSaveable(key) { mutableStateOf(false) }
    var text by rememberSaveable(key) { mutableStateOf("") }
    TextButton(onClick = { open = true }) {
        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("添加其他食材")
    }
    if (open) {
        val value = text.trim()
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(30) },
                    label = { Text("食材名称") },
                    placeholder = { Text("例如：秋葵") },
                    singleLine = true,
                    modifier = Modifier.testTag("custom-$key"),
                    supportingText = { Text(if (value in existing) "已添加这项食材" else "每次添加一种，最多 30 个字") },
                )
            },
            confirmButton = {
                TextButton(enabled = value.isNotEmpty() && value !in existing, onClick = {
                    onAdd(value)
                    text = ""
                    open =
                        false
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = {
                    text = ""
                    open = false
                }) { Text("取消") }
            },
        )
    }
}
