package com.deepcook.app.feature.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.deepcook.app.DeepCookApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val manager = (context.applicationContext as DeepCookApplication).container.timers
                val id = intent.getStringExtra("timer_id")
                when (intent.action) {
                    "FIRE" -> if (id != null) manager.deliver(id, intent.getIntExtra("revision", -1))
                    "PAUSE" -> if (id != null) manager.pause(id)
                    "RESUME" -> if (id != null) manager.resume(id)
                    "REMOVE" -> if (id != null) manager.remove(id)
                    else -> manager.restore()
                }
            } catch (error: Exception) {
                android.util.Log.e("CookingTimer", "Timer action could not complete; saved data retained", error)
            } finally {
                pending.finish()
            }
        }
    }
}
