@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deepcook.app.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.deepcook.app.core.designsystem.PageTitle
import com.deepcook.app.core.designsystem.PreferenceGroupHeader
import com.deepcook.app.core.designsystem.PreferenceRow
import com.deepcook.app.core.model.PasswordPolicy
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything the signed-in user can do with the account itself: see which number is in use, change
 * the password, and sign out. Reached from 我的 → 用户信息, like a web account page.
 */
@Composable
fun AccountScreen(
    state: AuthSessionState,
    onBack: () -> Unit,
    changePassword: suspend (current: String, next: String) -> String?,
    signOut: suspend () -> String?,
) {
    var passwordDialog by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val format = remember { SimpleDateFormat("yyyy 年 M 月 d 日", Locale.CHINA) }
    val date: (Long?) -> String = { timestamp -> timestamp?.let { format.format(Date(it)) } ?: "无" }

    Scaffold(topBar = { PageTitle("账号与安全", onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            PreferenceGroupHeader("当前账号")
            PreferenceRow(
                state.maskedPhone.ifBlank { "未登录" },
                "登录手机号",
                Icons.Rounded.PhoneAndroid,
            )
            PreferenceRow("注册时间", date(state.account?.createdAt), Icons.Rounded.EventAvailable)
            PreferenceRow("上次登录", date(state.account?.lastLoginAt), Icons.Rounded.History)
            PreferenceGroupHeader("安全")
            PreferenceRow(
                "修改密码",
                "下次登录使用新密码",
                Icons.Rounded.Lock,
                onClick = { passwordDialog = true },
            )
            PreferenceRow(
                "退出登录",
                "返回登录页，本机收藏与设置保留",
                Icons.AutoMirrored.Rounded.Logout,
                onClick = { confirmSignOut = true },
            )
            message?.let { text ->
                Text(
                    text,
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "账号、收藏和设置都保存在这台手机上。换手机、卸载或清除应用数据后无法找回。",
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (passwordDialog) {
        ChangePasswordDialog(
            onDismiss = { passwordDialog = false },
            onSubmit = changePassword,
            onChanged = {
                passwordDialog = false
                message = "密码已更新，下次登录请使用新密码。"
            },
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("退出登录？") },
            text = { Text("需要手机号和密码才能再次进入。本机的收藏、我的菜谱和设置不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    scope.launch { signOut()?.let { message = it } }
                }) { Text("退出登录") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: suspend (current: String, next: String) -> String?,
    onChanged: () -> Unit,
) {
    var current by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("修改密码") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PasswordField(
                    value = current,
                    onValue = {
                        current = it
                        error = null
                    },
                    label = "当前密码",
                    tag = "account-current",
                    error = null,
                    hint = null,
                    imeAction = ImeAction.Next,
                    onImeAction = {},
                )
                PasswordField(
                    value = next,
                    onValue = {
                        next = it
                        error = null
                    },
                    label = "新密码",
                    tag = "account-new",
                    error = null,
                    hint = PasswordPolicy.HINT,
                    imeAction = ImeAction.Next,
                    onImeAction = {},
                )
                PasswordField(
                    value = confirm,
                    onValue = {
                        confirm = it
                        error = null
                    },
                    label = "确认新密码",
                    tag = "account-confirm",
                    error = null,
                    hint = null,
                    imeAction = ImeAction.Done,
                    onImeAction = {},
                )
                error?.let {
                    Text(
                        it,
                        Modifier.testTag("account-error"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        val failure = if (next != confirm) "两次输入的新密码不一致" else onSubmit(current, next)
                        saving = false
                        if (failure == null) onChanged() else error = failure
                    }
                },
            ) { Text(if (saving) "保存中" else "保存") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("取消") }
        },
    )
}
