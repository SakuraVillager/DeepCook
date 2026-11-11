@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.deepcook.app.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deepcook.app.R
import com.deepcook.app.core.designsystem.ChefMark
import com.deepcook.app.core.model.PasswordPolicy

/**
 * The signed-out entry point: one warm page that switches between sign in, sign up and password
 * reset the way a web login page does, without any navigation stack of its own. The app shell is
 * only composed after [AuthSessionState.isSignedIn] turns true, so there is no way to reach a
 * recipe without an account.
 */
@Composable
fun AuthHost(auth: AuthViewModel, form: AuthFormState) {
    BackHandler(enabled = form.page != AuthPage.LOGIN) { auth.show(AuthPage.LOGIN) }
    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ChefMark(Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "收藏好菜，慢慢下厨。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                // No page transition here on purpose: switching between sign in and sign up is a
                // single card swap, and an instant swap keeps both forms from being on screen at
                // once (which would make the fields ambiguous for accessibility and for tests).
                AuthCard(form.page, form, auth)
                Spacer(Modifier.height(16.dp))
                Text(
                    "账号只保存在这台手机上，不会上传。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AuthCard(page: AuthPage, form: AuthFormState, auth: AuthViewModel) {
    val focus = LocalFocusManager.current
    val passwordFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    fun submit() {
        focus.clearFocus()
        auth.submit()
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = authCardColor(),
        border = BorderStroke(1.dp, authCardBorder()),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(page.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    page.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AuthTextField(
                value = form.phone,
                onValue = auth::phone,
                label = "手机号",
                tag = "auth-phone",
                error = form.phoneError,
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next,
                onImeAction = { passwordFocus.requestFocus() },
            )
            PasswordField(
                value = form.password,
                onValue = auth::password,
                label = if (page == AuthPage.RESET) "新密码" else "密码",
                tag = "auth-password",
                error = form.passwordError,
                hint = if (page == AuthPage.LOGIN) null else PasswordPolicy.HINT,
                imeAction = if (page == AuthPage.LOGIN) ImeAction.Done else ImeAction.Next,
                focusRequester = passwordFocus,
                onImeAction = { if (page == AuthPage.LOGIN) submit() else confirmFocus.requestFocus() },
            )
            if (page == AuthPage.LOGIN) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { auth.show(AuthPage.RESET) },
                        modifier = Modifier.testTag("auth-forgot"),
                    ) { Text("忘记密码？") }
                }
            } else {
                PasswordField(
                    value = form.confirm,
                    onValue = auth::confirm,
                    label = if (page == AuthPage.RESET) "确认新密码" else "确认密码",
                    tag = "auth-confirm",
                    error = form.confirmError,
                    hint = null,
                    imeAction = ImeAction.Done,
                    focusRequester = confirmFocus,
                    onImeAction = { submit() },
                )
            }
            if (page == AuthPage.REGISTER) {
                Row(
                    Modifier.fillMaxWidth().toggleable(
                        value = form.accepted,
                        role = Role.Checkbox,
                        onValueChange = auth::accept,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = form.accepted, onCheckedChange = null)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "我已了解：账号与收藏只保存在这台手机上。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            form.notice?.let { notice ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        notice,
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            form.formError?.let { error ->
                Text(
                    error,
                    Modifier.testTag("auth-error"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = { submit() },
                enabled = !form.submitting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("auth-submit"),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (form.submitting) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(page.submitLabel(form.submitting))
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    page.footerLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { auth.show(page.footerTarget) },
                    modifier = Modifier.testTag("auth-switch"),
                ) { Text(page.footerAction) }
            }
        }
    }
}

/**
 * Shared single-line field. Kept [internal] so the account page can reuse it in its change-password
 * dialog instead of growing a second copy of the same styling.
 */
@Composable
internal fun AuthTextField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    tag: String,
    error: String?,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
) {
    val support: (@Composable () -> Unit)? = if (error == null) null else { { Text(error) } }
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = support,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = imeActions(imeAction, onImeAction),
    )
}

@Composable
internal fun PasswordField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    tag: String,
    error: String?,
    hint: String?,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val support = error ?: hint
    val supportText: (@Composable () -> Unit)? = if (support == null) null else { { Text(support) } }
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth()
            .then(if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester))
            .testTag(tag),
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = supportText,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = imeActions(imeAction, onImeAction),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    if (visible) "隐藏密码" else "显示密码",
                )
            }
        },
    )
}

private fun imeActions(imeAction: ImeAction, action: () -> Unit): KeyboardActions = when (imeAction) {
    ImeAction.Next -> KeyboardActions(onNext = { action() })
    ImeAction.Done -> KeyboardActions(onDone = { action() })
    else -> KeyboardActions.Default
}

@Composable
private fun authCardColor(): Color {
    val colors = MaterialTheme.colorScheme
    return if (colors.background.luminance() > .5f) Color.White else colors.surfaceContainerLow
}

@Composable
private fun authCardBorder(): Color {
    val colors = MaterialTheme.colorScheme
    return if (colors.background.luminance() > .5f) Color(0xFFDDDDDD) else colors.outlineVariant
}

private val AuthPage.title: String
    get() = when (this) {
        AuthPage.LOGIN -> "登录"
        AuthPage.REGISTER -> "注册账号"
        AuthPage.RESET -> "重置密码"
    }

private val AuthPage.subtitle: String
    get() = when (this) {
        AuthPage.LOGIN -> "用手机号和密码继续。"
        AuthPage.REGISTER -> "账号保存在这台手机上，不会上传。"
        AuthPage.RESET -> "输入注册时的手机号，再设一个新密码。"
    }

private fun AuthPage.submitLabel(submitting: Boolean): String {
    if (submitting) return "处理中"
    return when (this) {
        AuthPage.LOGIN -> "登录"
        AuthPage.REGISTER -> "注册并开始"
        AuthPage.RESET -> "重置密码"
    }
}

private val AuthPage.footerLabel: String
    get() = when (this) {
        AuthPage.LOGIN -> "还没有账号？"
        AuthPage.REGISTER -> "已有账号？"
        AuthPage.RESET -> "想起来了？"
    }

private val AuthPage.footerAction: String
    get() = when (this) {
        AuthPage.LOGIN -> "立即注册"
        AuthPage.REGISTER -> "去登录"
        AuthPage.RESET -> "返回登录"
    }

private val AuthPage.footerTarget: AuthPage
    get() = if (this == AuthPage.LOGIN) AuthPage.REGISTER else AuthPage.LOGIN
