@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.deepcook.app.feature.authoring

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepcook.app.R
import com.deepcook.app.core.designsystem.InfoCard
import com.deepcook.app.core.designsystem.PageTitle
import com.deepcook.app.core.designsystem.RecipeImage
import com.deepcook.app.core.model.DraftIngredient
import com.deepcook.app.core.model.DraftStep
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.RecipeCategories
import com.deepcook.app.core.model.RecipeDraft
import com.deepcook.app.core.model.RecipeRegions
import com.deepcook.app.core.model.TimeFilter

/**
 * Writes one recipe: name first, then (if online) the prefilled rest, and every field editable.
 *
 * The screen is a plain form over [RecipeEditorViewModel]; saving hands a complete [Recipe] back
 * to the caller, which stores it and leaves. Nothing is written until the user presses 保存, so
 * backing out of a half-filled form never leaves a broken recipe behind.
 */
@Composable
fun RecipeEditorScreen(editor: RecipeEditorViewModel, onBack: () -> Unit) {
    val state by editor.state.collectAsStateWithLifecycle()
    val draft = state.draft
    val focus = LocalFocusManager.current
    val nameFocus = remember { FocusRequester() }
    var confirmDiscard by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.errors) { if (state.errors.isNotEmpty()) listState.animateScrollToItem(0) }

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) editor.importCover(uri.toString())
    }

    fun save() {
        focus.clearFocus()
        editor.save()
    }

    fun requestBack() {
        if (state.saving || state.importingCover) return
        if (state.dirty) confirmDiscard = true else editor.discard()
    }

    // Only intercept the system back gesture when there is typing worth losing.
    BackHandler { requestBack() }
    LaunchedEffect(editor) { editor.finished.collect { onBack() } }

    LaunchedEffect(Unit) {
        if (!state.editing && !state.dirty) nameFocus.requestFocus()
    }

    Scaffold(
        topBar = {
            PageTitle(
                stringResource(if (state.editing) R.string.edit_recipe else R.string.add_recipe),
                ::requestBack,
                actions = {
                    TextButton(onClick = ::save, enabled = !state.looking && !state.importingCover && !state.saving) {
                        Text(if (state.saving) "正在保存…" else "保存")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                NameSection(
                    draft = draft,
                    state = state,
                    nameFocus = nameFocus,
                    onName = { name -> editor.edit { it.copy(title = name) } },
                    onLookup = {
                        focus.clearFocus()
                        editor.prefill()
                    },
                )
            }
            item {
                CoverSection(
                    cover = draft.cover,
                    importing = state.importingCover,
                    onPick = {
                        pickCover.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onRemove = editor::removeCover,
                )
            }
            item {
                BasicsSection(draft = draft, edit = editor::edit)
            }
            item {
                FieldBlock("简介", "一两句话说明这道菜是什么味道。") {
                    OutlinedTextField(
                        value = draft.description,
                        label = { Text("简介") },
                        onValueChange = { text -> editor.edit { it.copy(description = text) } },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        placeholder = { Text("例如：酸甜开胃，15 分钟就能上桌。") },
                    )
                }
            }
            item {
                StringListSection(
                    title = "准备事项",
                    hint = "开火前要先做好的事，没有可以留空。",
                    values = draft.preparation,
                    addLabel = "添加准备事项",
                    onChange = { list -> editor.edit { it.copy(preparation = list) } },
                )
            }
            item {
                IngredientsSection(
                    ingredients = draft.ingredients,
                    onChange = { list -> editor.edit { it.copy(ingredients = list) } },
                )
            }
            item {
                StepsSection(
                    steps = draft.steps,
                    onChange = { list -> editor.edit { it.copy(steps = list) } },
                )
            }
            item {
                StringListSection(
                    title = "注意事项",
                    hint = "容易做坏的地方，写在最后提醒自己。",
                    values = draft.notes,
                    addLabel = "添加注意事项",
                    onChange = { list -> editor.edit { it.copy(notes = list) } },
                )
            }
            if (draft.sourceUrl != null) {
                item {
                    val sourceLicense = draft.originalSource?.license ?: "HowToCook · Unlicense"
                    InfoCard(
                        "资料来源",
                        "资料来自 ${draft.sourceName ?: "来源菜谱"}。$sourceLicense" +
                            "请按自己的做法核对后保存。",
                    )
                }
            }
            item {
                Text(
                    "保存后，这道菜会出现在首页和搜索里，也能收藏。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃这次编辑？") },
            text = { Text("已经填写的内容不会保存。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    editor.discard()
                }) { Text("放弃") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续填写") } },
        )
    }
}

@Composable
private fun NameSection(
    draft: RecipeDraft,
    state: EditorState,
    nameFocus: FocusRequester,
    onName: (String) -> Unit,
    onLookup: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft.title,
            onValueChange = onName,
            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
            label = { Text("菜名") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onLookup() }),
            trailingIcon = {
                if (state.looking) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Search, null)
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onLookup, enabled = !state.looking) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state.looking) "正在查找资料…" else "补全资料")
            }
            Text(
                "优先使用本地菜谱，只补空白内容；没有资料也可以手动填写。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.notice?.let { notice ->
            InfoCard("自动补全", notice)
        }
        if (state.errors.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("还差一点", style = MaterialTheme.typography.titleSmall)
                    state.errors.forEach { problem ->
                        Text(problem, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverSection(cover: String?, importing: Boolean, onPick: () -> Unit, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("封面", style = MaterialTheme.typography.titleMedium)
        Box(
            Modifier.fillMaxWidth().aspectRatio(4f / 3).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            RecipeImage(cover, Modifier.matchParentSize(), description = "菜谱封面")
            if (importing) {
                CircularProgressIndicator()
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onPick, enabled = !importing) {
                Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (cover == null) stringResource(R.string.pick_cover) else "更换封面")
            }
            if (cover != null) {
                TextButton(onClick = onRemove, enabled = !importing) { Text(stringResource(R.string.remove_cover)) }
            }
        }
        Text(
            "不选也可以，会显示占位图。图片裁成 4:3 后存在应用内。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BasicsSection(draft: RecipeDraft, edit: ((RecipeDraft) -> RecipeDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("基本信息", style = MaterialTheme.typography.titleMedium)
        Picker(
            label = "地域",
            value = draft.region,
            options = RecipeRegions,
            onChoose = { region -> edit { it.copy(region = region) } },
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("分类（可多选）", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecipeCategories.forEach { category ->
                    val selected = category in draft.categories
                    FilterChip(
                        selected = selected,
                        onClick = {
                            edit {
                                it.copy(
                                    categories = if (selected) {
                                        it.categories - category
                                    } else {
                                        it.categories + category
                                    },
                                )
                            }
                        },
                        label = { Text(category) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            var minutes by remember(draft.minutes) {
                mutableStateOf(draft.minutes?.toString().orEmpty())
            }
            OutlinedTextField(
                value = minutes,
                onValueChange = { text ->
                    val digits = text.filter(Char::isDigit).take(4)
                    minutes = digits
                    edit { it.copy(minutes = digits.toIntOrNull()) }
                },
                modifier = Modifier.weight(1f),
                label = { Text("用时（分钟）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            Picker(
                label = "难度",
                value = draft.difficulty?.let { "$it/5" } ?: "未标注",
                options = listOf("未标注") + (1..5).map { "$it/5" },
                modifier = Modifier.weight(1f),
                onChoose = { label -> edit { it.copy(difficulty = label.substringBefore('/').toIntOrNull()) } },
            )
        }
        OutlinedTextField(
            value = draft.servings,
            onValueChange = { text -> edit { it.copy(servings = text) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("份量") },
            singleLine = true,
            placeholder = { Text("例如：2 人份") },
        )
        Text(
            "未知的用时、难度和份量可以留空，保存后显示“无”。用时留空时不计入指定用时筛选。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Picker(
    label: String,
    value: String,
    options: List<String>,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(value, Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Rounded.ExpandMore, null)
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onChoose(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun IngredientsSection(ingredients: List<DraftIngredient>, onChange: (List<DraftIngredient>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("材料", style = MaterialTheme.typography.titleMedium)
        Text(
            "用量可以写「200 克」「2 个」「适量」——按你自己的手写。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ingredients.forEachIndexed { index, ingredient ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ingredient.name,
                    onValueChange = { text -> onChange(ingredients.replaced(index, ingredient.copy(name = text))) },
                    modifier = Modifier.weight(2f),
                    label = { Text("材料 ${index + 1}") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = ingredient.amount,
                    onValueChange = { text -> onChange(ingredients.replaced(index, ingredient.copy(amount = text))) },
                    modifier = Modifier.weight(1f),
                    label = { Text("用量") },
                    singleLine = true,
                )
                IconButton(onClick = { onChange(ingredients.filterIndexed { at, _ -> at != index }) }) {
                    Icon(Icons.Rounded.RemoveCircleOutline, "删除这项材料")
                }
            }
        }
        TextButton(onClick = { onChange(ingredients + DraftIngredient()) }) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("添加材料")
        }
    }
}

@Composable
private fun StepsSection(steps: List<DraftStep>, onChange: (List<DraftStep>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("步骤", style = MaterialTheme.typography.titleMedium)
        steps.forEachIndexed { index, step ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第 ${index + 1} 步", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { onChange(steps.filterIndexed { at, _ -> at != index }) }) {
                            Icon(Icons.Rounded.RemoveCircleOutline, "删除第 ${index + 1} 步")
                        }
                    }
                    OutlinedTextField(
                        value = step.title,
                        onValueChange = { text -> onChange(steps.replaced(index, step.copy(title = text))) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("小标题") },
                        singleLine = true,
                        placeholder = { Text("例如：处理西红柿") },
                    )
                    OutlinedTextField(
                        value = step.text,
                        onValueChange = { text -> onChange(steps.replaced(index, step.copy(text = text))) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("操作") },
                        minLines = 2,
                        maxLines = 6,
                        placeholder = { Text("这一步具体怎么做。") },
                    )
                    OutlinedTextField(
                        value = step.cue,
                        onValueChange = { text -> onChange(steps.replaced(index, step.copy(cue = text))) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("观察提示（可选）") },
                        singleLine = true,
                        placeholder = { Text("例如：油面微微起烟") },
                    )
                }
            }
        }
        TextButton(onClick = { onChange(steps + DraftStep()) }) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("添加步骤")
        }
    }
}

@Composable
private fun StringListSection(
    title: String,
    hint: String,
    values: List<String>,
    addLabel: String,
    onChange: (List<String>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        values.forEachIndexed { index, value ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { text -> onChange(values.replaced(index, text)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("第 ${index + 1} 项") },
                    singleLine = true,
                )
                IconButton(onClick = { onChange(values.filterIndexed { at, _ -> at != index }) }) {
                    Icon(Icons.Rounded.RemoveCircleOutline, "删除第 ${index + 1} 项")
                }
            }
        }
        TextButton(onClick = { onChange(values + "") }) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(addLabel)
        }
    }
}

@Composable
private fun FieldBlock(title: String, hint: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

private fun <T> List<T>.replaced(index: Int, value: T): List<T> =
    mapIndexed { at, existing -> if (at == index) value else existing }
