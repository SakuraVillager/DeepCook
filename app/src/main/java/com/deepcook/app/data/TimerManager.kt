package com.deepcook.app.data

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.net.toUri
import com.deepcook.app.MainActivity
import com.deepcook.app.R
import com.deepcook.app.core.model.*
import com.deepcook.app.feature.timer.TimerReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** Deadlines live in SQLite and AlarmManager, not a coroutine or a process that must stay alive. */
class TimerManager(private val context: Context, private val database: KitchenDatabase) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val mutex = Mutex()
    private val mutableTimers = MutableStateFlow<List<CookingTimer>>(emptyList())
    val timers = mutableTimers.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    init {
        channels()
    }

    fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    fun notificationsAllowed(): Boolean = notifications.areNotificationsEnabled() &&
        notifications.getNotificationChannel(ALERT_CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE &&
        (
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            )

    private fun read(): List<CookingTimer> =
        database.readableDatabase.rawQuery("SELECT payload FROM timers", null).use { c ->
            buildList { while (c.moveToNext()) add(json.decodeFromString<CookingTimer>(c.getString(0))) }
        }

    private fun save(timer: CookingTimer) {
        database.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO timers VALUES(?,?)",
            arrayOf(timer.id, json.encodeToString(timer)),
        )
    }

    private fun publish() {
        mutableTimers.value = read()
        mutableError.value = null
    }

    private suspend fun locked(action: () -> Unit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                action()
                publish()
            } catch (error: Exception) {
                android.util.Log.e("CookingTimer", "Timer operation failed; saved state retained", error)
                mutableError.value = "计时器保存或提醒安排失败，请重试。"
                throw error
            }
        }
    }

    suspend fun start(recipe: Recipe, stepId: String?, label: String, seconds: Long) = locked {
        require(seconds in 1..7 * 86400)
        check(exactAllowed() && notificationsAllowed()) { "需要通知和精确闹钟权限" }
        val duration = seconds * 1000
        val timer = CookingTimer(
            UUID.randomUUID().toString(), recipe.id, recipe.title, stepId, label.ifBlank { recipe.title }.take(80),
            duration, System.currentTimeMillis() + duration, SystemClock.elapsedRealtime() + duration,
            bootCount(), duration, TimerStatus.RUNNING,
        )
        save(timer)
        try {
            schedule(timer)
            show(timer)
        } catch (error: Exception) {
            alarms.cancel(alarmIntent(timer))
            database.writableDatabase.delete("timers", "id=?", arrayOf(timer.id))
            throw error
        }
    }

    suspend fun pause(id: String) = locked {
        val old = read().find { it.id == id && it.status == TimerStatus.RUNNING } ?: return@locked
        val left = remaining(old)
        if (left == 0L) {
            finish(old)
            return@locked
        }
        val timer = old.copy(status = TimerStatus.PAUSED, pausedMs = left, revision = old.revision + 1)
        save(timer)
        alarms.cancel(alarmIntent(old))
        show(timer)
    }

    suspend fun resume(id: String) = locked {
        check(exactAllowed() && notificationsAllowed()) { "需要重新开启提醒权限" }
        val old = read().find { it.id == id && it.status == TimerStatus.PAUSED } ?: return@locked
        val timer = old.copy(
            status = TimerStatus.RUNNING,
            endWall = System.currentTimeMillis() + old.pausedMs,
            endElapsed = SystemClock.elapsedRealtime() + old.pausedMs,
            boot = bootCount(),
            revision = old.revision + 1,
        )
        save(timer)
        try {
            schedule(timer)
            show(timer)
        } catch (error: Exception) {
            save(old)
            throw error
        }
    }

    suspend fun remove(id: String) = locked {
        val timer = read().find { it.id == id } ?: return@locked
        database.writableDatabase.delete("timers", "id=?", arrayOf(id))
        alarms.cancel(alarmIntent(timer))
        notifications.cancel(id, 0)
    }

    suspend fun deliver(id: String, revision: Int) = locked {
        val timer =
            read().find { it.id == id && it.revision == revision && it.status == TimerStatus.RUNNING } ?: return@locked
        if (remaining(timer) > 0) schedule(timer) else finish(timer)
    }

    suspend fun restore() = locked {
        read().forEach { timer ->
            when (timer.status) {
                TimerStatus.RUNNING -> if (remaining(timer) <= 0) {
                    finish(timer)
                } else {
                    val recovered = if (timer.boot != bootCount()) {
                        val left = remaining(timer)
                        timer.copy(
                            boot = bootCount(),
                            endElapsed = SystemClock.elapsedRealtime() + left,
                            endWall = System.currentTimeMillis() + left,
                            revision = timer.revision + 1,
                        ).also(::save)
                    } else {
                        timer
                    }
                    if (exactAllowed()) schedule(recovered)
                    show(recovered)
                }
                TimerStatus.PAUSED -> show(timer)
                TimerStatus.FINISHED -> Unit // Never replay a completed alarm just because the UI opens.
            }
        }
    }

    private fun remaining(timer: CookingTimer) =
        timer.remaining(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount())

    private fun finish(timer: CookingTimer) {
        val done = timer.copy(status = TimerStatus.FINISHED)
        save(done)
        show(done)
    }

    private fun alarmIntent(timer: CookingTimer): PendingIntent = action(timer, "FIRE")

    private fun action(timer: CookingTimer, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TimerReceiver::class.java).setAction(action)
            .setData("deepcook://timer/${timer.id}/$action".toUri())
            .putExtra("timer_id", timer.id).putExtra("revision", timer.revision),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun open(timer: CookingTimer): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .setData("deepcook://timer/${timer.id}".toUri()).putExtra("timer_recipe", timer.recipeId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun schedule(timer: CookingTimer) {
        // Alarm-clock alarms are user-visible and aren't batched by Doze, even for nearby timers.
        alarms.setAlarmClock(
            AlarmManager.AlarmClockInfo(System.currentTimeMillis() + remaining(timer), open(timer)),
            alarmIntent(timer),
        )
    }

    private fun show(timer: CookingTimer) {
        if (!notificationsAllowed()) return
        val done = timer.status == TimerStatus.FINISHED
        val paused = timer.status == TimerStatus.PAUSED
        val builder = Notification.Builder(context, if (done) ALERT_CHANNEL else RUNNING_CHANNEL)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(if (done) "${timer.label} · 时间到" else timer.label)
            .setContentText(if (done) timer.recipeTitle else "${timer.recipeTitle} · ${if (paused) "已暂停" else "计时中"}")
            .setContentIntent(open(timer)).setOngoing(true)
            .setCategory(if (done) Notification.CATEGORY_ALARM else Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (!done) {
            if (!paused) {
                builder.setWhen(
                    System.currentTimeMillis() + remaining(timer),
                ).setUsesChronometer(true).setChronometerCountDown(true)
            } else {
                builder.setSubText(countdownLabel(timer.pausedMs))
            }
            builder.setOnlyAlertOnce(true).addAction(
                Notification.Action.Builder(
                    null,
                    if (paused) "继续" else "暂停",
                    action(timer, if (paused) "RESUME" else "PAUSE"),
                ).build(),
            )
        }
        builder.addAction(
            Notification.Action.Builder(null, if (done) "停止提醒" else "取消", action(timer, "REMOVE")).build(),
        )
        val notification = builder.build()
        if (done) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        notifications.notify(timer.id, 0, notification)
    }

    private fun channels() {
        notifications.createNotificationChannel(
            NotificationChannel(RUNNING_CHANNEL, "正在计时", NotificationManager.IMPORTANCE_LOW),
        )
        notifications.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, "厨房计时到点提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "烹饪计时结束时播放提醒声音，可在通知中停止。"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(
                            AudioAttributes.USAGE_ALARM,
                        ).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                enableVibration(true)
            },
        )
    }

    companion object {
        const val RUNNING_CHANNEL = "cooking_timer_running"
        const val ALERT_CHANNEL = "cooking_timer_alert"
    }
}
