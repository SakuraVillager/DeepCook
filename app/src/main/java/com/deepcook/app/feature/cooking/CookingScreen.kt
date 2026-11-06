@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deepcook.app.feature.cooking

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepcook.app.R
import com.deepcook.app.core.designsystem.*
import com.deepcook.app.core.model.Recipe
import com.deepcook.app.core.model.UserPreferences
import com.deepcook.app.data.CookTimerService
import com.deepcook.app.data.ai.AiExchange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val TimerPresets = listOf(1, 3, 5, 10, 15, 30)

/**
 * The cooking view: one step at a time in large type, a timer, and (in later stages) the
 * assistant bar. It renders the same [Recipe.steps] as the step reader, so both stay in sync.
 */
@Composable
fun CookingScreen(
    recipe: Recipe,
    vm: CookingViewModel,
    preferences: UserPreferences,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onDeleteNote: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTimers by remember { mutableStateOf(false) }
    var showDirectory by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var announcedNote by remember(recipe.id) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val speaker = remember(preferences.ttsVoice) { TtsSpeaker(context, preferences.ttsVoice) }
    val voice = remember { VoiceInput(context) }
    val index = state.stepIndex.coerceIn(recipe.steps.indices)
    val step = recipe.steps[index]
    val lastStep = index == recipe.steps.lastIndex

    LaunchedEffect(vm, preferences.speakAnswers) {
        if (!preferences.speakAnswers) return@LaunchedEffect
        vm.speech.collect { sentence -> speaker.speak(sentence) }
    }
    DisposableEffect(speaker) {
        onDispose {
            voice.stop()
            speaker.destroy()
        }
    }
    LaunchedEffect(state.notes, preferences.speakAnswers) {
        val latest = state.notes.firstOrNull() ?: return@LaunchedEffect
        if (!preferences.speakAnswers) return@LaunchedEffect
        if (!announcedNote) {
            announcedNote = true
            speaker.speak("上次你说：${latest.text}")
        }
    }

    LaunchedEffect(state.ready) {
        if (state.ready) CookTimerService.start(context)
    }
    BackHandler { onBack() }
    KeepAwake(preferences.keepScreenOn)

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun beginListening() {
        speaker.stop()
        listening = true
        // Give the audio route a moment after TTS stops, otherwise the first syllable is clipped.
        scope.launch {
            delay(180)
            if (!listening) return@launch
            voice.start(
                onText = { recognized ->
                    listening = false
                    question = ""
                    keyboard?.hide()
                    speaker.stop()
                    vm.ask(recognized)
                },
                onError = { message ->
                    listening = false
                    scope.launch { snackbars.showSnackbar(message) }
                },
                onPartial = { partial -> question = partial },
            )
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            beginListening()
        } else {
            scope.launch { snackbars.showSnackbar("没有麦克风权限，先打字吧。") }
        }
    }

    fun requestMic() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            beginListening()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        // The whole screen ends above the keyboard, so the input row sits exactly on top of it
        // instead of being covered or padded twice.
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
        ),
        contentWindowInsets = WindowInsets(0),
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
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { showDirectory = true }, enabled = state.ready) {
                        Icon(Icons.Rounded.FormatListNumbered, stringResource(R.string.step_directory))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.widthIn(max = 720.dp).fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.timers.isNotEmpty()) {
                            TimerRow(
                                timers = state.timers,
                                onCancel = vm::cancelTimer,
                            )
                        }
                        if (state.ai.question.isNotEmpty()) {
                            AiAnswerCard(state.ai, onClick = { showHistory = true })
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = question,
                                onValueChange = { question = it.take(120) },
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                placeholder = { Text("问一句：盐放多少？") },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                            )
                            if (preferences.voiceInputEnabled) {
                                FilledTonalIconButton(
                                    onClick = {
                                        if (listening) {
                                            voice.stop()
                                            listening = false
                                        } else {
                                            requestMic()
                                        }
                                    },
                                    modifier = Modifier.size(52.dp),
                                ) {
                                    Icon(
                                        if (listening) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                        if (listening) "停止听" else "说话",
                                    )
                                }
                            }
                            FilledIconButton(
                                onClick = {
                                    speaker.stop()
                                    vm.ask(question)
                                    question = ""
                                    keyboard?.hide()
                                },
                                enabled = state.ready && question.isNotBlank(),
                                modifier = Modifier.size(52.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Send, "发送")
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { vm.goTo(index - 1) },
                                enabled = state.ready && index > 0,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("上一步")
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    askForNotifications()
                                    showTimers = true
                                },
                                enabled = state.ready,
                                modifier = Modifier.size(52.dp),
                            ) {
                                Icon(Icons.Rounded.Timer, "计时")
                            }
                            Button(
                                onClick = {
                                    if (lastStep) vm.complete(onComplete) else vm.goTo(index + 1)
                                },
                                enabled = state.ready,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                            ) {
                                Text(if (lastStep) "完成" else "下一步")
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    if (lastStep) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                                    null,
                                    Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                if (state.notes.isNotEmpty()) {
                    NoteBanner(note = state.notes.first(), count = state.notes.size, onClick = { showNotes = true })
                }
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "第 ${index + 1} / ${recipe.steps.size} 步",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LinearProgressIndicator(
                        progress = { (index + 1f) / recipe.steps.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                                if (preferences.showStepImages && step.image != null) {
                                    RecipeImage(
                                        step.image,
                                        Modifier.fillMaxWidth().aspectRatio(16f / 9).clip(RoundedCornerShape(16.dp)),
                                    )
                                }
                                Text(
                                    step.text,
                                    fontSize = preferences.readingSize.readerSp.sp,
                                    lineHeight = (preferences.readingSize.readerSp * 1.5).sp,
                                )
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
                                step.visibleCue?.let { CueNote(it) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTimers) {
        ModalBottomSheet(onDismissRequest = { showTimers = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("加一个计时", style = MaterialTheme.typography.titleLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TimerPresets.forEach { minutes ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                vm.addTimer(minutes)
                                showTimers = false
                            },
                            label = { Text("$minutes 分钟") },
                        )
                    }
                }
                Text(
                    "到点会响铃并弹出通知，切到后台也会提醒。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                            vm.goTo(n)
                            showDirectory = false
                        },
                    )
                }
            }
        }
    }

    if (showHistory) {
        val exchanges = buildList {
            addAll(state.ai.history)
            if (state.ai.question.isNotEmpty() && lastOrNull()?.question != state.ai.question) {
                add(AiExchange(state.ai.question, state.ai.answer))
            }
        }
        ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Text("这次做菜的问与答", style = MaterialTheme.typography.titleLarge) }
                if (exchanges.isEmpty()) {
                    item {
                        Text(
                            "还没有问过 AI。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 32.dp),
                        )
                    }
                }
                items(exchanges.size) { position ->
                    val exchange = exchanges[position]
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            exchange.question,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(exchange.answer, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (position == exchanges.lastIndex) Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (showNotes) {
        ModalBottomSheet(onDismissRequest = { showNotes = false }) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Text("这道菜的备注", style = MaterialTheme.typography.titleLarge) }
                items(state.notes.size) { position ->
                    val note = state.notes[position]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(note.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { onDeleteNote(note.id) }) {
                            Icon(Icons.Rounded.Delete, "删除备注")
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun NoteBanner(note: com.deepcook.app.core.model.DishNote, count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Rounded.EditNote,
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "上次：${note.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (count > 1) {
                    Text(
                        "还有 ${count - 1} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = .72f),
                    )
                }
            }
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun AiAnswerCard(ai: CookingViewModel.AiUi, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                ai.question,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (ai.answer.isNotEmpty()) ai.answer else if (ai.thinking) "正在想…" else "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TimerRow(timers: List<com.deepcook.app.core.model.CookTimer>, onCancel: (String) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timers) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        timers.forEach { timer ->
            AssistChip(
                onClick = { onCancel(timer.id) },
                label = { Text("${timer.label} · ${remainingLabel(timer.endsAt, now)}") },
                trailingIcon = { Icon(Icons.Rounded.Close, "取消计时", Modifier.size(16.dp)) },
            )
        }
    }
}

private fun remainingLabel(endsAt: Long, now: Long): String {
    val seconds = ((endsAt - now).coerceAtLeast(0) + 999) / 1_000
    return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}

@Composable
private fun CueNote(text: String) {
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
