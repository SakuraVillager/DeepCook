package com.deepcook.app

import com.deepcook.app.data.AppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

internal const val TEST_PHONE = "13800138000"
internal const val TEST_PASSWORD = "cook1234"

/**
 * The app opens on the login page now, so UI tests that exercise the shell need an account first.
 * Idempotent on purpose: the account persists between tests on the same device, and a run that is
 * already signed in does nothing.
 */
internal fun AppContainer.signInForTest() = runBlocking {
    if (auth.session.first() == null) {
        auth.register(TEST_PHONE, TEST_PASSWORD)
    }
}
