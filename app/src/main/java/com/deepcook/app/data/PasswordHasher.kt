package com.deepcook.app.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password hashing for the local account store.
 *
 * PBKDF2-HMAC-SHA256 with a per-account salt and [ITERATIONS] rounds: fast enough to keep login
 * under a fraction of a second on an Android 8 phone, slow enough that guessing from a stolen
 * `account.json` is not practical. Salts and hashes are Base64 so the file stays JSON-friendly.
 */
object PasswordHasher {
    const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    private val random = SecureRandom()

    fun newSalt(): String =
        ByteArray(SALT_BYTES).also { random.nextBytes(it) }.let { Base64.getEncoder().encodeToString(it) }

    fun hash(password: String, salt: String, iterations: Int = ITERATIONS): String {
        val spec = PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), iterations, KEY_BITS)
        return try {
            Base64.getEncoder().encodeToString(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,
            )
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time comparison, so a wrong password cannot be narrowed down by timing. */
    fun verify(password: String, salt: String, iterations: Int, expectedHash: String): Boolean =
        MessageDigest.isEqual(
            hash(password, salt, iterations).toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8),
        )
}
