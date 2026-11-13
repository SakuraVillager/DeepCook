@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deepcook.app.feature.recipe

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepcook.app.core.designsystem.RecipeImage
import com.deepcook.app.core.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RatingSummary(state: CommunityUiState, onRate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("菜谱评分", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onRate, enabled = !state.loading) {
                Text(if (state.content.ownRating == null) "评价一下" else "我的评分 ${state.content.ownRating}/10")
            }
        }
        RatingBreakdown(state.content)
    }
}

@Composable
private fun RatingBreakdown(content: RecipeCommunity) {
    val scoreHeight = with(LocalDensity.current) { MaterialTheme.typography.bodySmall.lineHeight.toDp() } * 2 + 6.dp
    Layout(content = {
        Text(
            content.average?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "无",
            modifier = Modifier.testTag("rating-average"),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = with(LocalDensity.current) { scoreHeight.toSp() * .92f },
                lineHeight = with(LocalDensity.current) { scoreHeight.toSp() },
            ),
            maxLines = 1,
            softWrap = false,
        )
        Text(
            "${content.ratingCount} 条评分",
            modifier = Modifier.testTag("rating-count"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf("好极了" to 8..10, "挺好" to 6..7, "一般" to 1..5).forEach { (name, range) ->
            // Scale the glyph size before multiplying: Android's large-font
            // scaling is nonlinear, so converting 39sp differs from 3 × 13sp.
            val labelWidth = with(LocalDensity.current) { MaterialTheme.typography.bodySmall.fontSize.toDp() } * 3.3f
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, Modifier.width(labelWidth), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = {
                        content.countScores(range).toFloat() / content.ratingCount.coerceAtLeast(1).toFloat()
                    },
                    modifier = Modifier.weight(1f).height(6.dp),
                    drawStopIndicator = {},
                )
            }
        }
    }, modifier = Modifier.fillMaxWidth()) { items, constraints ->
        val gap = 6.dp.roundToPx()
        val columnGap = 16.dp.roundToPx()
        val width = constraints.maxWidth
        val leftWidth = maxOf(items[0].maxIntrinsicWidth(Int.MAX_VALUE), items[1].maxIntrinsicWidth(Int.MAX_VALUE))
            .coerceAtMost((width * .45f).toInt())
        val rightWidth = (width - leftWidth - columnGap).coerceAtLeast(0)
        val rows = items.drop(2).map { it.measure(Constraints.fixedWidth(rightWidth)) }
        // Measure from the actual text rows, including Chinese font fallback and
        // system scaling: the score spans rows 1–2; the count occupies row 3.
        val scoreSpan = rows[0].height + gap + rows[1].height
        val countHeight = maxOf(rows[2].height, items[1].minIntrinsicHeight(leftWidth))
        val score = items[0].measure(Constraints.fixed(leftWidth, scoreSpan))
        val count = items[1].measure(Constraints.fixed(leftWidth, countHeight))
        layout(width, scoreSpan + gap + countHeight) {
            score.placeRelative(0, 0)
            count.placeRelative(0, scoreSpan + gap)
            rows[0].placeRelative(leftWidth + columnGap, 0)
            rows[1].placeRelative(leftWidth + columnGap, rows[0].height + gap)
            rows[2].placeRelative(leftWidth + columnGap, scoreSpan + gap + (countHeight - rows[2].height) / 2)
        }
    }
}

@Composable
fun NutritionSection(state: CommunityUiState, onAll: () -> Unit) {
    val nutrition = state.content.nutrition
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (nutrition?.basis == "recipe") "整道菜营养估算" else "每份营养估算", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onAll) { Text("查看全部") }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 4.dp)) {
            nutritionValues(nutrition).take(4).forEachIndexed { index, (name, value) ->
                if (index > 0) {
                    VerticalDivider(
                        Modifier.fillMaxHeight().padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
                    )
                }
                Column(
                    Modifier.weight(1f).padding(horizontal = 4.dp).testTag("nutrition-column-$index"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Text(value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun nutritionValues(n: Nutrition?): List<Pair<String, String>> = listOf(
    "热量" to (n?.calories?.let { "$it kcal" } ?: "—"),
    "脂肪" to (n?.fat?.let { "$it g" } ?: "—"),
    "蛋白质" to (n?.protein?.let { "$it g" } ?: "—"),
    "碳水" to (n?.carbs?.let { "$it g" } ?: "—"),
    "膳食纤维" to (n?.fiber?.let { "$it g" } ?: "—"),
    "钠" to (n?.sodium?.let { "$it mg" } ?: "—"),
)

@Composable
fun CommunitySection(state: CommunityUiState, viewModel: CommunityViewModel) {
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val light = MaterialTheme.colorScheme.background.luminance() > .5f
    val cardColor = if (light) Color.White else MaterialTheme.colorScheme.surfaceContainerLow
    val borderColor = if (light) Color(0xFFDDDDDD) else MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("厨房交流", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = viewModel::load, enabled = !state.busy) { Text("刷新") }
        }
        if (state.loading) CircularProgressIndicator(Modifier.size(24.dp))
        if (state.error != null) {
            Text(state.error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::load) { Text("重新读取") }
        }
        listOf(true to "交作业", false to "评论").forEach { (submission, name) ->
            var limit by rememberSaveable(submission) { mutableIntStateOf(5) }
            val posts = state.content.posts.filter { it.submission == submission }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = cardColor,
                border = BorderStroke(1.dp, borderColor),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("$name · ${if (submission) state.content.submissionCount ?: posts.size else state.content.commentCount ?: posts.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(
                            onClick = { viewModel.open(if (submission) "submission" else "comment") },
                            modifier = Modifier.testTag(if (submission) "add-submission" else "add-comment"),
                        ) {
                            Icon(
                                if (submission) Icons.Rounded.AddAPhoto else Icons.Rounded.Edit,
                                null,
                                Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (submission) "交作业" else "写评论")
                        }
                    }
                    if (posts.isEmpty()) {
                        Text(
                            if (submission) "还没有作业，记录这次下厨的成果吧。" else "还没有评论，写下你的想法吧。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    posts.take(limit).forEachIndexed { index, post ->
                        if (index > 0) HorizontalDivider(color = borderColor.copy(alpha = .6f))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                    Text(post.author.take(1), style = MaterialTheme.typography.titleSmall)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(post.author, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(post.createdAt)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (post.mine) {
                                        IconButton(onClick = { pendingDelete = post.id }) {
                                            Icon(Icons.Rounded.DeleteOutline, "删除这条${if (submission) "作业" else "评论"}")
                                        }
                                    }
                                }
                                Text(post.text, style = MaterialTheme.typography.bodyLarge)
                                if (post.photo != null) {
                                    RecipeImage(
                                        post.photo,
                                        Modifier.fillMaxWidth().aspectRatio(4f / 3).clip(RoundedCornerShape(12.dp)),
                                    )
                                }
                            }
                        }
                    }
                    val cursor = if (submission) state.content.submissionCursor else state.content.commentCursor
                    if (posts.size > limit || cursor != null) TextButton(enabled = !state.busy, onClick = {
                        if (posts.size <= limit && cursor != null) viewModel.more(submission)
                        limit += 5
                    }) { Text("查看更多$name") }
                }
            }
        }
    }
    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条内容？") },
            text = { Text("这条内容及附图将被删除，不影响菜谱与收藏。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(id)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
fun CommunityDialogs(state: CommunityUiState, viewModel: CommunityViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.photo(uri.toString())
    }
    val mode = state.composer ?: return
    val nutrition = mode == "nutrition"
    val rating = mode == "rating"
    val submission = mode == "submission"
    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        title = {
            Text(
                when (mode) {
                    "nutrition" -> if (state.content.nutrition?.basis == "recipe") "整道菜营养成分估算" else "每份营养成分估算"
                    "rating" -> "给这道菜评分"
                    "submission" -> "交一份作业"
                    else -> "写评论"
                },
            )
        },
        text = {
            Column(
                Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    nutrition -> {
                        nutritionValues(state.content.nutrition).forEach { (name, value) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name)
                                Text(value)
                            }
                        }
                        Text(state.content.nutrition?.note ?: "营养数据为大致估算，仅供饮食参考；实际数值随食材、份量和烹饪方式变化。")
                    }
                    rating -> {
                        Text("${state.score} / 10", style = MaterialTheme.typography.headlineMedium)
                        Slider(
                            value = state.score.toFloat(),
                            onValueChange = {
                                viewModel.score(it.toInt())
                            },
                            valueRange =
                            1f..10f,
                            steps = 8,
                            enabled = !state.busy,
                        )
                        Text("每道菜保留一条本机评分，再次评价会更新原评分。", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        OutlinedTextField(
                            state.text, viewModel::text, Modifier.fillMaxWidth().testTag("community-text"),
                            label = {
                                Text(if (submission) "下厨心得" else "评论内容")
                            }, minLines = 3, maxLines = 8, enabled = !state.busy,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            supportingText = { Text("${state.text.length}/2000") },
                        )
                        if (submission) {
                            if (state.photo !=
                                null
                            ) {
                                RecipeImage(
                                    state.photo,
                                    Modifier.fillMaxWidth().aspectRatio(4f / 3).clip(RoundedCornerShape(12.dp)),
                                )
                            }
                            OutlinedButton(onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }, enabled = !state.busy) {
                                Icon(Icons.Rounded.PhotoLibrary, null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (state.photo ==
                                        null
                                    ) {
                                        "选择成品照片（可选）"
                                    } else {
                                        "更换照片"
                                    },
                                )
                            }
                        }
                    }
                }
                if (state.error != null) Text(state.error, color = MaterialTheme.colorScheme.error)
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (nutrition) viewModel.dismiss() else viewModel.submit() },
                enabled = !state.busy && (nutrition || rating || state.text.isNotBlank()),
            ) {
                Text(
                    if (nutrition) {
                        "关闭"
                    } else if (rating) {
                        "保存评分"
                    } else {
                        "发布"
                    },
                )
            }
        },
        dismissButton = {
            if (!nutrition) TextButton(onClick = viewModel::dismiss, enabled = !state.busy) { Text("取消") }
        },
    )
}
