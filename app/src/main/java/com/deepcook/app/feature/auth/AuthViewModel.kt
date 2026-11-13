package com.deepcook.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcook.app.core.model.Account
import com.deepcook.app.core.model.PasswordPolicy
import com.deepcook.app.core.model.PhoneNumber
import com.deepcook.app.core.model.Session
import com.deepcook.app.core.model.Username
import com.deepcook.app.data.AuthRepository
import com.deepcook.app.data.AuthResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The three web-style pages: sign in, sign up, and the "forgot password" recovery. */
enum class AuthPage { LOGIN, REGISTER, RESET }

data class AuthFormState(
    val page: AuthPage = AuthPage.LOGIN,
    val phone: String = "",
    val password: String = "",
    val confirm: String = "",
    /** Registration asks the user to acknowledge that the account stays on this phone. */
    val accepted: Boolean = false,
    val phoneError: String? = null,
    val passwordError: String? = null,
    val confirmError: String? = null,
    /** A failure that belongs to the request rather than to one field. */
    val formError: String? = null,
    val notice: String? = null,
    val submitting: Boolean = false,
)

data class AuthSessionState(
    /** False until `account.json` has been read, so the app does not flash the login page. */
    val loaded: Boolean = false,
    val session: Session? = null,
    val account: Account? = null,
) {
    val isSignedIn: Boolean get() = session != null

    val maskedPhone: String get() = session?.phone?.let { if (account?.cloud == true) it else PhoneNumber.masked(it) }.orEmpty()
}

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {
    val cloudAccounts: Boolean get() = auth.cloudAccounts
    private val mutableForm = MutableStateFlow(AuthFormState())
    val form = mutableForm.asStateFlow()

    private val mutableSession = MutableStateFlow(AuthSessionState())
    val session = mutableSession.asStateFlow()

    init {
        viewModelScope.launch {
            combine(auth.session, auth.account) { active, account ->
                AuthSessionState(loaded = true, session = active, account = account)
            }.catch { error ->
                if (error is CancellationException) throw error
                // The file is left untouched; the user can still register a second account.
                mutableSession.value = AuthSessionState(loaded = true)
                mutableForm.value = AuthFormState(formError = "账号数据暂时无法读取，请重新登录。")
            }.collect { next ->
                val wasSignedIn = mutableSession.value.isSignedIn
                mutableSession.value = next
                // A sign-out returns to an empty login form, never to the previous password.
                if (wasSignedIn && !next.isSignedIn) mutableForm.value = AuthFormState()
            }
        }
    }

    /** Switching page clears the form but keeps the number, which is the one thing worth reusing. */
    fun show(page: AuthPage) = editForm {
        AuthFormState(
            page = page,
            accepted = cloudAccounts && page == AuthPage.REGISTER,
            phone = it.phone,
            notice = if (page == AuthPage.LOGIN) it.notice else null,
        )
    }

    fun phone(value: String) = editForm {
        it.copy(phone = if (cloudAccounts) value.take(24) else PhoneNumber.normalize(value).take(PHONE_DIGITS), phoneError = null, formError = null)
    }

    fun password(value: String) = editForm {
        it.copy(password = value, passwordError = null, formError = null)
    }

    fun confirm(value: String) = editForm {
        it.copy(confirm = value, confirmError = null, formError = null)
    }

    fun accept(value: Boolean) = editForm { it.copy(accepted = value, formError = null) }

    fun submit() {
        val state = mutableForm.value
        if (state.submitting) return
        val number = if (cloudAccounts) state.phone.trim() else PhoneNumber.normalize(state.phone)
        val phoneError = if (cloudAccounts) {
            if (Username.isValid(number)) null else "用户名需为 3–24 位字母、数字或下划线。"
        } else if (PhoneNumber.isValid(number)) null else "请输入 11 位手机号"
        val passwordError = when {
            state.password.isEmpty() -> "请输入密码"
            state.page == AuthPage.LOGIN -> null
            else -> PasswordPolicy.problemOf(state.password)?.let { "密码$it" }
        }
        val confirmError =
            if (state.page != AuthPage.LOGIN && state.confirm != state.password) "两次输入的密码不一致" else null
        val formError =
            if (!cloudAccounts && state.page == AuthPage.REGISTER && !state.accepted) "请先确认账号只保存在这台手机上" else null
        if (phoneError != null || passwordError != null || confirmError != null || formError != null) {
            editForm {
                it.copy(
                    phoneError = phoneError,
                    passwordError = passwordError,
                    confirmError = confirmError,
                    formError = formError,
                )
            }
            return
        }
        viewModelScope.launch {
            editForm { it.copy(submitting = true, formError = null, notice = null) }
            val result = when (state.page) {
                AuthPage.LOGIN -> auth.authenticate(number, state.password, false, state.accepted)
                AuthPage.REGISTER -> auth.authenticate(number, state.password, true, state.accepted)
                AuthPage.RESET -> auth.resetPassword(number, state.password)
            }
            when (result) {
                // Signed in: the session flow takes the app to onboarding or the home page.
                is AuthResult.SignedIn -> editForm { AuthFormState() }
                AuthResult.Done -> editForm {
                    AuthFormState(
                        phone = number,
                        notice = "密码已更新，请用新密码登录。",
                    )
                }
                is AuthResult.Failure -> editForm {
                    it.copy(
                        submitting = false,
                        password = "",
                        confirm = "",
                        formError = result.reason.message,
                    )
                }
            }
        }
    }

    /** Null on success; a ready-to-show message on failure. */
    suspend fun changePassword(current: String, next: String): String? {
        if (current.isEmpty()) return "请输入当前密码"
        PasswordPolicy.problemOf(next)?.let { return "新密码$it" }
        if (current == next) return "新密码不能与当前密码相同"
        val result = auth.changePassword(current, next)
        return if (result is AuthResult.Failure) result.reason.message else null
    }

    suspend fun importLocal(): String? {
        val result = auth.importLocal()
        return if (result is AuthResult.Failure) result.reason.message else null
    }

    suspend fun signOut(): String? = if (auth.logout()) null else "退出登录未完成，请重试。"

    private fun editForm(transform: (AuthFormState) -> AuthFormState) {
        mutableForm.value = transform(mutableForm.value)
    }

    private companion object {
        const val PHONE_DIGITS = 11
    }
}
