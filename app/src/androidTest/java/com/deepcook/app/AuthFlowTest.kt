package com.deepcook.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deepcook.app.core.model.FoodSetup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = (compose.activity.application as DeepCookApplication).container

    @Before fun signedOut() {
        runBlocking {
            container.auth.logout()
            // Skip onboarding so the assertions can look for the home page.
            container.userData.updateFoodSetup { FoodSetup(completed = true) }
        }
        awaitLogin()
    }

    private fun awaitLogin() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("auth-submit").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitRegisterForm() {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("auth-confirm").fetchSemanticsNodes().size == 1 }
    }

    private fun awaitHome() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("精选菜谱").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun emptyFormShowsFieldErrorAndWritesNothing() {
        compose.onNodeWithTag("auth-submit").performClick()
        compose.onNodeWithText("请输入 11 位手机号").assertIsDisplayed()
        assertNull(runBlocking { container.auth.session.first() })
    }

    @Test fun registerThenSignOutThenSignInWithTheSamePassword() {
        val phone = "139" + (10_000_000..99_999_999).random()
        compose.onNodeWithTag("auth-switch").performClick()
        awaitRegisterForm()
        compose.onNodeWithTag("auth-phone").performTextInput(phone)
        compose.onNodeWithTag("auth-password").performTextInput(TEST_PASSWORD)
        compose.onNodeWithTag("auth-confirm").performTextInput(TEST_PASSWORD)
        compose.onNodeWithText("我已了解：账号与收藏只保存在这台手机上。").performClick()
        compose.onNodeWithTag("auth-submit").performClick()
        awaitHome()

        val signedIn = runBlocking { container.auth.session.first() }
        assertNotNull("registering must also sign the account in", signedIn)
        assertEquals(phone, signedIn?.phone)

        runBlocking { container.auth.logout() }
        awaitLogin()

        compose.onNodeWithTag("auth-phone").performTextInput(phone)
        compose.onNodeWithTag("auth-password").performTextInput(TEST_PASSWORD)
        compose.onNodeWithTag("auth-submit").performClick()
        awaitHome()
        assertEquals(phone, runBlocking { container.auth.session.first() }?.phone)
    }

    @Test fun wrongPasswordKeepsTheUserOut() {
        container.signInForTest()
        runBlocking { container.auth.logout() }
        awaitLogin()
        compose.onNodeWithTag("auth-phone").performTextInput(TEST_PHONE)
        compose.onNodeWithTag("auth-password").performTextInput("wrong1234")
        compose.onNodeWithTag("auth-submit").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("手机号或密码不正确。").fetchSemanticsNodes().isNotEmpty()
        }
        assertNull(runBlocking { container.auth.session.first() })
    }
}
