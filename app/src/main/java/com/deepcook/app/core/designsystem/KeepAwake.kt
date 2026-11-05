package com.deepcook.app.core.designsystem

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Holds the screen on while the user is reading or cooking, and releases the flag when the
 * screen leaves the foreground so the app cannot keep a phone awake from the background.
 */
@Composable
fun KeepAwake(enabled: Boolean) {
    val activity = LocalContext.current.activity()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(enabled, activity, owner) {
        fun update() {
            if (enabled && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner.lifecycle.addObserver(observer)
        update()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
