@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deepcook.app.feature.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deepcook.app.R
import com.deepcook.app.app.AppUiState
import com.deepcook.app.app.favoriteCount
import com.deepcook.app.core.designsystem.*
import com.deepcook.app.core.model.*
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
fun DiscoverScreen(
    state: AppUiState,
    onSearch: (String) -> Unit,
    onOpen: (String) -> Unit,
    onFavorite: (String) -> Unit,
) {
    val wide = LocalWindowInfo.current.containerSize.width / LocalDensity.current.density >= 600f
    val foodProfile = state.user.foodSetup.profile
    val personalized = foodProfile.hasAnswers
    val recommendations = remember(state.recipes, foodProfile) { state.recipes.forFoodProfile(foodProfile) }
    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            actions = {
                IconButton(onClick = { onSearch("全部") }) {
                    Icon(Icons.Rounded.Search, stringResource(R.string.search_recipes))
                }
            },
        )
    }) { padding ->
        val minCard = (150 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minCard),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            modifier = Modifier.padding(padding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!wide) {
                        Text("今天，想做点什么？", style = MaterialTheme.typography.headlineSmall)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Regions first, then dish styles; both are picker values the
                        // search screen understands (a region filters by Recipe.region).
                        items(RecipeCategoryOptions) { category ->
                            FilterChip(selected = category == "全部", onClick = {
                                onSearch(category)
                            }, label = { Text(category) })
                        }
                    }
                    SectionTitle(if (personalized) "合你口味" else "精选菜谱", "查看全部", { onSearch("全部") })
                }
            }
            if (recommendations.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("暂时没有合适的推荐", "试试搜索其他菜谱，或在饮食偏好中调整选择。", "去搜索", { onSearch("全部") })
                }
            }
            gridItems(recommendations, key = { it.id }) { recipe ->
                RecipeCard(
                    recipe,
                    state.user.favorites.any {
                        it.recipeId == recipe.id
                    },
                    { onOpen(recipe.id) },
                    { onFavorite(recipe.id) },
                    favoriteCount = state.favoriteCount(recipe.id),
                )
            }
        }
    }
}

@Composable
fun SearchScreen(
    state: AppUiState,
    filter: RecipeFilter,
    query: String,
    onQuery: (String) -> Unit,
    onCategory: (String) -> Unit,
    onTime: (TimeFilter) -> Unit,
    onDifficulty: (Int?) -> Unit,
    onClearFilters: () -> Unit,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onFavorite: (String) -> Unit,
) {
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val recipes = remember(state.recipes, filter) { state.recipes.filteredBy(filter) }
    Scaffold(topBar = {
        Column {
            Row(
                Modifier.statusBarsPadding().fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onBack)
                InlineSearchField(
                    query = query,
                    onQuery = onQuery,
                    focusRequester = focusRequester,
                    onSearch = { focus.clearFocus() },
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        }
    }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterDropdown(
                            filter.category.takeUnless {
                                it == "全部"
                            } ?: "分类",
                            filter.category != "全部",
                            RecipeCategoryOptions,
                            onCategory,
                        )
                    }
                    item {
                        FilterDropdown(
                            filter.time.label.takeUnless { filter.time == TimeFilter.ANY } ?: "用时",
                            filter.time != TimeFilter.ANY,
                            TimeFilter.entries.map { it.label },
                        ) { label ->
                            onTime(
                                TimeFilter.entries.first {
                                    it.label ==
                                        label
                                },
                            )
                        }
                    }
                    item {
                        FilterDropdown(
                            filter.difficulty?.let { "$it/5" } ?: "难度",
                            filter.difficulty != null,
                            listOf("不限难度") + (1..5).map { "$it/5" },
                        ) { label -> onDifficulty(label.substringBefore('/').toIntOrNull()) }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "找到 ${recipes.size} 道菜谱",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onClearFilters, enabled = filter.active) { Text("清除筛选") }
                }
            }
            if (recipes.isEmpty()) {
                item {
                    EmptyState("没有找到相关菜谱", "换个食材关键词，或调整一下筛选条件。", "清除筛选", onClearFilters, icon = Icons.Rounded.Search)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { focusRequester.requestFocus() }) { Text("修改关键词") }
                    }
                }
            }
            items(recipes, key = { it.id }) { recipe ->
                RecipeRow(recipe, state.user.favorites.any { it.recipeId == recipe.id }, {
                    focus.clearFocus()
                    onOpen(recipe.id)
                }, { onFavorite(recipe.id) }, favoriteCount = state.favoriteCount(recipe.id))
            }
        }
    }
}

@Composable
private fun FilterDropdown(label: String, active: Boolean, choices: List<String>, onChoose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = active, onClick = {
            expanded = true
        }, label = { Text(label) }, trailingIcon = { Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp)) })
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(text = { Text(choice) }, onClick = {
                    onChoose(choice)
                    expanded =
                        false
                })
            }
        }
    }
}

@Composable
fun FavoritesScreen(state: AppUiState, onFind: () -> Unit, onOpen: (String) -> Unit, onFavorite: (String) -> Unit) {
    val favorites = state.user.favorites.mapNotNull { saved -> state.recipes.find { it.id == saved.recipeId } }
    Scaffold(topBar = { PageTitle("我的收藏 · ${favorites.size}") }) { padding ->
        if (favorites.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState("还没有收藏菜谱", "把喜欢的味道，留在自己的菜谱里。", "去找菜", onFind)
            }
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(favorites, key = {
                    it.id
                }) { recipe ->
                    RecipeRow(recipe, true, {
                        onOpen(recipe.id)
                    }, { onFavorite(recipe.id) }, favoriteCount = state.favoriteCount(recipe.id))
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    state: AppUiState,
    onSettings: () -> Unit,
    onRecent: () -> Unit,
    onMyRecipes: () -> Unit,
    onFoodPreferences: () -> Unit,
    onOpen: (String) -> Unit,
    onFavorite: (String) -> Unit,
    onFind: () -> Unit,
    onProfile: () -> Unit,
    onNotes: () -> Unit,
) {
    val favoriteIds = state.user.favorites.map { it.recipeId }.toSet()
    val recent = state.user.recent.mapNotNull { saved -> state.recipes.find { it.id == saved.recipeId } }.take(3)
    val notes = state.user.dishNotes
    Scaffold(topBar = {
        PageTitle("我的", actions = {
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
        })
    }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClickLabel = "编辑用户信息", onClick = onProfile)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ProfileAvatar(state.user.profile.avatar, Modifier.size(72.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(state.user.profile.name, style = MaterialTheme.typography.headlineMedium)
                        Text("收藏好菜，慢慢下厨。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Surface(onClick = onMyRecipes, color = Color.Transparent, shape = RoundedCornerShape(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Rounded.EditNote, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("我的菜谱", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Surface(
                    onClick = onFoodPreferences,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)),
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Spa, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("饮食偏好", style = MaterialTheme.typography.titleMedium)
                            Text("喜好、忌口与下厨习惯", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Surface(
                    onClick = onNotes,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            Icons.Rounded.EditNote,
                            null,
                            Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("口味记忆", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (notes.isEmpty()) {
                                    "记下每道菜想怎么改，下次做时先提醒你"
                                } else {
                                    "${notes.size} 条 · 按菜查看、修改或删除"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { SectionTitle("最近浏览", if (recent.isNotEmpty()) "查看全部" else null, onRecent) }
            if (recent.isEmpty()) {
                item {
                    EmptyState("从一道菜开始", "看过的菜谱会留在这里，方便下次找到。", "去找菜", onFind, icon = Icons.Rounded.History)
                }
            }
            item {
                Column {
                    recent.forEachIndexed { index, recipe ->
                        RecipeRow(
                            recipe,
                            recipe.id in favoriteIds,
                            { onOpen(recipe.id) },
                            { onFavorite(recipe.id) },
                            favoriteCount = state.favoriteCount(recipe.id),
                            outlined = false,
                        )
                        if (index < recent.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                    }
                }
            }
        }
    }
}

@Composable
fun RecentScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onFind: () -> Unit,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val recent = state.user.recent.mapNotNull { saved -> state.recipes.find { it.id == saved.recipeId } }
    Scaffold(topBar = { PageTitle("最近浏览", onBack) }) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (recent.isEmpty()) {
                item {
                    EmptyState("还没有浏览记录", "去看看今天想做什么。", "去找菜", onFind, icon = Icons.Rounded.History)
                }
            }
            itemsIndexed(recent, key = { _, recipe -> recipe.id }) { index, recipe ->
                RecipeRow(recipe, false, { onOpen(recipe.id) }, {}, trailing = {
                    IconButton(onClick = {
                        onRemove(recipe.id)
                    }) { Icon(Icons.Rounded.Close, stringResource(R.string.remove_recent)) }
                }, outlined = false)
                if (index < recent.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
            }
        }
    }
}
