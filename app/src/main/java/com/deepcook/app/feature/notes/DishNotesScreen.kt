package com.deepcook.app.feature.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepcook.app.core.designsystem.EmptyState
import com.deepcook.app.core.designsystem.PageTitle
import com.deepcook.app.core.model.DishNote
import com.deepcook.app.core.model.Recipe
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Notes for one dish, ready to render. */
data class DishNoteGroup(val recipeId: String, val title: String, val notes: List<DishNote>)

/**
 * Groups notes by dish, newest dish first, newest note first inside a dish. [titles] maps the
 * recipes that still exist; a note left behind by a retired recipe keeps its wording and stays
 * deletable instead of vanishing silently.
 */
fun groupNotes(notes: List<DishNote>, titles: Map<String, String>): List<DishNoteGroup> = notes
    .groupBy { it.recipeId }
    .map { (recipeId, list) ->
        DishNoteGroup(
            recipeId = recipeId,
            title = titles[recipeId] ?: "已移除的菜谱",
            notes = list.sortedByDescending { it.updatedAt },
        )
    }
    .sortedByDescending { group -> group.notes.maxOf { it.updatedAt } }

/**
 * All dish notes in one place: read, correct and delete what the assistant remembered, or write
 * a note by hand for a dish that has none yet.
 */
@Composable
fun DishNotesScreen(
    notes: List<DishNote>,
    recipes: List<Recipe>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: (String, String) -> Unit,
    onFind: () -> Unit,
) {
    val titles = remember(recipes) { recipes.associate { it.id to it.title } }
    val groups = remember(notes, titles) { groupNotes(notes, titles) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val editingNote = notes.find { it.id == editing }
    Scaffold(topBar = {
        PageTitle("口味记忆", onBack, actions = {
            TextButton(onClick = { showAdd = true }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加")
            }
        })
    }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "做菜时说「记一笔：少放盐」，或在做完后的反馈页说一句。下次再做这道菜时，" +
                        "做菜页顶部会先提醒你。这里也能手写、修改和删除。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (groups.isEmpty()) {
                item {
                    EmptyState(
                        "还没有口味备注",
                        "记下每道菜想怎么改，下次做同一道菜时会先提醒你。",
                        "去找菜",
                        onFind,
                        icon = Icons.Rounded.EditNote,
                    )
                }
            }
            items(groups, key = { it.recipeId }) { group ->
                NoteGroupCard(
                    group = group,
                    onOpen = if (titles.containsKey(group.recipeId)) {
                        { onOpen(group.recipeId) }
                    } else {
                        null
                    },
                    onEdit = { editing = it.id },
                    onDelete = { onDelete(it.id) },
                )
            }
        }
    }
    if (showAdd) {
        AddNoteDialog(
            recipes = recipes,
            onDismiss = { showAdd = false },
            onSave = { recipeId, text ->
                showAdd = false
                onAdd(recipeId, text)
            },
        )
    }
    if (editingNote != null) {
        EditNoteDialog(
            note = editingNote,
            onDismiss = { editing = null },
            onSave = { text ->
                editing = null
                onEdit(editingNote.id, text)
            },
        )
    }
}

@Composable
private fun NoteGroupCard(
    group: DishNoteGroup,
    onOpen: (() -> Unit)?,
    onEdit: (DishNote) -> Unit,
    onDelete: (DishNote) -> Unit,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (onOpen != null) Modifier.clickable(onClickLabel = "打开菜谱", onClick = onOpen) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.EditNote, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    group.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${group.notes.size} 条",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onOpen != null) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        null,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            group.notes.forEach { note ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(note.text, style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                note.source.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                noteTime(note.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { onEdit(note) }) {
                        Icon(Icons.Rounded.Edit, "修改备注", Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onDelete(note) }) {
                        Icon(Icons.Rounded.Delete, "删除备注", Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EditNoteDialog(note: DishNote, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable(note.id) { mutableStateOf(note.text) }
    val trimmed = text.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改备注") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (note.raw.isNotBlank() && note.raw != note.text) {
                    Text(
                        "原话：${note.raw}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                NoteTextField(text, { text = it }, label = "下次怎么改")
            }
        },
        confirmButton = {
            TextButton(enabled = trimmed.isNotEmpty() && trimmed != note.text, onClick = { onSave(trimmed) }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AddNoteDialog(recipes: List<Recipe>, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var pickedId by rememberSaveable { mutableStateOf<String?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    val picked = recipes.find { it.id == pickedId }
    val matches = remember(query, recipes) {
        val key = query.trim()
        if (key.isEmpty()) {
            emptyList()
        } else {
            recipes.filter { it.title.contains(key, ignoreCase = true) }.take(30)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (picked == null) "选择菜品" else "写一条备注") },
        text = {
            if (picked == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        query,
                        { query = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("菜名") },
                        singleLine = true,
                    )
                    if (matches.isEmpty()) {
                        Text(
                            if (query.isBlank()) "输入菜名搜索，比如「红烧肉」。" else "没有找到这道菜。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(matches, key = { it.id }) { recipe ->
                            ListItem(
                                headlineContent = { Text(recipe.title) },
                                supportingContent = { Text(recipe.region) },
                                modifier = Modifier.clickable {
                                    pickedId = recipe.id
                                    text = ""
                                },
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(picked.title, style = MaterialTheme.typography.titleMedium)
                    NoteTextField(text, { text = it }, label = "下次怎么改")
                    TextButton(onClick = { pickedId = null }) { Text("换一道菜") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = picked != null && text.trim().isNotEmpty(), onClick = {
                picked?.let { onSave(it.id, text.trim()) }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NoteTextField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value,
        { next -> if (next.length <= NOTE_MAX_LENGTH) onChange(next) },
        Modifier.fillMaxWidth().heightIn(min = 96.dp),
        label = { Text(label) },
        placeholder = { Text("例如：少放盐、冰糖减半") },
        supportingText = { Text("${value.length}/$NOTE_MAX_LENGTH") },
        maxLines = 3,
    )
}

/** Long enough for a real correction, short enough to read from the stove. */
private const val NOTE_MAX_LENGTH = 40

private val dayFormat = DateTimeFormatter.ofPattern("M月d日")
private val fullFormat = DateTimeFormatter.ofPattern("yyyy年M月d日")

/** A date the cook can place: "今天", "昨天", "3 天前", then a calendar date. */
private fun noteTime(at: Long, now: Long = System.currentTimeMillis()): String {
    if (at <= 0) return ""
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0L -> "今天"
        days == 1L -> "昨天"
        days < 7L -> "$days 天前"
        day.year == today.year -> day.format(dayFormat)
        else -> day.format(fullFormat)
    }
}
