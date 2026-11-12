package com.deepcook.app

import android.app.Application
import com.deepcook.app.data.AppContainer

/**
 * Timers need no restore step: [com.deepcook.app.core.model.CookTimer.endsAt] is wall-clock time,
 * so a deadline saved before the process died or the phone rebooted is still correct when the
 * cooking screen or the foreground service reads it back.
 */
class DeepCookApplication : Application() {
    val container by lazy { AppContainer(this) }
}
