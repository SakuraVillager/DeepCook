@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deepcook.app.feature.authoring

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deepcook.app.R
import com.deepcook.app.app.AppUiState
import com.deepcook.app.core.designsystem.EmptyState
import com.deepcook.app.core.designsystem.PageTitle
import com.deepcook.app.core.designsystem.RecipeRow

/**
 * The list behind "我的 › 我的菜谱": everything the user wrote on this device.
 *
 * These recipes live in the same catalog as the bundled ones, so this screen is only about
 * managing them — opening, editing and deleting.
 */
@Composable
fun MyRecipesScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onOpen: (String) -> Unit,
    onFavorite: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val recipes = state.myRecipes
    Scaffold(
        topBar = {
            PageTitle("我的菜谱", onBack, actions = {
                IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, "添加菜谱") }
            })
        },
    ) { padding ->
        if (recipes.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    "还没有自己的菜谱",
                    "写下自己的做法；联网时还能按菜名补全材料与步骤。",
                    "添加菜谱",
                    onAdd,
                    icon = Icons.Rounded.EditNote,
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "共 ${recipes.size} 道。它们和内置菜谱一样能搜索、能收藏。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(recipes, key = { it.id }) { recipe ->
                    RecipeRow(
                        recipe,
                        state.user.favorites.any { it.recipeId == recipe.id },
                        { onOpen(recipe.id) },
                        { onFavorite(recipe.id) },
                        trailing = {
                            Row {
                                IconButton(onClick = { onEdit(recipe.id) }) {
                                    Icon(Icons.Rounded.Edit, stringResource(R.string.edit_recipe))
                                }
                                IconButton(onClick = { pendingDelete = recipe.id }) {
                                    Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete_recipe))
                                }
                            }
                        },
                    )
                }
            }
        }
        pendingDelete?.let { id ->
            val title = recipes.find { it.id == id }?.title.orEmpty()
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("删除这道菜谱？") },
                text = { Text("「$title」和它的封面图会从本机删除，收藏与浏览记录里的这一条也会一起清掉。") },
                confirmButton = {
                    TextButton(onClick = {
                        onDelete(id)
                        pendingDelete = null
                    }) { Text("删除") }
                },
                dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
            )
        }
    }
}
