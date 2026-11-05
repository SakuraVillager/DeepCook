package com.deepcook.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.deepcook.app.DeepCookApplication
import com.deepcook.app.MainActivity
import com.deepcook.app.R
import com.deepcook.app.core.model.CookSession
import com.deepcook.app.core.model.CookTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps a cooking session alive while the user is away from the screen: an ongoing notification
 * with the current step, and one wake-up per running timer.
 *
 * The timers themselves are stored in [CookSessionStore] (not in this service), so the screen and
 * the notification always read the same list and a service restart can reschedule from disk.
 */
class CookTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ringJobs = mutableMapOf<String, Job>()
    private lateinit var sessions: CookSessionStore
    private lateinit var notifications: NotificationManager
    private var current: CookSession? = null

    override fun onCreate() {
        super.onCreate()
        sessions = (application as DeepCookApplication).container.cookSessions
        notifications = getSystemService(NotificationManager::class.java)
        createChannels()
        startForeground(FOREGROUND_ID, sessionNotification(null))
        scope.launch {
            sessions.session.collect(::applySession)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    /** Removing the app from recents ends the session, as the user asked: nothing keeps calling. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { sessions.clear() }
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun applySession(session: CookSession?) {
        current = session
        if (session == null) {
            ringJobs.keys.toList().forEach { id -> ringJobs.remove(id)?.cancel() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val live = session.timers.associateBy { it.id }
        ringJobs.keys.filterNot { it in live }.forEach { id -> ringJobs.remove(id)?.cancel() }
        live.values.filterNot { it.id in ringJobs }.forEach(::schedule)
        notifications.notify(FOREGROUND_ID, sessionNotification(session))
    }

    private fun schedule(timer: CookTimer) {
        ringJobs[timer.id] = scope.launch {
            val wait = timer.endsAt - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            notifications.notify(timer.id.hashCode(), alarmNotification(timer))
            // Drop the entry before the store emits, so applySession does not cancel this job.
            ringJobs.remove(timer.id)
            runCatching { sessions.removeTimer(timer.id) }
        }
    }

    private fun sessionNotification(session: CookSession?): Notification {
        val builder = NotificationCompat.Builder(this, SESSION_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(openIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (session == null) {
            return builder.setContentTitle("DeepCook").setContentText("准备中…").build()
        }
        val step = "第 ${session.stepIndex + 1} 步"
        val next = session.pendingTimers(System.currentTimeMillis()).firstOrNull()
        builder.setContentTitle(session.recipeTitle.ifBlank { "正在做菜" })
        if (next == null) {
            builder.setContentText("$step · 没有计时器")
        } else {
            builder.setContentText("$step · ${session.timers.size} 个计时器")
                .setWhen(next.endsAt)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        return builder.build()
    }

    private fun alarmNotification(timer: CookTimer): Notification = NotificationCompat.Builder(this, ALARM_CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle("计时结束")
        .setContentText("「${timer.label}」到点了")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setAutoCancel(true)
        .setContentIntent(openIntent())
        .build()

    private fun openIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannels() {
        val session = NotificationChannel(SESSION_CHANNEL, "做菜进行中", NotificationManager.IMPORTANCE_LOW).apply {
            description = "做菜时的常驻提醒，含当前步骤与计时"
            setShowBadge(false)
        }
        val alarm = NotificationChannel(ALARM_CHANNEL, "计时提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "计时到点的响铃与振动"
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        notifications.createNotificationChannels(listOf(session, alarm))
    }

    companion object {
        private const val SESSION_CHANNEL = "cooking_session"
        private const val ALARM_CHANNEL = "cooking_alarm"
        private const val FOREGROUND_ID = 4101

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CookTimerService::class.java))
        }
    }
}
