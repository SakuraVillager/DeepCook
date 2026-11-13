package com.deepcook.app.data.cloud

import com.deepcook.app.core.model.Account
import com.deepcook.app.core.model.Session
import com.deepcook.app.core.model.Username
import com.deepcook.app.core.model.PasswordPolicy
import com.deepcook.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class CloudAuthRepository(
    private val api: CloudApi,
    private val kitchen: CloudKitchen,
    private val guest: UserDataRepository,
    private val guestRecipes: UserRecipeStore,
    private val guestCovers: CoverOverrides,
    private val scope: CoroutineScope,
    private val importSocial: suspend () -> Unit = {},
) : AuthRepository {
    override val cloudAccounts = true
    private val state = MutableStateFlow<Pair<Session?, Account?>>(null to null)
    private val restored = CompletableDeferred<Unit>()
    private val mutex = Mutex()
    override val session = flow { restored.await(); emitAll(state.map { it.first }.distinctUntilChanged()) }
    override val account = flow { restored.await(); emitAll(state.map { it.second }.distinctUntilChanged()) }

    init {
        api.onExpired = { scope.launch { kitchen.deactivate(); state.value = null to null } }
        scope.launch {
            try {
                val saved = api.credentials
                if (saved.account != null && saved.cookie != null && saved.expiresAt > System.currentTimeMillis()) {
                    try {
                        val response = api.request("/auth/session", expected = null)
                        if (response["account"] is JsonNull) { api.clear(); return@launch }
                        val remote = com.deepcook.app.data.json.decodeFromJsonElement<CloudAccount>(response.getValue("account"))
                        if (remote.id != saved.account.id) { api.clear(); return@launch }
                        kitchen.activate(remote.id)
                        publish(remote, saved.issuedAt)
                    } catch (error: CloudException) {
                        if (error.status == 0 || error.status == 429 || error.status >= 500) { kitchen.activate(saved.account.id); publish(saved.account, saved.issuedAt) }
                        else api.clear()
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            } finally { restored.complete(Unit) }
        }
    }
    private fun publish(account: CloudAccount, issued: Long = api.credentials.issuedAt) {
        state.value = Session(account.id, account.username, issued) to Account(
            id = account.id, phone = account.username, salt = "", passwordHash = "", iterations = 0,
            createdAt = account.createdAt, lastLoginAt = issued, cloud = true,
        )
    }
    override suspend fun current(): Session? { restored.await(); return state.value.first }
    override suspend fun register(phone: String, password: String) = authenticate(phone, password, true, false)
    override suspend fun login(phone: String, password: String) = authenticate(phone, password, false, false)

    override suspend fun authenticate(username: String, password: String, register: Boolean, mergeLocal: Boolean): AuthResult = mutex.withLock {
        restored.await()
        if (!Username.isValid(username)) return@withLock AuthResult.Failure(AuthFailure.Remote("用户名需为 3–24 位字母、数字或下划线。"))
        if (!PasswordPolicy.isValid(password)) return@withLock AuthResult.Failure(AuthFailure.WeakPassword)
        try {
            val response = api.request(if (register) "/auth/register" else "/auth/login", "POST", buildJsonObject {
                put("username", username); put("password", password); put("claimGuest", mergeLocal)
                if (register && mergeLocal) put("name", guest.data.first().profile.displayName)
            }, expected = null)
            val account = json.decodeFromJsonElement<CloudAccount>(response.getValue("account"))
            val confirmed = api.request("/auth/session", expected = null)
            require(confirmed["account"]?.jsonObject?.get("id")?.jsonPrimitive?.content == account.id) { "云端未能保存登录状态，请重试。" }
            api.setAccount(account)
            kitchen.activate(account.id)
            if (mergeLocal) {
                try { mergeGuest() }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    kitchen.syncError.value = "已登录，但本机资料尚未全部并入；可在账号页面继续合并。" + (error.message ?: "")
                }
            }
            publish(account)
            AuthResult.SignedIn(state.value.first!!)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            AuthResult.Failure(AuthFailure.Remote(error.message ?: "云端登录未完成，请重试。"))
        }
    }
    private suspend fun mergeGuest() {
        kitchen.importGuest(guest.data.first(), guestRecipes.recipes.first(), guestCovers.entries.first())
        importSocial()
    }
    override suspend fun importLocal(): AuthResult = mutex.withLock {
        try { mergeGuest(); kitchen.syncError.value = null; AuthResult.Done }
        catch (error: Exception) {
            if (error is CancellationException) throw error
            AuthResult.Failure(AuthFailure.Remote(error.message ?: "合并未完成，原件仍在本机。"))
        }
    }
    override suspend fun resetPassword(phone: String, newPassword: String) = AuthResult.Failure(AuthFailure.Remote("暂未提供密码找回服务。"))
    override suspend fun changePassword(currentPassword: String, newPassword: String): AuthResult {
        if (!PasswordPolicy.isValid(newPassword)) return AuthResult.Failure(AuthFailure.WeakPassword)
        return try {
            val id = api.accountId ?: return AuthResult.Failure(AuthFailure.NotSignedIn)
            api.request("/auth/change-password", "POST", buildJsonObject {
                put("currentPassword", currentPassword); put("newPassword", newPassword)
            }, expected = id)
            AuthResult.Done
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            AuthResult.Failure(AuthFailure.Remote(error.message ?: "密码未能更新，请重试。"))
        }
    }
    override suspend fun logout(): Boolean = mutex.withLock {
        try {
            api.request("/auth/logout", "POST")
            api.clear(); kitchen.deactivate(); state.value = null to null
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            false
        }
    }
}
