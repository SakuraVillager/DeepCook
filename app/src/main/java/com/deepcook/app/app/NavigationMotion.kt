/*
 * Navigation motion matches Mihon HomeScreen.kt and presentation/util/Navigator.kt
 * (Apache License 2.0). DeepCook adapts the calls to AndroidX Navigation Compose.
 * The transitions themselves come from the same Material Motion Compose library.
 */
package com.deepcook.app.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut

internal const val TAB_FADE_DURATION = 200

private fun NavDestination.homeTab(): Int? = when {
    hasRoute<Discover>() || hasRoute<Search>() -> 0
    hasRoute<Favorites>() -> 1
    hasRoute<Profile>() -> 2
    else -> null
}

// A restored search belongs to Discover. Opening Search is hierarchical, while
// switching between Search and another tab uses the same fade as other tabs.
private fun switchesTabs(initial: NavDestination, target: NavDestination): Boolean {
    val from = initial.homeTab()
    val to = target.homeTab()
    return from != null && to != null && from != to
}

internal fun homeEnterTransition(
    initial: NavDestination,
    target: NavDestination,
    slideDistance: Int,
    forward: Boolean,
): EnterTransition = if (switchesTabs(initial, target)) {
    materialFadeThroughIn(initialScale = 1f, durationMillis = TAB_FADE_DURATION)
} else {
    materialSharedAxisXIn(forward = forward, slideDistance = slideDistance)
}

internal fun homeExitTransition(
    initial: NavDestination,
    target: NavDestination,
    slideDistance: Int,
    forward: Boolean,
): ExitTransition = if (switchesTabs(initial, target)) {
    materialFadeThroughOut(durationMillis = TAB_FADE_DURATION)
} else {
    materialSharedAxisXOut(forward = forward, slideDistance = slideDistance)
}
