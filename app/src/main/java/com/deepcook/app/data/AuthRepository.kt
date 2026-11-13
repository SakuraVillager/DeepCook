package com.deepcook.app.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.deepcook.app.core.model.Account
import com.deepcook.app.core.model.AuthState
import com.deepcook.app.core.model.PasswordPolicy
import com.deepcook.app.core.model.PhoneNumber
import com.deepcook.app.core.model.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** What the UI needs to know about a failed call. [message] is already written for the user. */
sealed interface AuthFailure {
    val message: String

    data object InvalidPhone : AuthFailure {
        override val message = "请输入 11 位手机号"
    }

    data object WeakPassword : AuthFailure {
        override val message = "密码需为 6–128 个字符。"
    }

    data object PhoneTaken : AuthFailure {
        override val message = "这个手机号已经注册，直接登录吧。"
    }

    data object PhoneNotFound : AuthFailure {
        override val message = "这个手机号还没有注册。"
    }

    /** One message for both wrong number and wrong password, so an attacker learns nothing. */
    data object WrongCredentials : AuthFailure {
        override val message = "手机号或密码不正确。"
    }

    data object WrongCurrentPassword : AuthFailure {
        override val message = "当前密码不正确。"
    }

    data class Locked(val seconds: Int) : AuthFailure {
        override val message get() = "尝试次数过多，请 $seconds 秒后再试。"
    }

    data object NotSignedIn : AuthFailure {
        override val message = "登录状态已失效，请重新登录。"
    }

    data class Remote(override val message: String) : AuthFailure

    data object Storage : AuthFailure {
        override val message = "账号暂时无法保存，请重试。"
    }
}

sealed interface AuthResult {
    /** Signed in, and [session] is now stored on the device. */
    data class SignedIn(val session: Session) : AuthResult

    /** Applied without signing in; used by the local password reset. */
    data object Done : AuthResult

    data class Failure(val reason: AuthFailure) : AuthResult
}

/**
 * The seam for accounts.
 *
 * [LocalAuthRepository] keeps everything in one DataStore file. A cloud build only has to provide
 * another implementation of this interface (register / login / reset / change / logout, each
 * returning [AuthResult]) and swap it in [AppContainer]; no screen or view model changes, because
 * they depend on this interface and on `session` / `account` flows only.
 */
interface AuthRepository {
    val cloudAccounts: Boolean get() = false
    suspend fun authenticate(username: String, password: String, register: Boolean, mergeLocal: Boolean): AuthResult =
        if (register) register(username, password) else login(username, password)

    /** Null when logged out. Emits a new value on login, logout and account changes. */
    val session: Flow<Session?>

    val account: Flow<Account?>

    suspend fun importLocal(): AuthResult = AuthResult.Failure(AuthFailure.Remote("当前账号不支持合并本机资料。"))

    suspend fun current(): Session?
    suspend fun register(phone: String, password: String): AuthResult
    suspend fun login(phone: String, password: String): AuthResult
    suspend fun resetPassword(phone: String, newPassword: String): AuthResult
    suspend fun changePassword(currentPassword: String, newPassword: String): AuthResult

    /** False when the file could not be written, so the caller can say so instead of pretending. */
    suspend fun logout(): Boolean
}

object AuthStateSerializer : Serializer<AuthState> {
    override val defaultValue = AuthState()

    override suspend fun readFrom(input: InputStream): AuthState = try {
        json.decodeFromString(AuthState.serializer(), input.readBytes().decodeToString())
    } catch (error: SerializationException) {
        // Keep the unreadable file: the same rule as user_data.json, never silently replace it.
        throw CorruptionException("账号数据无法读取", error)
    }

    override suspend fun writeTo(t: AuthState, output: OutputStream) {
        output.write(json.encodeToString(AuthState.serializer(), t).encodeToByteArray())
    }
}

class LocalAuthRepository(
    private val store: DataStore<AuthState>,
    private val throttle: LoginThrottle = LoginThrottle(),
    private val now: () -> Long = System::currentTimeMillis,
) : AuthRepository {
    override val session: Flow<Session?> = store.data.map { it.session }.distinctUntilChanged()

    override val account: Flow<Account?> = store.data
        .map { state -> state.accountOf(state.session?.accountId) }
        .distinctUntilChanged()

    override suspend fun current(): Session? = read().session

    override suspend fun register(phone: String, password: String): AuthResult {
        val number = PhoneNumber.normalize(phone)
        if (!PhoneNumber.isValid(number)) return AuthResult.Failure(AuthFailure.InvalidPhone)
        if (!PasswordPolicy.isValid(password)) return AuthResult.Failure(AuthFailure.WeakPassword)
        val existing = readOrNull() ?: return AuthResult.Failure(AuthFailure.Storage)
        if (existing.accounts.any { it.phone == number }) return AuthResult.Failure(AuthFailure.PhoneTaken)

        // Hashing happens once, before the write, so a retried write cannot mint two accounts.
        val salt = PasswordHasher.newSalt()
        val hash = hashOffMainThread(password, salt, PasswordHasher.ITERATIONS)
        val createdAt = now()
        val account = Account(
            id = UUID.randomUUID().toString(),
            phone = number,
            salt = salt,
            passwordHash = hash,
            iterations = PasswordHasher.ITERATIONS,
            createdAt = createdAt,
            lastLoginAt = createdAt,
        )
        val signedIn = Session(account.id, number, createdAt)
        var outcome: AuthResult = AuthResult.SignedIn(signedIn)
        val written = write {
            if (it.accounts.any { candidate -> candidate.phone == number }) {
                outcome = AuthResult.Failure(AuthFailure.PhoneTaken)
                it
            } else {
                it.copy(accounts = it.accounts + account, session = signedIn)
            }
        }
        return if (written) outcome else AuthResult.Failure(AuthFailure.Storage)
    }

    override suspend fun login(phone: String, password: String): AuthResult {
        val number = PhoneNumber.normalize(phone)
        if (!PhoneNumber.isValid(number)) return AuthResult.Failure(AuthFailure.InvalidPhone)
        throttle.lockRemainingSeconds(number).takeIf { it > 0 }?.let {
            return AuthResult.Failure(AuthFailure.Locked(it))
        }
        val state = readOrNull() ?: return AuthResult.Failure(AuthFailure.Storage)
        val account = state.accounts.firstOrNull { it.phone == number }
            ?: return AuthResult.Failure(AuthFailure.PhoneNotFound)

        val matches = verifyOffMainThread(password, account)
        if (!matches) {
            throttle.recordFailure(number)
            return AuthResult.Failure(AuthFailure.WrongCredentials)
        }
        throttle.clear(number)

        val issuedAt = now()
        val signedIn = Session(account.id, number, issuedAt)
        val written = write { current ->
            current.copy(
                accounts = current.accounts.map {
                    if (it.id == account.id) it.copy(lastLoginAt = issuedAt) else it
                },
                session = signedIn,
            )
        }
        return if (written) AuthResult.SignedIn(signedIn) else AuthResult.Failure(AuthFailure.Storage)
    }

    /**
     * Local reset: no SMS can be sent from the phone, so this only works for a number that is
     * already registered on this device. It changes the password and leaves the user signed out,
     * the same as a web flow that asks you to log in again afterwards.
     */
    override suspend fun resetPassword(phone: String, newPassword: String): AuthResult {
        val number = PhoneNumber.normalize(phone)
        if (!PhoneNumber.isValid(number)) return AuthResult.Failure(AuthFailure.InvalidPhone)
        if (!PasswordPolicy.isValid(newPassword)) return AuthResult.Failure(AuthFailure.WeakPassword)
        val state = readOrNull() ?: return AuthResult.Failure(AuthFailure.Storage)
        val account = state.accounts.firstOrNull { it.phone == number }
            ?: return AuthResult.Failure(AuthFailure.PhoneNotFound)

        val salt = PasswordHasher.newSalt()
        val hash = hashOffMainThread(newPassword, salt, PasswordHasher.ITERATIONS)
        val written = write { current ->
            current.copy(
                accounts = current.accounts.map {
                    if (it.id == account.id) {
                        it.copy(
                            salt = salt,
                            passwordHash = hash,
                            iterations = PasswordHasher.ITERATIONS,
                        )
                    } else {
                        it
                    }
                },
            )
        }
        if (!written) return AuthResult.Failure(AuthFailure.Storage)
        throttle.clear(number)
        return AuthResult.Done
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): AuthResult {
        if (!PasswordPolicy.isValid(newPassword)) return AuthResult.Failure(AuthFailure.WeakPassword)
        val state = readOrNull() ?: return AuthResult.Failure(AuthFailure.Storage)
        val active = state.session ?: return AuthResult.Failure(AuthFailure.NotSignedIn)
        val account = state.accountOf(active.accountId) ?: return AuthResult.Failure(AuthFailure.NotSignedIn)
        if (!verifyOffMainThread(currentPassword, account)) {
            return AuthResult.Failure(AuthFailure.WrongCurrentPassword)
        }

        val salt = PasswordHasher.newSalt()
        val hash = hashOffMainThread(newPassword, salt, PasswordHasher.ITERATIONS)
        val written = write { current ->
            current.copy(
                accounts = current.accounts.map {
                    if (it.id == account.id) {
                        it.copy(
                            salt = salt,
                            passwordHash = hash,
                            iterations = PasswordHasher.ITERATIONS,
                        )
                    } else {
                        it
                    }
                },
            )
        }
        return if (written) AuthResult.Done else AuthResult.Failure(AuthFailure.Storage)
    }

    override suspend fun logout(): Boolean = write { it.copy(session = null) }

    private suspend fun read(): AuthState = store.data.first()

    /** Null means the file could not be read; callers turn that into [AuthFailure.Storage]. */
    private suspend fun readOrNull(): AuthState? = try {
        read()
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        null
    }

    private suspend fun write(transform: (AuthState) -> AuthState): Boolean = try {
        store.updateData { transform(it) }
        true
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        false
    }

    private suspend fun hashOffMainThread(password: String, salt: String, iterations: Int): String =
        withContext(Dispatchers.Default) { PasswordHasher.hash(password, salt, iterations) }

    private suspend fun verifyOffMainThread(password: String, account: Account): Boolean =
        withContext(Dispatchers.Default) {
            PasswordHasher.verify(password, account.salt, account.iterations, account.passwordHash)
        }
}
