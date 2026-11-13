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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deepcook.app.core.designsystem.*
import com.deepcook.app.core.model.FoodProfile
import com.deepcook.app.core.model.PhoneNumber
import com.deepcook.app.core.model.UserProfile
import kotlinx.coroutines.launch

/**
 * User information edits the nickname and avatar, and links to the shared food preferences.
 *
 * Nickname changes write through to the repository, so the page has no
 * save button and leaving it at any moment keeps what was chosen. The picture comes from the system
 * photo picker, which hands back a readable URI without any storage permission, and is cropped to a
 * square here before the store saves it.
 */
@Composable
fun ProfileInfoScreen(
    profile: UserProfile,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    foodProfile: FoodProfile,
    onFoodPreferences: () -> Unit,
    savingAvatar: Boolean,
    loadPhoto: suspend (String) -> Bitmap?,
    onSaveAvatar: (Bitmap) -> Unit,
    onRemoveAvatar: () -> Unit,
    /** Null only while the saved session is being read; the page is not reachable before then. */
    phone: String?,
    cloudAccount: Boolean = false,
    onAccount: () -> Unit,
) {
    var cropping by remember { mutableStateOf<Bitmap?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var decoding by remember { mutableStateOf(false) }
    val photoBusy = decoding || savingAvatar
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            decoding = true
            val bitmap = try {
                loadPhoto(uri.toString())
            } finally {
                decoding = false
            }
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
                            enabled = !photoBusy,
                            onClick = {
                                pickPhoto.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        ) { Text(if (profile.avatar == null) "选择头像" else "更换头像") }
                        if (profile.avatar != null) {
                            TextButton(enabled = !photoBusy, onClick = onRemoveAvatar) { Text("移除") }
                        }
                    }
                    if (photoBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
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
                PreferenceRow(
                    "饮食偏好",
                    buildList {
                        addAll(foodProfile.tastes)
                        addAll(foodProfile.avoided.map { "避开$it" })
                    }.take(4).joinToString("、").ifBlank { "喜好、忌口与下厨习惯" },
                    Icons.Rounded.Spa,
                    onClick = onFoodPreferences,
                )
            }
            item {
                PreferenceRow(
                    "账号与安全",
                    phone?.let { if (cloudAccount) it else PhoneNumber.masked(it) } ?: "未登录",
                    Icons.Rounded.Lock,
                    onClick = onAccount,
                )
            }
            item {
                Text(
                    "登录后，昵称、头像和偏好随账号同步到云端。",
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
    var touched by rememberSaveable { mutableStateOf(false) }
    // The stored profile can arrive a moment after the page opens; adopt it only while the field
    // is still untouched, so a late emission cannot overwrite typing.
    LaunchedEffect(profile.displayName) {
        if (!touched) text = profile.displayName
    }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            touched = true
            val capped = UserProfile.capName(value)
            text = capped
            onName(capped)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("昵称") },
        placeholder = { Text("留空则显示「${UserProfile.DEFAULT_NAME}」") },
        supportingText = { Text("${UserProfile.nameLength(text)} / ${UserProfile.MAX_NAME_LENGTH}") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    )
}
