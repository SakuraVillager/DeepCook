@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deepcook.app.feature.recipe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.deepcook.app.core.model.Ingredient
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.data.ai.ServingScale

/**
 * The detail page's assistant block: a serving picker over the ingredient list, the two questions
 * a cook actually asks before or after cooking, and the answer card. It sits right under 材料
 * because that is the section both questions are about.
 */
@Composable
fun DishAiSection(
    recipe: Recipe,
    state: DishAiViewModel.UiState,
    amountOf: (Ingredient) -> String,
    onSubstitute: (Ingredient) -> Unit,
    onDiagnose: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    var draft by rememberSaveable(recipe.id) { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        draft = ""
        keyboard?.hide()
        onDiagnose(text)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("问 AI", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (state.answer != null) TextButton(onClick = onDismiss) { Text("收起") }
            }
            Text(
                if (state.servingsBase == null) {
                    "缺料、失手都可以问。这道菜没标份量，暂时不能自动换算。回答仅供参考。"
                } else {
                    "缺料、失手、份量都可以问。回答来自 AI 或菜谱原文，仅供参考。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { picking = true },
                    label = { Text("缺料替代") },
                    leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = {
                        focus.requestFocus()
                        keyboard?.show()
                    },
                    label = { Text("哪儿不对？") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, Modifier.size(18.dp)) },
                )
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("例：我做的为什么发苦？") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                trailingIcon = {
                    IconButton(onClick = { send() }, enabled = draft.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Rounded.Send, "发送")
                    }
                },
            )
            state.answer?.let { answer -> AnswerCard(answer, onRetry) }
        }
    }
    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }) {
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Text(
                        "没有哪样材料？",
                        Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "常见调料直接给本地经验，其余交给 AI；都不影响菜谱原文。",
                        Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(recipe.ingredients.size) { index ->
                    val ingredient = recipe.ingredients[index]
                    ListItem(
                        headlineContent = { Text(ingredient.name) },
                        supportingContent = { Text(amountOf(ingredient)) },
                        modifier = Modifier.fillMaxWidth().clickable {
                            picking = false
                            onSubstitute(ingredient)
                        },
                    )
                }
            }
        }
    }
}

/** The serving picker that sits in the 材料 section header; null restores the printed amounts. */
@Composable
fun ServingsPicker(state: DishAiViewModel.UiState, onSelect: (Int?) -> Unit) {
    val base = state.servingsBase ?: return
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                state.targetServings?.let { "$it 人份" } ?: state.servingsLabel,
                style = MaterialTheme.typography.labelLarge,
            )
            Icon(Icons.Rounded.ArrowDropDown, "换算份量")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("原始份量（${state.servingsLabel}）") },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            ServingScale.options(base).forEach { count ->
                DropdownMenuItem(
                    text = { Text("$count 人份") },
                    onClick = {
                        expanded = false
                        onSelect(count)
                    },
                )
            }
        }
    }
}

@Composable
private fun AnswerCard(answer: DishAiViewModel.Answer, onRetry: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                answer.asked,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .75f),
            )
            Text(
                if (answer.text.isNotEmpty()) answer.text else if (answer.thinking) "正在想…" else "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (answer.thinking) LinearProgressIndicator(Modifier.fillMaxWidth())
            answer.hint?.let { hint ->
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .75f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (answer.source) {
                        DishAiViewModel.Source.AI -> "AI 建议，仅供参考"
                        DishAiViewModel.Source.LOCAL -> "本地经验，可直接用"
                        DishAiViewModel.Source.FALLBACK -> "来自菜谱原文"
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .7f),
                )
                // A local answer is the whole answer; anything else can be asked again.
                if (!answer.thinking && answer.source != DishAiViewModel.Source.LOCAL) {
                    TextButton(onClick = onRetry) {
                        Text(if (answer.source == DishAiViewModel.Source.AI) "再问一次" else "再试一次")
                    }
                }
            }
        }
    }
}
