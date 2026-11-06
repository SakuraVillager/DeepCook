@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deepcook.app.feature.cooking

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepcook.app.core.designsystem.PageTitle
import com.deepcook.app.core.model.DishNote
import com.deepcook.app.core.model.Recipe
import kotlinx.coroutines.launch

/**
 * Shown right after "完成": one sentence about how it went, turned into the note the cooking
 * screen will surface next time. The user is never asked to confirm the text afterwards.
 */
@Composable
fun FeedbackScreen(
    recipe: Recipe,
    existingNotes: List<DishNote>,
    voiceEnabled: Boolean,
    vm: FeedbackViewModel,
    onBack: () -> Unit,
    onSave: (text: String, raw: String) -> Unit,
) {
    val saving by vm.saving.collectAsStateWithLifecycle()
    var text by rememberSaveable(recipe.id) { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val voice = remember { VoiceInput(context) }
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { voice.stop() }
    }

    fun beginListening() {
        listening = true
        voice.start(
            onText = { recognized ->
                listening = false
                text = (text + recognized).take(120)
            },
            onError = { _, message ->
                listening = false
                scope.launch { snackbars.showSnackbar(message) }
            },
        )
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            beginListening()
        } else {
            scope.launch { snackbars.showSnackbar("没有麦克风权限，可以先打字。") }
        }
    }

    fun requestMic() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            beginListening()
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = { PageTitle("这道菜怎么样", onBack) },
        snackbarHost = { SnackbarHost(snackbars) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { vm.submit(text) { cleaned -> onSave(cleaned, text) } },
                    enabled = text.isNotBlank() && !saving,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).heightIn(min = 52.dp),
                ) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Check, null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (saving) "正在整理…" else "记下来")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(recipe.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                "说说这次的味道，下次做同一道菜会先提醒你。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (existingNotes.isNotEmpty()) {
                Text("已有的备注", style = MaterialTheme.typography.titleMedium)
                existingNotes.forEach { note ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            note.text,
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                placeholder = { Text("比如：盐放少了，下次多放一点") },
                shape = RoundedCornerShape(16.dp),
            )
            if (voiceEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = {
                        if (listening) {
                            voice.stop()
                            listening = false
                        } else {
                            requestMic()
                        }
                    }) {
                        Icon(if (listening) Icons.Rounded.MicOff else Icons.Rounded.Mic, if (listening) "停止" else "说一句")
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (listening) "正在听…" else "点一下，说一句",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "只保存到本机，不会改动菜谱。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
