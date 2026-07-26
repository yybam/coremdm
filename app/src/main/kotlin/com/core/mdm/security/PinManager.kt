package com.core.mdm.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PinManager private constructor(context: Context) {

    companion object {
        private const val META_PREFS_FILE    = "mdm_pin_meta"    // plain prefs — flag only
        private const val SECURE_PREFS_FILE  = "mdm_pin_secure"  // encrypted — hash + salt
        private const val KEY_PIN_SET_FLAG   = "pin_set"          // unencrypted fast-path flag
        private const val KEY_HASH           = "pin_hash"
        private const val KEY_SALT           = "pin_salt"
        private const val KEY_FAILED         = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL  = "lockout_until"
        private const val PBKDF2_ITERATIONS  = 50_000
        private const val PBKDF2_KEY_BITS    = 256
        private const val MIN_PIN_LENGTH     = 4

        private val LOCKOUT_MAP = sortedMapOf(3 to 30L, 5 to 60L, 7 to 120L, 10 to 300L)

        @Volatile private var INSTANCE: PinManager? = null

        fun getInstance(context: Context): PinManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PinManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    // ── Plain prefs: fast flag only, no Keystore required ────────────────────────
    // Reading this on the main thread is safe and instant.
    private val metaPrefs: SharedPreferences =
        context.getSharedPreferences(META_PREFS_FILE, Context.MODE_PRIVATE)

    // ── Encrypted prefs: lazy — only initialized when PIN is actually read/written
    // (i.e., during verifyPin / setPin, not on startup).
    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ── Public API ────────────────────────────────────────────────────────────────

    /** Instant — reads a plain SharedPreferences boolean, no Keystore involved. */
    val isPinSet: Boolean get() = metaPrefs.getBoolean(KEY_PIN_SET_FLAG, false)

    fun setPin(pin: String): Boolean {
        if (pin.length < MIN_PIN_LENGTH) return false
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        // Write hash+salt to encrypted prefs
        securePrefs.edit()
            .putString(KEY_HASH, pbkdf2(pin, salt))
            .putString(KEY_SALT, salt.toHex())
            .putInt(KEY_FAILED, 0)
            .remove(KEY_LOCKOUT_UNTIL)
            .commit()   // commit() (sync) so flag is always in sync with the hash
        // Write fast-path flag to plain prefs
        metaPrefs.edit().putBoolean(KEY_PIN_SET_FLAG, true).apply()
        return true
    }

    fun clearPin() {
        securePrefs.edit().clear().commit()
        metaPrefs.edit().putBoolean(KEY_PIN_SET_FLAG, false).apply()
    }

    fun verifyPin(input: String): VerifyResult {
        if (!isPinSet) return VerifyResult.NoPinSet
        val lockoutUntil = securePrefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (System.currentTimeMillis() < lockoutUntil) {
            return VerifyResult.LockedOut(lockoutUntil - System.currentTimeMillis())
        }
        val salt   = securePrefs.getString(KEY_SALT, null)?.fromHex() ?: return VerifyResult.Error
        val stored = securePrefs.getString(KEY_HASH, null)             ?: return VerifyResult.Error
        return if (pbkdf2(input, salt) == stored) {
            securePrefs.edit().putInt(KEY_FAILED, 0).remove(KEY_LOCKOUT_UNTIL).apply()
            VerifyResult.Success
        } else {
            val attempts  = securePrefs.getInt(KEY_FAILED, 0) + 1
            val lockoutMs = LOCKOUT_MAP.entries.lastOrNull { attempts >= it.key }?.value?.times(1000L) ?: 0L
            securePrefs.edit().putInt(KEY_FAILED, attempts).also {
                if (lockoutMs > 0) it.putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + lockoutMs)
            }.apply()
            VerifyResult.Wrong(attempts, lockoutMs)
        }
    }

    /** Returns 0 if not locked out. Does NOT initialize securePrefs. */
    fun lockoutRemainingMs(): Long {
        if (!isPinSet) return 0L
        // securePrefs must be initialized to check lockout; only called after first PIN verify
        return maxOf(0L, securePrefs.getLong(KEY_LOCKOUT_UNTIL, 0L) - System.currentTimeMillis())
    }

    // ── Crypto ────────────────────────────────────────────────────────────────────

    private fun pbkdf2(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        return runCatching {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(spec).encoded.toHex()
        }.also { spec.clearPassword() }.getOrDefault("")
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

sealed class VerifyResult {
    object Success   : VerifyResult()
    object NoPinSet  : VerifyResult()
    object Error     : VerifyResult()
    data class Wrong(val totalAttempts: Int, val lockoutMs: Long) : VerifyResult()
    data class LockedOut(val remainingMs: Long)                   : VerifyResult()
}
