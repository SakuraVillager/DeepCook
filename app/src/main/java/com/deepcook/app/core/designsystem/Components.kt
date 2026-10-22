@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deepcook.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.deepcook.app.R
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.compactCount
import com.deepcook.app.core.model.isMine

@Composable
fun ChefMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_launcher), null, modifier.clip(RoundedCornerShape(20.dp)))
}

/**
 * The profile picture. A chosen photo fills a circle; without one the chef mark stands in, so the
 * heading looks intentional before the user picks anything.
 */
@Composable
fun ProfileAvatar(avatar: String?, modifier: Modifier = Modifier) {
    if (avatar == null) {
        Image(
            painterResource(R.drawable.ic_launcher),
            null,
            modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentScale = ContentScale.Crop,
        )
    } else {
        AsyncImage(
            model = avatar,
            contentDescription = "头像",
            placeholder = painterResource(R.drawable.ic_launcher),
            error = painterResource(R.drawable.ic_launcher),
            fallback = painterResource(R.drawable.ic_launcher),
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape),
        )
    }
}

/**
 * Cover art. Bundled recipes store an asset-relative path; a user recipe stores the `file:` URI
 * of the copy in the app's own storage, so both forms arrive here as [Recipe.cover].
 */
@Composable
fun RecipeImage(path: String?, modifier: Modifier = Modifier, description: String? = null) {
    val model = when {
        path == null -> null
        path.startsWith("file:") || path.startsWith("content:") -> path
        else -> "file:///android_asset/$path"
    }
    AsyncImage(
        model = model,
        contentDescription = description,
        placeholder = painterResource(R.drawable.food_placeholder),
        error = painterResource(R.drawable.food_placeholder),
        fallback = painterResource(R.drawable.food_placeholder),
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
fun FavoriteButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    count: Int? = null,
) {
    val description = stringResource(if (selected) R.string.remove_favorite else R.string.add_favorite)
    IconToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = modifier.semantics {
            contentDescription = description
            stateDescription = (if (selected) "已收藏" else "未收藏") + if (count == null) "" else "，收藏数 $count"
        },
    ) {
        FavoriteGlyph(selected, count?.let(::compactCount), iconSize = iconSize)
    }
}

@Composable
fun RecipeMetadata(
    recipe: Recipe,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showRegion: Boolean = true,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val time = recipe.minutes?.let { "$it min" } ?: "无"
    val difficulty = recipe.difficulty?.let { "$it/5" } ?: "无"
    val timeDescription = recipe.minutes?.let { "用时 $it 分钟" } ?: "用时无"
    val difficultyDescription = recipe.difficulty?.let { "难度 $it/5" } ?: "难度无"
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Metadata(Icons.Rounded.Schedule, time, compact, color, timeDescription)
        Metadata(Icons.Rounded.Restaurant, difficulty, compact, color, difficultyDescription)
        if (showRegion) {
            Metadata(Icons.Rounded.Public, recipe.region, compact, color, "地域 ${recipe.region}")
        }
    }
}

/**
 * Marks a recipe the user wrote, so their own dishes stay recognisable inside the shared
 * catalog without adding a region label to the cover.
 */
@Composable
fun MineBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = "我添加的菜谱" },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .92f),
    ) {
        Text(
            "我的",
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
fun Metadata(
    icon: ImageVector,
    label: String,
    compact: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    description: String = label,
) {
    Row(
        Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            icon,
            null,
            Modifier.size(if (compact) 13.dp else 15.dp),
            tint = color,
        )
        Text(
            label,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

@Composable
fun RecipeCard(recipe: Recipe, favorite: Boolean, onOpen: () -> Unit, onFavorite: () -> Unit, favoriteCount: Int = 0) {
    val favoriteIconSize = with(LocalDensity.current) { 20.sp.toDp() }.coerceAtMost(24.dp)
    val favoriteBackdropSize = if (LocalDensity.current.fontScale > 1.3f) 48.dp else (favoriteIconSize + 12.dp)
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = recipeContainerColor()),
        border = recipeBorder(),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3)) {
            RecipeImage(recipe.cover, Modifier.matchParentSize())
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(favoriteBackdropSize),
                    shape = CircleShape,
                    color = recipeContainerColor().copy(alpha = .95f),
                ) {}
                FavoriteButton(
                    favorite,
                    onFavorite,
                    modifier = Modifier.matchParentSize(),
                    iconSize = favoriteIconSize,
                    count = favoriteCount,
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                recipe.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            RecipeMetadata(recipe, compact = true, showRegion = false)
            if (recipe.isMine) MineBadge()
        }
    }
}

@Composable
private fun recipeContainerColor(): Color {
    val colors = MaterialTheme.colorScheme
    return if (colors.background.luminance() > .5f) Color.White else colors.surfaceContainerLow
}

@Composable
private fun recipeBorder(): BorderStroke {
    val colors = MaterialTheme.colorScheme
    val color = if (colors.background.luminance() > .5f) Color(0xFFDDDDDD) else colors.outlineVariant
    return BorderStroke(1.dp, color)
}

@Composable
fun RecipeRow(
    recipe: Recipe,
    favorite: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    favoriteCount: Int = 0,
    trailing: (@Composable () -> Unit)? = null,
    outlined: Boolean = true,
) {
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (outlined) 16.dp else 0.dp),
        color = if (outlined) recipeContainerColor() else Color.Transparent,
        border = if (outlined) recipeBorder() else null,
    ) {
        Row(
            Modifier.padding(horizontal = if (outlined) 10.dp else 0.dp, vertical = if (outlined) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecipeImage(recipe.cover, Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(recipe.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge)
                    if (recipe.isMine) MineBadge()
                }
                RecipeMetadata(recipe)
            }
            Box(Modifier.align(Alignment.Top)) {
                if (trailing != null) trailing() else FavoriteButton(favorite, onFavorite, count = favoriteCount)
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.SoupKitchen,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(96.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
}

@Composable
fun PageTitle(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { onBack?.let { BackButton(it) } },
        actions = actions,
    )
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun StepNumber(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun InfoCard(title: String, text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
