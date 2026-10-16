package com.deepcook.app

import android.app.Application
import com.deepcook.app.data.AppContainer

class DeepCookApplication : Application() {
    val container by lazy { AppContainer(this) }
}
