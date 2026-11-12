@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.deepcook.app.feature.recipe

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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withResumed
import com.deepcook.app.R
import com.deepcook.app.core.designsystem.*
import com.deepcook.app.core.model.*
import com.deepcook.app.data.CookSessionStore
import com.deepcook.app.feature.timer.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    recipe: Recipe,
    favorite: Boolean,
    preferences: UserPreferences,
    restoredStep: String?,
    dishAi: DishAiViewModel,
    onFavorite: () -> Unit,
    onBack: () -> Unit,
    onRead: (String?) -> Unit,
    onCook: () -> Unit,
    onVisit: () -> Unit,
    onPhotoVisibilityChange: (Boolean) -> Unit,
    favoriteCount: Int = 0,
    ratingContent: @Composable () -> Unit = {},
    nutritionContent: @Composable () -> Unit = {},
    communityContent: @Composable () -> Unit = {},
) {
    val dishState by dishAi.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val topInset = WindowInsets.safeDrawing.getTop(density)
    val coverVisible by remember(listState, topInset) {
        derivedStateOf {
            val cover = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "cover" }
            listState.firstVisibleItemIndex == 0 &&
                (cover == null || cover.offset + cover.size > topInset)
        }
    }
    var tabHeight by remember(density) { mutableIntStateOf(with(density) { 48.dp.roundToPx() }) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(listState, topInset) {
        snapshotFlow { coverVisible }
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
    // Cover, introduction and the sticky section bar are always the first three lazy items.
    val headerItems = 3
    val sections = remember(recipe.id) {
        buildList {
            add("材料")
            if (recipe.preparationItems.isNotEmpty()) add("准备")
            add("步骤")
            if (recipe.noteItems.isNotEmpty()) add("注意")
            add("来源")
        }
    }
    // Each step has a lazy item, so a reader can return to that exact step. Walking the
    // visible sections keeps this mapping correct when 准备 or 注意 is hidden. The "问 AI"
    // block is a second item under 材料, so that section owns two lazy items.
    val sectionIndices = remember(recipe.id) {
        buildMap {
            var index = headerItems
            sections.forEach { section ->
                put(section, index)
                index += when (section) {
                    "步骤" -> recipe.steps.size + 1
                    "材料" -> 2
                    else -> 1
                }
            }
        }
    }
    // Shift only the tab placement as it approaches the safe area. The lazy item's
    // measured height stays constant, so pinning never changes the scroll geometry.
    val tabInset by remember(topInset) {
        derivedStateOf {
            val offset = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "sections" }?.offset
            if (offset == null) 0 else (topInset - offset).coerceIn(0, topInset)
        }
    }
    val activeSection by remember(sections, topInset) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            val stickyHeight = tabHeight + if (tabInset > 0) topInset else 0
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
            listState.scrollToItem(target, -(tabHeight + topInset))
        }
    }
    var imageLinkError by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = preferences.readingSize.detailSp.sp,
        lineHeight = (
            preferences.readingSize.detailSp *
                1.5
            ).sp,
    )
    Scaffold(bottomBar = {
        Surface(tonalElevation = 3.dp) {
            Box(
                Modifier.navigationBarsPadding().fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onCook,
                        modifier = Modifier.testTag("bottom-cooking-mode").weight(1f).heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("开始做菜")
                    }
                    OutlinedButton(onClick = {
                        onRead(null)
                    }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.MenuBook, null)
                        Spacer(Modifier.width(8.dp))
                        Text("分步阅读")
                    }
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
                                FavoriteButton(favorite, onFavorite, count = favoriteCount)
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
                            recipe.servings.ifBlank { NO_CONTENT },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(recipe.description, style = textStyle)
                        Text(
                            if (recipe.isMine) {
                                "我的菜谱 · 原始份量"
                            } else if (recipe.collection == "DeepCook 自编示例") {
                                "自编示例 · 未经试做验证"
                            } else {
                                "${recipe.collection.ifBlank {
                                    "菜谱"
                                }} · ${if (recipe.id.startsWith("world-")) "中文译文" else "原文整理"} · 原始份量"
                            },
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        ratingContent()
                        nutritionContent()
                    }
                }
                stickyHeader(key = "sections") {
                    Layout(content = {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                        ) {
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
                                                -(tabHeight + topInset),
                                            )
                                        }
                                    }, text = { Text(label) })
                                }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth().testTag("detail-section-header")) { children, constraints ->
                        val tabs = children.single().measure(constraints)
                        // Reserve the safe area from the first measure. The tabs
                        // always remain inside their parent, including on tall
                        // cutouts, so drawing, touch and accessibility agree.
                        layout(tabs.width, tabs.height + topInset) {
                            tabs.placeRelative(0, tabInset)
                        }
                    }
                }
                item(key = "材料") {
                    DetailSection(
                        "材料",
                        compact = true,
                        action = { ServingsPicker(dishState, dishAi::selectServings) },
                    ) {
                        Column {
                            // A range like "2—3 人份" scales from its smaller number, so picking
                            // that same number changes nothing and needs no notice.
                            if (dishState.targetServings != null && dishState.factor != 1.0) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "已按 ${dishState.targetServings} 人份换算，调味请按口味调整；" +
                                            "步骤文字里的用量仍是原文。",
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    TextButton(onClick = { dishAi.selectServings(null) }) { Text("恢复") }
                                }
                            }
                            recipe.ingredients.forEachIndexed { index, ingredient ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Text(ingredient.name, Modifier.weight(1f), style = textStyle)
                                    Text(
                                        dishAi.amountOf(ingredient).ifBlank { NO_CONTENT },
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
                item(key = "问 AI") {
                    DishAiSection(
                        recipe = recipe,
                        state = dishState,
                        amountOf = dishAi::amountOf,
                        onSubstitute = dishAi::askSubstitute,
                        onDiagnose = dishAi::askDiagnosis,
                        onRetry = dishAi::retry,
                        onDismiss = dishAi::dismiss,
                    )
                }
                if (recipe.preparationItems.isNotEmpty()) {
                    item(key = "准备") {
                        DetailSection("准备事项") {
                            recipe.preparationItems.forEach { Text("•  $it", style = textStyle) }
                        }
                    }
                }
                item(key = "步骤") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "烹饪步骤 · 共 ${recipe.steps.size} 步",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        TextButton(onClick = { onRead(null) }, modifier = Modifier.testTag("step-cooking-mode")) {
                            Text(stringResource(R.string.cooking_mode))
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
                        }
                    }
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
                            StepTiming(step, Modifier.padding(start = 44.dp))
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
                            step.visibleCue?.let { InfoCard("观察提示", it) }
                        }
                    }
                    if (index < recipe.steps.lastIndex) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                }
                if (recipe.noteItems.isNotEmpty()) {
                    item(key = "注意") {
                        DetailSection("注意事项") { recipe.noteItems.forEach { Text("•  $it", style = textStyle) } }
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
                item(key = "community") { communityContent() }
            }
            if (!coverVisible) {
                // Draw outside the scrolling content without consuming any new inset.
                Surface(
                    modifier = Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.safeDrawing)
                        .testTag("detail-status-bar-background"),
                    color = MaterialTheme.colorScheme.surface,
                ) {}
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    compact: Boolean = false,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (action != null) 10.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            action?.invoke()
        }
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
fun ReaderScreen(
    recipe: Recipe,
    initialStep: String?,
    preferences: UserPreferences,
    sessions: CookSessionStore,
    showTimers: Boolean = false,
    onBack: (String) -> Unit,
) {
    var index by rememberSaveable(recipe.id, recipe.version) {
        mutableIntStateOf(recipe.steps.indexOfFirst { it.id == initialStep }.coerceAtLeast(0))
    }
    var showDirectory by remember { mutableStateOf(false) }
    val step = recipe.steps[index.coerceIn(recipe.steps.indices)]
    val timerUi = rememberCookingTimers(recipe, sessions, showTimers)
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
                    CookingTimerButton(timerUi)
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
                                StepTiming(step)
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
                                StepTimerSection(timerUi, step)
                            }
                        }
                        val ingredients = recipe.ingredients.filter { it.id in step.ingredientRefs }
                        if (ingredients.isNotEmpty() || step.visibleCue != null) {
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
                                step.visibleCue?.let { ObservationNote(it) }
                            }
                        }
                    }
                }
            }
        }
    }
    CookingTimerDialogs(timerUi)
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

