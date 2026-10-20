@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.deepcook.app.feature.profile

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deepcook.app.core.designsystem.*
import com.deepcook.app.core.model.UserProfile
import kotlinx.coroutines.launch

/**
 * "用户信息": the only place where the user edits their name, picture, and taste tags.
 *
 * Every chip tap and every keystroke writes straight through to the repository, so the page has no
 * save button and leaving it at any moment keeps what was chosen. The picture comes from the system
 * photo picker, which hands back a readable URI without any storage permission, and is cropped to a
 * square here before the store saves it.
 */
@Composable
fun ProfileInfoScreen(
    profile: UserProfile,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onToggleLike: (String, Boolean) -> Unit,
    onToggleAvoid: (String, Boolean) -> Unit,
    onClearLikes: () -> Unit,
    onClearAvoids: () -> Unit,
    loadPhoto: suspend (String) -> Bitmap?,
    onSaveAvatar: (Bitmap) -> Unit,
    onRemoveAvatar: () -> Unit,
) {
    var cropping by remember { mutableStateOf<Bitmap?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = loadPhoto(uri.toString())
            if (bitmap == null) {
                photoError = "这张照片无法读取，请换一张试试。"
            } else {
                photoError = null
                cropping = bitmap
            }
        }
    }
    Scaffold(topBar = { PageTitle("用户信息", onBack) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ProfileAvatar(profile.avatar, Modifier.size(96.dp))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                pickPhoto.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        ) { Text(if (profile.avatar == null) "选择头像" else "更换头像") }
                        if (profile.avatar != null) {
                            TextButton(onClick = onRemoveAvatar) { Text("移除") }
                        }
                    }
                    photoError?.let { message ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            item { NameField(profile, onName) }
            item {
                TagGroup(
                    title = "喜好",
                    hint = "喜欢的口味，做菜时优先考虑。",
                    tags = UserProfile.LIKE_TAGS,
                    selected = profile.likes,
                    onToggle = onToggleLike,
                    onClear = onClearLikes,
                )
            }
            item {
                TagGroup(
                    title = "忌口",
                    hint = "不想吃或不能吃的，会被避开。",
                    tags = UserProfile.AVOID_TAGS,
                    selected = profile.avoids,
                    onToggle = onToggleAvoid,
                    onClear = onClearAvoids,
                    avoided = true,
                )
            }
            item {
                Text(
                    "这些信息只保存在这台手机上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    cropping?.let { source ->
        AvatarCropDialog(
            source = source,
            onCancel = { cropping = null },
            onConfirm = { cropped ->
                cropping = null
                onSaveAvatar(cropped)
            },
        )
    }
}

/**
 * The name is edited locally so the caret never jumps while the write lands in DataStore. A blank
 * field is allowed: [UserProfile.name] falls back to the default heading.
 */
@Composable
private fun NameField(profile: UserProfile, onName: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(profile.displayName) }
    // The stored profile can arrive a moment after the page opens; adopt it only while the field
    // is still untouched, so a late emission cannot overwrite typing.
    LaunchedEffect(profile.displayName) {
        if (text.isBlank() && profile.displayName.isNotBlank()) text = profile.displayName
    }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            val capped = UserProfile.capName(value)
            text = capped
            onName(capped)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("用户名") },
        placeholder = { Text("留空则显示「${UserProfile.DEFAULT_NAME}」") },
        supportingText = { Text("${UserProfile.nameLength(text)} / ${UserProfile.MAX_NAME_LENGTH}") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    )
}

/** A block of multi-select chips; [avoided] tints the selection so 忌口 cannot be mistaken for 喜好. */
@Composable
private fun TagGroup(
    title: String,
    hint: String,
    tags: List<String>,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onClear: () -> Unit,
    avoided: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title, action = if (selected.isEmpty()) null else "清空", onAction = onClear)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEach { tag ->
                val on = tag in selected
                val check: (@Composable () -> Unit)? = if (on) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }
                } else {
                    null
                }
                FilterChip(
                    selected = on,
                    onClick = { onToggle(tag, !on) },
                    label = { Text(tag) },
                    leadingIcon = check,
                    colors = if (avoided) {
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    } else {
                        FilterChipDefaults.filterChipColors()
                    },
                )
            }
        }
    }
}
