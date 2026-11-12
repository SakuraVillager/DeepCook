package com.deepcook.app.core.model

import kotlinx.serialization.Serializable

/**
 * One account stored on this device.
 *
 * The password is never kept: [passwordHash] is PBKDF2-HMAC-SHA256 over [salt], so a copy of
 * `account.json` cannot be turned back into a password. [iterations] is stored per account so a
 * future build can raise the work factor without invalidating existing accounts.
 */
@Serializable
data class Account(
    val id: String,
    val phone: String,
    val salt: String,
    val passwordHash: String,
    val iterations: Int,
    val createdAt: Long,
    val lastLoginAt: Long? = null,
)

/** The signed-in state that survives a restart. A null session in [AuthState] means "logged out". */
@Serializable
data class Session(val accountId: String, val phone: String, val issuedAt: Long)

@Serializable
data class AuthState(val accounts: List<Account> = emptyList(), val session: Session? = null) {
    fun accountOf(id: String?): Account? = accounts.firstOrNull { it.id == id }
}

/**
 * Mainland mobile numbers only, as the login form asks for a 11-digit number. [normalize] accepts
 * what a person actually types (`+86 138-0013-8000`, spaces, dashes) and reduces it to 11 digits.
 */
object PhoneNumber {
    private val PATTERN = Regex("^1[3-9]\\d{9}$")

    fun normalize(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length == 13 && digits.startsWith("86")) digits.drop(2) else digits
    }

    fun isValid(phone: String): Boolean = PATTERN.matches(phone)

    /** `13800138000` -> `138****8000`; anything unexpected is returned unchanged. */
    fun masked(phone: String): String =
        if (phone.length == 11) "${phone.take(3)}****${phone.takeLast(4)}" else phone
}

/** Rules shared by registration, password reset and change-password. */
object PasswordPolicy {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 32

    /** One human-readable reason, or null when [password] is acceptable. */
    fun problemOf(password: String): String? = when {
        password.length < MIN_LENGTH -> "至少 $MIN_LENGTH 位"
        password.length > MAX_LENGTH -> "不超过 $MAX_LENGTH 位"
        password.any { it.isWhitespace() } -> "不能包含空格"
        password.none { it.isLetter() } -> "至少包含一个字母"
        password.none { it.isDigit() } -> "至少包含一个数字"
        else -> null
    }

    fun isValid(password: String): Boolean = problemOf(password) == null

    const val HINT = "$MIN_LENGTH–$MAX_LENGTH 位，需同时包含字母和数字"
}

/** Limits a logged-out attacker guessing passwords on a stolen, unlocked phone. */
object AuthRules {
    const val MAX_FAILURES = 5
    const val LOCK_SECONDS = 60
}
