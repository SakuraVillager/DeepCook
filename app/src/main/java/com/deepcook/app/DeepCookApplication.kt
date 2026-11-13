package com.deepcook.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import com.deepcook.app.data.AppContainer

/**
 * Timers need no restore step: [com.deepcook.app.core.model.CookTimer.endsAt] is wall-clock time,
 * so a deadline saved before the process died or the phone rebooted is still correct when the
 * cooking screen or the foreground service reads it back.
 */
class DeepCookApplication : Application(), SingletonImageLoader.Factory {
    internal var cloudEnabled = true
    val container by lazy { AppContainer(this, cloudEnabled) }
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.cloudApi?.http ?: OkHttpClient() })) }
        .build()
}
