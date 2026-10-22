@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.deepcook.app.feature.authoring

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
        modifier = Modifier.imePadding(),
        topBar = {
            PageTitle(
                stringResource(if (state.editing) R.string.edit_recipe else R.string.add_recipe),
                ::requestBack,
                actions = {
                    FilledTonalButton(
                        onClick = ::save,
                        enabled = !state.looking && !state.importingCover && !state.saving,
                        modifier = Modifier.padding(end = 8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.saving) "保存中" else "保存")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 720.dp).fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "name") {
                    EditorGroup("菜谱信息", Icons.Rounded.RestaurantMenu) {
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
                }
                item(key = "cover") {
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
                            shape = RoundedCornerShape(12.dp),
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
                        "保存在我的菜谱，随时回来修改。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
            shape = RoundedCornerShape(12.dp),
            value = draft.title,
            onValueChange = onName,
            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
            label = { Text("菜名") },
            singleLine = true,
            isError = state.errors.any { it == "菜名不能为空" },
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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onLookup, enabled = !state.looking) {
                Icon(Icons.Rounded.ManageSearch, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state.looking) "正在查找资料…" else "补全资料")
            }
            Text(
                "只补全空白内容，保留你已经填写的做法。",
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
    EditorGroup("封面", Icons.Rounded.PhotoCamera) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                onClick = onPick,
                enabled = !importing,
                modifier = Modifier.width(112.dp).aspectRatio(4f / 3),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (cover == null) {
                        Icon(
                            Icons.Rounded.AddPhotoAlternate,
                            "选择菜谱封面",
                            Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        RecipeImage(cover, Modifier.fillMaxSize(), description = "菜谱封面")
                    }
                    if (importing) CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "可选，随时可以更换",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onPick, enabled = !importing, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(if (cover == null) stringResource(R.string.pick_cover) else "更换封面")
                }
                if (cover != null) {
                    TextButton(
                        onClick = onRemove,
                        enabled = !importing,
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        Text(stringResource(R.string.remove_cover))
                    }
                }
            }
        }
    }
}

@Composable
private fun BasicsSection(draft: RecipeDraft, edit: ((RecipeDraft) -> RecipeDraft) -> Unit) {
    EditorGroup("基本信息", Icons.Rounded.Tune) {
        Picker(
            label = "地域",
            value = draft.region,
            options = RecipeRegions,
            onChoose = { region -> edit { it.copy(region = region) } },
        )
        CategoryPicker(draft.categories) { categories -> edit { it.copy(categories = categories) } }
        var minutes by remember(draft.minutes) { mutableStateOf(draft.minutes?.toString().orEmpty()) }
        EditorFieldPair(first = { modifier ->
            OutlinedTextField(
                value = minutes,
                onValueChange = { text ->
                    val digits = text.filter(Char::isDigit).take(4)
                    minutes = digits
                    edit { it.copy(minutes = digits.toIntOrNull()) }
                },
                modifier = modifier,
                shape = RoundedCornerShape(12.dp),
                label = { Text("用时（min）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
        }, second = { modifier ->
            Picker(
                label = "难度",
                value = draft.difficulty?.let { "$it/5" } ?: "未标注",
                options = listOf("未标注") + (1..5).map { "$it/5" },
                modifier = modifier,
                onChoose = { label -> edit { it.copy(difficulty = label.substringBefore('/').toIntOrNull()) } },
            )
        })
        OutlinedTextField(
            value = draft.servings,
            onValueChange = { text -> edit { it.copy(servings = text) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
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
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Surface(
        onClick = {
            focus.clearFocus()
            expanded = true
        },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text("选择$label") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp).selectableGroup()) {
                    items(options) { option ->
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                selected = value == option,
                                role = Role.RadioButton,
                                onClick = {
                                    onChoose(option)
                                    expanded = false
                                },
                            ).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = value == option, onClick = null)
                            Text(option, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { expanded = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun CategoryPicker(categories: List<String>, onChoose: (List<String>) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Surface(
        onClick = {
            focus.clearFocus()
            expanded = true
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "分类",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (categories.isEmpty()) {
                        "选择分类，可多选"
                    } else {
                        categories.take(3).joinToString("、") +
                            if (categories.size > 3) " 等 ${categories.size} 项" else ""
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Icon(Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (expanded) {
        var pending by rememberSaveable { mutableStateOf(categories.toList()) }
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text("选择分类") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(RecipeCategories) { category ->
                        val checked = category in pending
                        Row(
                            Modifier.fillMaxWidth().toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { chosen ->
                                    pending =
                                        if (chosen) pending + category else pending - category
                                },
                            ).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(checked, onCheckedChange = null)
                            Text(category, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onChoose(pending)
                    expanded = false
                }, enabled = pending.isNotEmpty()) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { expanded = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun IngredientsSection(ingredients: List<DraftIngredient>, onChange: (List<DraftIngredient>) -> Unit) {
    EditorGroup("材料", Icons.Rounded.ShoppingBasket, "用量可填写克、个或适量。", ingredients.size) {
        ingredients.forEachIndexed { index, ingredient ->
            EditorFieldPair(firstWeight = 1.5f, first = { modifier ->
                OutlinedTextField(
                    value = ingredient.name,
                    onValueChange = { text -> onChange(ingredients.replaced(index, ingredient.copy(name = text))) },
                    modifier = modifier,
                    shape = RoundedCornerShape(12.dp),
                    label = { Text("材料 ${index + 1}") },
                    singleLine = true,
                )
            }, second = { modifier ->
                OutlinedTextField(
                    value = ingredient.amount,
                    onValueChange = { text -> onChange(ingredients.replaced(index, ingredient.copy(amount = text))) },
                    modifier = modifier,
                    shape = RoundedCornerShape(12.dp),
                    label = { Text("用量") },
                    singleLine = true,
                )
            }, trailing = {
                IconButton(onClick = { onChange(ingredients.filterIndexed { at, _ -> at != index }) }) {
                    Icon(Icons.Rounded.DeleteOutline, "删除这项材料")
                }
            })
        }
        OutlinedButton(onClick = {
            onChange(ingredients + DraftIngredient())
        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("添加材料")
        }
    }
}

@Composable
private fun StepsSection(steps: List<DraftStep>, onChange: (List<DraftStep>) -> Unit) {
    EditorGroup("步骤", Icons.Rounded.FormatListNumbered, count = steps.size) {
        steps.forEachIndexed { index, step ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "第 ${index + 1} 步",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        IconButton(onClick = { onChange(steps.filterIndexed { at, _ -> at != index }) }) {
                            Icon(Icons.Rounded.DeleteOutline, "删除第 ${index + 1} 步")
                        }
                    }
                    OutlinedTextField(
                        shape = RoundedCornerShape(12.dp),
                        value = step.title,
                        onValueChange = { text -> onChange(steps.replaced(index, step.copy(title = text))) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("小标题") },
                        singleLine = true,
                        placeholder = { Text("例如：处理西红柿") },
                    )
                    OutlinedTextField(
                        shape = RoundedCornerShape(12.dp),
                        value = step.text,
                        onValueChange = { text -> onChange(steps.replaced(index, step.copy(text = text))) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("操作") },
                        minLines = 2,
                        maxLines = 6,
                        placeholder = { Text("这一步具体怎么做。") },
                    )
                    OutlinedTextField(
                        shape = RoundedCornerShape(12.dp),
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
        OutlinedButton(onClick = {
            onChange(steps + DraftStep())
        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
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
    EditorGroup(title, Icons.Rounded.Notes, hint) {
        values.forEachIndexed { index, value ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    shape = RoundedCornerShape(12.dp),
                    value = value,
                    onValueChange = { text -> onChange(values.replaced(index, text)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("第 ${index + 1} 项") },
                    singleLine = true,
                )
                IconButton(onClick = { onChange(values.filterIndexed { at, _ -> at != index }) }) {
                    Icon(Icons.Rounded.DeleteOutline, "删除第 ${index + 1} 项")
                }
            }
        }
        OutlinedButton(onClick = {
            onChange(values + "")
        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(addLabel)
        }
    }
}

@Composable
private fun FieldBlock(title: String, hint: String, content: @Composable () -> Unit) {
    EditorGroup(title, Icons.Rounded.Description, hint) { content() }
}

@Composable
private fun EditorGroup(
    title: String,
    icon: ImageVector,
    description: String? = null,
    count: Int? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (count !=
                    null
                ) {
                    Text(
                        "$count 项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (description !=
                null
            ) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun EditorFieldPair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    firstWeight: Float = 1f,
    trailing: (@Composable () -> Unit)? = null,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints {
        if (maxWidth < 280.dp || fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    first(Modifier.weight(1f))
                    trailing?.invoke()
                }
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                first(Modifier.weight(firstWeight))
                second(Modifier.weight(1f))
                trailing?.invoke()
            }
        }
    }
}

private fun <T> List<T>.replaced(index: Int, value: T): List<T> =
    mapIndexed { at, existing -> if (at == index) value else existing }
