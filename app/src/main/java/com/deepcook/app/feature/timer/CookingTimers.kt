@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deepcook.app.feature.timer

import android.Manifest
import android.content.Intent
import android.os.Build
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
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.deepcook.app.core.model.*
import com.deepcook.app.data.CookSessionStore
import com.deepcook.app.data.CookTimerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TimerDraft(val label: String, val seconds: Long, val stepId: String?, val detected: String? = null)

/**
 * Timers started from the reader.
 *
 * There is exactly one timer engine in the app: [CookSessionStore] holds the deadlines and
 * [CookTimerService] rings them in a foreground service. This class is the reader's view of that
 * engine — it never keeps its own copy, so a timer started here also shows up on the cooking
 * screen and in the «计时十分钟» voice path.
 */
class CookingTimerUi(
    val recipe: Recipe,
    private val sessions: CookSessionStore,
    private val scope: CoroutineScope,
    val canNotify: () -> Boolean,
    private val startService: () -> Unit,
    initiallyOpen: Boolean,
) {
    var timers by mutableStateOf<List<CookTimer>>(emptyList())
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
        if (!canNotify()) {
            needsPermission = true
            return
        }
        perform {
            // The reader may be the first screen that starts a timer, so make sure the session
            // belongs to this dish before adding to it.
            sessions.ensure(recipe.id, recipe.title)
            sessions.addTimer(
                recipeId = recipe.id,
                seconds = value.seconds.coerceIn(1, MAX_SECONDS),
                label = value.label,
                stepId = value.stepId,
            )
            startService()
            draft = null
            needsPermission = false
            showManager = true
        }
    }

    fun toggle(timer: CookTimer) = perform {
        if (timer.paused) sessions.resumeTimer(timer.id) else sessions.pauseTimer(timer.id)
    }

    fun remove(timer: CookTimer) = perform { sessions.removeTimer(timer.id) }

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

    private companion object {
        const val MAX_SECONDS = 7L * 86400
    }
}

@Composable
fun rememberCookingTimers(recipe: Recipe, sessions: CookSessionStore, initiallyOpen: Boolean): CookingTimerUi {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val ui = remember(recipe.id, sessions) {
        CookingTimerUi(
            recipe = recipe,
            sessions = sessions,
            scope = scope,
            canNotify = { NotificationManagerCompat.from(context).areNotificationsEnabled() },
            startService = { context.let(CookTimerService::start) },
            initiallyOpen = initiallyOpen,
        )
    }
    val session by sessions.session.collectAsStateWithLifecycle(initialValue = null)
    SideEffect {
        ui.timers = session?.takeIf { it.recipeId == recipe.id }?.timers.orEmpty()
    }
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
    val timers = ui.timers.filter { it.stepId == step.id }
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
private fun TimerItem(timer: CookTimer, ui: CookingTimerUi) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(500)
            }
        }
    }
    val left = timer.remainingMs(now)
    val finished = !timer.paused && left <= 0
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
                    ui.recipe.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        timer.paused -> "已暂停 · ${countdownLabel(left)}"
                        finished -> "时间到"
                        else -> countdownLabel(left)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (!finished) {
                IconButton(onClick = { ui.toggle(timer) }, enabled = !ui.busy) {
                    Icon(
                        if (timer.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        if (timer.paused) "继续计时" else "暂停计时",
                    )
                }
            }
            IconButton(onClick = { ui.remove(timer) }, enabled = !ui.busy) {
                Icon(Icons.Rounded.Close, if (finished) "停止提醒" else "取消计时")
            }
        }
    }
}

@Composable
fun CookingTimerDialogs(ui: CookingTimerUi) {
    val context = LocalContext.current
    val notifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ui.permissionEpoch++ }
    ui.permissionEpoch // Recheck the notification permission when coming back from settings.
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
                    "计时由做菜前台服务维持，切到后台或锁屏也会到点提醒；从最近任务里划掉应用会结束本次做菜。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!ui.canNotify()) {
                    Text("没有通知权限，计时到点时不会有提醒。", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { ui.needsPermission = true }) { Text("开启通知") }
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
            title = { Text("开启计时提醒") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("通知用于显示倒计时和到点提醒；关掉通知后，计时仍会走完，但不会响铃。")
                    OutlinedButton(onClick = {
                        runCatching {
                            if (Build.VERSION.SDK_INT >= 33 && !ui.canNotify()) {
                                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            }
                        }.onFailure { ui.error = "无法打开通知设置，请在系统设置中开启。" }
                    }) { Text(if (ui.canNotify()) "通知已开启" else "开启通知") }
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }.onFailure { ui.error = "无法打开通知设置。" }
                    }) { Text("系统通知设置") }
                    ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val draft = ui.draft
                    if (draft != null) ui.start(draft) else ui.needsPermission = false
                }, enabled = ui.canNotify() && !ui.busy) {
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
