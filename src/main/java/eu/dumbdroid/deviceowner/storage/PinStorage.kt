package eu.dumbdroid.deviceowner.storage

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

class PinStorage(context: Context) {

    private val preferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    fun isPinSet(): Boolean =
        preferences.contains(KEY_PIN_HASH) && preferences.contains(KEY_PIN_SALT)

    fun savePin(pin: String) {
        val salt = ByteArray(SALT_LENGTH).also(secureRandom::nextBytes)
        val hash = hash(pin, salt)
        preferences.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val hash = preferences.getString(KEY_PIN_HASH, null) ?: return false
        val saltEncoded = preferences.getString(KEY_PIN_SALT, null) ?: return false
        val salt = Base64.decode(saltEncoded, Base64.NO_WRAP)
        return hash(pin, salt) == hash
    }

    fun isRestrictionEnabled(): Boolean =
        preferences.getBoolean(KEY_RESTRICTION_ENABLED, false)

    fun setRestrictionEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_RESTRICTION_ENABLED, enabled).apply()
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val hashed = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashed, Base64.NO_WRAP)
    }

    companion object {
        private const val PREF_NAME = "device_owner_pin"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_RESTRICTION_ENABLED = "restriction_enabled"
        private const val SALT_LENGTH = 16
    }
}
