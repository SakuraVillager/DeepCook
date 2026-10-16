@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.deepcook.app.feature.recipe

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.deepcook.app.R
import com.deepcook.app.core.designsystem.*
import com.deepcook.app.core.model.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    recipe: Recipe,
    favorite: Boolean,
    preferences: UserPreferences,
    restoredStep: String?,
    onFavorite: () -> Unit,
    onBack: () -> Unit,
    onRead: (String?) -> Unit,
    onVisit: () -> Unit,
    onPhotoVisibilityChange: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density)
    var tabHeight by remember(density) { mutableIntStateOf(with(density) { 48.dp.roundToPx() }) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 }
            .distinctUntilChanged()
            .collect(onPhotoVisibilityChange)
    }
    var recordedVisit by rememberSaveable(recipe.id) { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(recipe.id, lifecycleOwner) {
        if (!recordedVisit) {
            // A navigation entry resumes after its enter animation. Avoid a
            // DataStore emission recomposing Home and Detail during that animation.
            lifecycleOwner.lifecycle.withResumed {
                onVisit()
                recordedVisit = true
            }
        }
    }
    val sections = remember(recipe.id) {
        buildList {
            add("材料")
            if (recipe.preparation.isNotEmpty()) add("准备")
            add("步骤")
            if (recipe.notes.isNotEmpty()) add("注意")
            add("来源")
        }
    }
    // Each step has a lazy item, so a reader can return to that exact step.
    val sectionIndices = remember(recipe.id) {
        buildMap {
            var index = 3
            put("材料", index++)
            if (recipe.preparation.isNotEmpty()) put("准备", index++)
            put("步骤", index++)
            index += recipe.steps.size
            if (recipe.notes.isNotEmpty()) put("注意", index++)
            put("来源", index)
        }
    }
    val sectionPinned by remember { derivedStateOf { listState.firstVisibleItemIndex >= 2 } }
    val activeSection by remember(sections, statusBarHeight) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            val stickyHeight = tabHeight + if (sectionPinned) statusBarHeight else 0
            val anchor = visible.firstOrNull { it.index >= 3 && it.offset + it.size > stickyHeight }?.index ?: 3
            if (!listState.canScrollForward && listState.firstVisibleItemIndex > 0) {
                sections.lastIndex
            } else {
                sections.indexOfLast { sectionIndices.getValue(it) <= anchor }.coerceAtLeast(0)
            }
        }
    }
    LaunchedEffect(restoredStep) {
        if (restoredStep != null) {
            val stepIndex = recipe.steps.indexOfFirst { it.id == restoredStep }.coerceAtLeast(0)
            val target = sectionIndices.getValue("步骤") + 1 + stepIndex
            // Position once, with the height the header will have when pinned.
            // Scrolling once with the unpinned height hides the section title.
            listState.scrollToItem(target, -(tabHeight + statusBarHeight))
        }
    }
    var imageLinkError by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = preferences.readingSize.detailSp.sp,
        lineHeight = (
            preferences.readingSize.detailSp *
                1.6
            ).sp,
    )
    Scaffold(bottomBar = {
        Surface(tonalElevation = 3.dp) {
            Box(
                Modifier.navigationBarsPadding().fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Button(onClick = {
                    onRead(null)
                }, modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, null)
                    Spacer(Modifier.width(8.dp))
                    Text("分步阅读")
                }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(
                    max = 840.dp,
                ).fillMaxWidth().padding(bottom = padding.calculateBottomPadding()),
            ) {
                item(key = "cover") {
                    Box {
                        RecipeImage(recipe.cover, Modifier.fillMaxWidth().height(280.dp))
                        Box(
                            Modifier.fillMaxWidth().height(
                                120.dp,
                            ).background(
                                Brush.verticalGradient(listOf(Color.Black.copy(alpha = .45f), Color.Transparent)),
                            ),
                        )
                        Row(
                            Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            FilledTonalIconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                            }
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                                FavoriteButton(favorite, onFavorite)
                            }
                        }
                    }
                }
                item(key = "introduction") {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(recipe.title, style = MaterialTheme.typography.headlineLarge)
                        RecipeMetadata(recipe)
                        Text(
                            recipe.servings,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(recipe.description, style = textStyle)
                        Text(
                            "示例菜谱 · 仅展示原始份量",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                stickyHeader(key = "sections") {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                    ) {
                        Column {
                            if (sectionPinned) Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                            PrimaryScrollableTabRow(
                                selectedTabIndex = activeSection,
                                edgePadding = 8.dp,
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.onSizeChanged { tabHeight = it.height },
                            ) {
                                sections.forEachIndexed { index, label ->
                                    Tab(selected = index == activeSection, onClick = {
                                        scope.launch {
                                            listState.animateScrollToItem(
                                                sectionIndices.getValue(label),
                                                -(tabHeight + statusBarHeight),
                                            )
                                        }
                                    }, text = { Text(label) })
                                }
                            }
                        }
                    }
                }
                item(key = "材料") {
                    DetailSection("材料", compact = true) {
                        Column {
                            recipe.ingredients.forEachIndexed { index, ingredient ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Text(ingredient.name, Modifier.weight(1f), style = textStyle)
                                    Text(
                                        ingredient.amount,
                                        Modifier.weight(.75f),
                                        style = textStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.End,
                                    )
                                }
                                if (index < recipe.ingredients.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f),
                                    )
                                }
                            }
                        }
                    }
                }
                if (recipe.preparation.isNotEmpty()) {
                    item(key = "准备") {
                        DetailSection("准备事项") {
                            recipe.preparation.forEach { Text("•  $it", style = textStyle) }
                        }
                    }
                }
                item(key = "步骤") {
                    Text(
                        "烹饪步骤 · 共 ${recipe.steps.size} 步",
                        Modifier.padding(20.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                items(recipe.steps.size, key = { recipe.steps[it].id }) { index ->
                    val step = recipe.steps[index]
                    val highlight = step.id == restoredStep
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (highlight) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                StepNumber(index + 1)
                                Text(
                                    step.title,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            if (preferences.showStepImages &&
                                step.image != null
                            ) {
                                RecipeImage(
                                    step.image,
                                    Modifier.fillMaxWidth().aspectRatio(
                                        4f / 3,
                                    ).clip(RoundedCornerShape(16.dp)),
                                )
                            }
                            Text(step.text, style = textStyle)
                            step.cue?.let { InfoCard("观察提示", it) }
                            TextButton(onClick = { onRead(step.id) }) {
                                Icon(Icons.Rounded.OpenInFull, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("放大阅读 · 第 ${index + 1} 步")
                            }
                        }
                    }
                    if (index < recipe.steps.lastIndex) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                }
                if (recipe.notes.isNotEmpty()) {
                    item(key = "注意") {
                        DetailSection("注意事项") { recipe.notes.forEach { Text("•  $it", style = textStyle) } }
                    }
                }
                item(key = "来源") {
                    DetailSection("内容与来源") {
                        Text(recipe.source.author, style = MaterialTheme.typography.titleMedium)
                        Text(
                            recipe.source.license,
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val link = recipe.source.url ?: recipe.source.imageSource
                        if (link?.startsWith("https://") == true) {
                            OutlinedButton(onClick = {
                                try {
                                    uriHandler.openUri(link)
                                } catch (
                                    _: IllegalArgumentException,
                                ) {
                                    imageLinkError = true
                                }
                            }) { Text("查看来源") }
                        }
                        if (imageLinkError) Text("暂时无法打开链接，请检查是否安装浏览器。", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun ObservationNote(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Rounded.Visibility,
            null,
            Modifier.padding(top = 2.dp).size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("观察提示", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ReaderScreen(recipe: Recipe, initialStep: String?, preferences: UserPreferences, onBack: (String) -> Unit) {
    var index by rememberSaveable(recipe.id) {
        mutableIntStateOf(recipe.steps.indexOfFirst { it.id == initialStep }.coerceAtLeast(0))
    }
    var showDirectory by remember { mutableStateOf(false) }
    val step = recipe.steps[index]
    androidx.activity.compose.BackHandler { onBack(step.id) }
    KeepAwake(preferences.keepScreenOn)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        recipe.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = { BackButton { onBack(step.id) } },
                actions = {
                    IconButton(onClick = {
                        showDirectory = true
                    }) { Icon(Icons.Rounded.FormatListNumbered, stringResource(R.string.step_directory)) }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.widthIn(
                        max = 720.dp,
                    ).fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = {
                        index--
                    }, enabled = index > 0, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("上一步")
                    }
                    Button(onClick = {
                        if (index ==
                            recipe.steps.lastIndex
                        ) {
                            onBack(step.id)
                        } else {
                            index++
                        }
                    }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Text(
                            if (index ==
                                recipe.steps.lastIndex
                            ) {
                                "返回菜谱"
                            } else {
                                "下一步"
                            },
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "第 ${index + 1} / ${recipe.steps.size} 步",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LinearProgressIndicator(progress = {
                        (index + 1f) / recipe.steps.size
                    }, modifier = Modifier.fillMaxWidth())
                }
                key(step.id) {
                    Column(
                        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text(step.title, style = MaterialTheme.typography.headlineMedium)
                                if (preferences.showStepImages &&
                                    step.image != null
                                ) {
                                    RecipeImage(
                                        step.image,
                                        Modifier.fillMaxWidth().aspectRatio(16f / 9).clip(RoundedCornerShape(16.dp)),
                                    )
                                }
                                Text(
                                    step.text,
                                    fontSize = preferences.readingSize.readerSp.sp,
                                    lineHeight = (
                                        preferences.readingSize.readerSp *
                                            1.5
                                        ).sp,
                                )
                            }
                        }
                        val ingredients = recipe.ingredients.filter { it.id in step.ingredientRefs }
                        if (ingredients.isNotEmpty() || step.cue != null) {
                            Column(
                                Modifier.padding(horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (ingredients.isNotEmpty()) {
                                    Text(
                                        "本步材料",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        ingredients.joinToString("、") { "${it.name} ${it.amount}" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                step.cue?.let { ObservationNote(it) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDirectory) {
        ModalBottomSheet(onDismissRequest = { showDirectory = false }) {
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                item { Text("步骤目录", Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge) }
                items(recipe.steps.size) { n ->
                    ListItem(
                        headlineContent = { Text(recipe.steps[n].title) },
                        leadingContent = { StepNumber(n + 1) },
                        trailingContent = { if (n == index) Icon(Icons.Rounded.Check, "当前步骤") },
                        modifier = Modifier.fillMaxWidth().background(
                            if (n == index) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        ).clickable {
                            index = n
                            showDirectory = false
                        },
                    )
                }
            }
        }
    }
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
private fun KeepAwake(enabled: Boolean) {
    val activity = LocalContext.current.activity()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(enabled, activity, owner) {
        fun update() {
            if (enabled && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner.lifecycle.addObserver(observer)
        update()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
