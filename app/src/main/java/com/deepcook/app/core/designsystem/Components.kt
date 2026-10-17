@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deepcook.app.core.designsystem

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.deepcook.app.R
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.isMine

@Composable
fun ChefMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_launcher), null, modifier.clip(RoundedCornerShape(20.dp)))
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
    iconOffset: Dp = 0.dp,
) {
    IconToggleButton(checked = selected, onCheckedChange = { onClick() }, modifier = modifier) {
        Icon(
            if (selected) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            stringResource(if (selected) R.string.remove_favorite else R.string.add_favorite),
            modifier = Modifier.size(iconSize).offset(x = iconOffset),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun RecipeMetadata(recipe: Recipe, modifier: Modifier = Modifier, compact: Boolean = false, onImage: Boolean = false) {
    val color = if (onImage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val time = recipe.minutes?.toString() ?: "无"
    val difficulty = recipe.difficulty?.let { "$it/5" } ?: "无"
    val timeDescription = recipe.minutes?.let { "用时 $it 分钟" } ?: "用时无"
    val difficultyDescription = recipe.difficulty?.let { "难度 $it/5" } ?: "难度无"
    if (onImage) {
        Row(
            modifier,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Metadata(Icons.Rounded.Restaurant, difficulty, compact, color, difficultyDescription)
            Metadata(Icons.Rounded.Schedule, time, compact, color, timeDescription)
        }
    } else {
        FlowRow(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Metadata(Icons.Rounded.Schedule, time, compact, color, timeDescription)
            Metadata(Icons.Rounded.Restaurant, difficulty, compact, color, difficultyDescription)
            Metadata(Icons.Rounded.Public, recipe.region, compact, color, "地域 ${recipe.region}")
        }
    }
}

/**
 * Region tag shown on top of a card cover, where the metadata row has no space for a third item.
 * The visible label is the region itself; screen readers get the "地域" prefix.
 */
@Composable
fun RegionBadge(region: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = "地域 $region" },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .88f),
    ) {
        Text(
            region,
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Marks a recipe the user wrote, so their own dishes stay recognisable inside the shared
 * catalog. Same shape and weight as [RegionBadge] but on the opposite corner of a cover.
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
fun RecipeCard(recipe: Recipe, favorite: Boolean, onOpen: () -> Unit, onFavorite: () -> Unit) {
    // Match the title's font scaling while keeping the full touch target.
    // Offset only the glyph to align its visible edge with the title's inset.
    val favoriteIconSize = with(LocalDensity.current) { 16.sp.toDp() }
    val favoriteIconOffset = (16.dp - favoriteIconSize / 2).coerceAtLeast(0.dp)
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3)) {
            RecipeImage(recipe.cover, Modifier.matchParentSize())
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = .9f),
                    ),
                ),
            )
            RegionBadge(recipe.region, Modifier.align(Alignment.TopStart).padding(8.dp))
            if (recipe.isMine) MineBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
            RecipeMetadata(
                recipe,
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                compact = true,
                onImage = true,
            )
        }
        Column(Modifier.padding(start = 12.dp, end = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    recipe.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FavoriteButton(
                    favorite,
                    onFavorite,
                    modifier = Modifier.size(48.dp),
                    iconSize = favoriteIconSize,
                    iconOffset = favoriteIconOffset,
                )
            }
        }
    }
}

@Composable
fun RecipeRow(
    recipe: Recipe,
    favorite: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecipeImage(recipe.cover, Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(recipe.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium)
                    if (recipe.isMine) MineBadge()
                }
                RecipeMetadata(recipe)
            }
            if (trailing != null) trailing() else FavoriteButton(favorite, onFavorite)
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
    TopAppBar(title = { Text(title) }, navigationIcon = { onBack?.let { BackButton(it) } }, actions = actions)
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
