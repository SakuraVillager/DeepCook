@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deepcook.app.feature.timer

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.deepcook.app.core.model.*
import com.deepcook.app.data.TimerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TimerDraft(val label: String, val seconds: Long, val stepId: String?, val detected: String? = null)

class CookingTimerUi(
    val recipe: Recipe,
    val manager: TimerManager,
    private val scope: CoroutineScope,
    initiallyOpen: Boolean,
) {
    var timers by mutableStateOf<List<CookingTimer>>(emptyList())
    var showManager by mutableStateOf(initiallyOpen)
    var draft by mutableStateOf<TimerDraft?>(null)
    var needsPermission by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var permissionEpoch by mutableIntStateOf(0)

    fun add(step: RecipeStep? = null, duration: StepDuration? = null) {
        showManager = false
        draft =
            TimerDraft(
                if (step ==
                    null
                ) {
                    recipe.title
                } else {
                    step.title
                },
                duration?.maximumSeconds ?: 300,
                step?.id,
                duration?.text,
            )
    }

    fun start(value: TimerDraft) {
        draft = value
        if (!manager.exactAllowed() || !manager.notificationsAllowed()) {
            needsPermission = true
            return
        }
        perform {
            manager.start(recipe, value.stepId, value.label, value.seconds)
            draft = null
            needsPermission = false
            showManager = true
        }
    }

    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                action()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = "计时操作未完成，请检查提醒权限后重试。"
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun rememberCookingTimers(recipe: Recipe, manager: TimerManager, initiallyOpen: Boolean): CookingTimerUi {
    val scope = rememberCoroutineScope()
    val ui = remember(recipe.id, manager) { CookingTimerUi(recipe, manager, scope, initiallyOpen) }
    val timers = manager.timers.collectAsStateWithLifecycle().value
    val error by manager.error.collectAsStateWithLifecycle()
    SideEffect { ui.timers = timers }
    LaunchedEffect(error) { if (error != null) ui.error = error }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ui.permissionEpoch++
                scope.launch { runCatching { manager.restore() }.onFailure { ui.error = "保存的计时器暂时无法恢复，请重试。" } }
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(manager) { runCatching { manager.restore() }.onFailure { ui.error = "计时数据暂时无法读取，请重试。" } }
    return ui
}

@Composable
fun CookingTimerButton(ui: CookingTimerUi) {
    IconButton(onClick = { ui.showManager = true }) {
        BadgedBox(badge = { if (ui.timers.isNotEmpty()) Badge { Text(ui.timers.size.toString()) } }) {
            Icon(Icons.Rounded.Timer, "计时器")
        }
    }
}

@Composable
fun StepTimerSection(ui: CookingTimerUi, step: RecipeStep) {
    val detected = remember(step.text) { StepDurations.parse(step.text) }
    val timers = ui.timers.filter { it.recipeId == ui.recipe.id && it.stepId == step.id }
    if (detected.isNotEmpty() || timers.isNotEmpty()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (detected.isNotEmpty()) {
                Text("本步计时", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    detected.forEach { duration ->
                        AssistChip(
                            onClick = { ui.add(step, duration) },
                            label = { Text("计时 ${duration.text}") },
                            leadingIcon = { Icon(Icons.Rounded.Timer, null, Modifier.size(18.dp)) },
                        )
                    }
                }
                Text(
                    "从步骤原文识别，启动前可修改时长。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            timers.forEach { TimerItem(it, ui) }
        }
    }
}

@Composable
private fun TimerItem(timer: CookingTimer, ui: CookingTimerUi) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay(500)
            }
        }
    }
    val left = timer.remaining(System.currentTimeMillis(), now, ui.manager.bootCount())
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    timer.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    timer.recipeTitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (timer.status) {
                        TimerStatus.FINISHED -> "时间到"
                        TimerStatus.PAUSED -> "已暂停 · ${countdownLabel(left)}"
                        TimerStatus.RUNNING -> countdownLabel(left)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (timer.status != TimerStatus.FINISHED) {
                IconButton(onClick = {
                    ui.perform {
                        if (timer.status ==
                            TimerStatus.PAUSED
                        ) {
                            ui.manager.resume(timer.id)
                        } else {
                            ui.manager.pause(timer.id)
                        }
                    }
                }, enabled = !ui.busy) {
                    Icon(
                        if (timer.status == TimerStatus.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        if (timer.status == TimerStatus.PAUSED) "继续计时" else "暂停计时",
                    )
                }
            }
            IconButton(onClick = { ui.perform { ui.manager.remove(timer.id) } }, enabled = !ui.busy) {
                Icon(Icons.Rounded.Close, if (timer.status == TimerStatus.FINISHED) "停止提醒" else "取消计时")
            }
        }
    }
}

@Composable
fun CookingTimerDialogs(ui: CookingTimerUi) {
    val context = LocalContext.current
    val notifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ui.permissionEpoch++ }
    val settings =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { ui.permissionEpoch++ }
    ui.permissionEpoch // Recheck special access when returning from system settings.
    if (ui.showManager) {
        ModalBottomSheet(onDismissRequest = { ui.showManager = false }) {
            Column(
                Modifier.fillMaxWidth().padding(
                    horizontal = 20.dp,
                ).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("厨房计时器", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { ui.add() }) {
                        Icon(Icons.Rounded.Add, null)
                        Text("添加计时器")
                    }
                }
                Text(
                    "系统闹钟负责到点提醒，进程退出不影响已安排的闹钟；重启后恢复。手动强行停止应用或关闭提醒权限后，需要重新打开应用检查计时器。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!ui.manager.exactAllowed() || !ui.manager.notificationsAllowed()) {
                    Text("后台提醒权限未完整开启。", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { ui.needsPermission = true }) { Text("设置提醒权限") }
                }
                if (ui.timers.isEmpty()) Text("还没有计时器。可以从步骤下方快速添加。", style = MaterialTheme.typography.bodyMedium)
                ui.timers.forEach { TimerItem(it, ui) }
                ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (ui.needsPermission) {
        AlertDialog(
            onDismissRequest = { ui.needsPermission = false },
            title = { Text("启用计时提醒") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("通知用于显示倒计时和到点提醒；精确闹钟允许系统在锁屏或应用退出后准时触发。")
                    OutlinedButton(onClick = {
                        runCatching {
                            if (Build.VERSION.SDK_INT >= 33 &&
                                !ui.manager.notificationsAllowed()
                            ) {
                                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                settings.launch(
                                    Intent(
                                        Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                                    ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            }
                        }.onFailure { ui.error = "无法打开通知设置，请在系统设置中开启。" }
                    }) { Text(if (ui.manager.notificationsAllowed()) "通知已开启" else "开启通知") }
                    TextButton(onClick = {
                        settings.launch(
                            Intent(
                                Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                            ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }) { Text("系统通知设置") }
                    if (Build.VERSION.SDK_INT >= 31) {
                        OutlinedButton(onClick = {
                            runCatching {
                                settings.launch(
                                    Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        "package:${context.packageName}".toUri(),
                                    ),
                                )
                            }
                                .onFailure { ui.error = "无法打开精确闹钟设置。" }
                        }) { Text(if (ui.manager.exactAllowed()) "精确闹钟已开启" else "允许精确闹钟") }
                    }
                    ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val draft = ui.draft
                    if (draft != null) ui.start(draft) else ui.needsPermission = false
                }, enabled = ui.manager.exactAllowed() && ui.manager.notificationsAllowed() && !ui.busy) {
                    Text(if (ui.draft != null) "开始计时" else "完成")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    ui.needsPermission = false
                    ui.draft = null
                }) { Text("暂不启动") }
            },
        )
    } else {
        ui.draft?.let { draft ->
            key(draft) { TimerEditor(draft, ui) }
        }
    }
}

@Composable
private fun TimerEditor(draft: TimerDraft, ui: CookingTimerUi) {
    var label by rememberSaveable { mutableStateOf(draft.label) }
    var hours by rememberSaveable { mutableStateOf((draft.seconds / 3600).toString()) }
    var minutes by rememberSaveable { mutableStateOf((draft.seconds % 3600 / 60).toString()) }
    var seconds by rememberSaveable { mutableStateOf((draft.seconds % 60).toString()) }
    val total = (hours.toLongOrNull() ?: 0) * 3600 + (minutes.toLongOrNull() ?: 0) * 60 + (seconds.toLongOrNull() ?: 0)
    AlertDialog(
        onDismissRequest = { if (!ui.busy) ui.draft = null },
        title = { Text("添加计时器") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (draft.detected !=
                    null
                ) {
                    Text("步骤原文：${draft.detected}。范围时长默认取上限，请确认后启动。", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(label, {
                    label = it.take(80)
                }, label = { Text("计时名称") }, singleLine = true, enabled = !ui.busy)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimerNumber("时", hours, { hours = it }, Modifier.weight(1f), "timer-hours")
                    TimerNumber("分", minutes, { minutes = it }, Modifier.weight(1f), "timer-minutes")
                    TimerNumber("秒", seconds, { seconds = it }, Modifier.weight(1f), "timer-seconds")
                }
                Text("支持 1 秒至 7 天，可以同时运行多个计时器。", style = MaterialTheme.typography.bodySmall)
                ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { ui.start(draft.copy(label = label, seconds = total)) },
                enabled =
                !ui.busy && total in 1..7 * 86400 && (minutes.toIntOrNull() ?: 0) in 0..59 &&
                    (seconds.toIntOrNull() ?: 0) in 0..59,
            ) { Text("开始计时") }
        },
        dismissButton = { TextButton(onClick = { ui.draft = null }, enabled = !ui.busy) { Text("取消") } },
    )
}

@Composable
private fun TimerNumber(label: String, value: String, onValue: (String) -> Unit, modifier: Modifier, tag: String) {
    OutlinedTextField(
        value,
        { text ->
            if (text.length <= 3 &&
                text.all(Char::isDigit)
            ) {
                onValue(text)
            }
        },
        modifier.testTag(tag),
        label = {
            Text(label)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
