package com.deepcook.app

import android.app.Application
import com.deepcook.app.data.AppContainer
import kotlinx.coroutines.launch

class DeepCookApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        ).launch {
            runCatching { container.timers.restore() }
                .onFailure { android.util.Log.e("CookingTimer", "Unable to restore saved timers", it) }
        }
    }
}
