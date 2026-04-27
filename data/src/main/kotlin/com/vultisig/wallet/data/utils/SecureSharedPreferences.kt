package com.vultisig.wallet.data.utils

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEYSTORE = "AndroidKeyStore"
/** Key alias used to identify the AES-256-GCM key in the AndroidKeyStore. */
internal const val SECURE_PREFS_KEY_ALIAS = "vultisig_secure_prefs_key"
private const val IV_LENGTH = 12
private const val GCM_TAG_BITS = 128

private const val P_STRING = "s:"
private const val P_BOOL = "b:"
private const val P_INT = "i:"
private const val P_LONG = "l:"
private const val P_FLOAT = "f:"

private val secureKeyLock = Any()

/**
 * Builds or retrieves the AES-256-GCM AndroidKeyStore key used to encrypt preference values.
 * StrongBox is not requested to avoid keystore-daemon stalls on certain Pixel/Samsung devices.
 */
internal fun buildSecurePrefsKey(): SecretKey =
    synchronized(secureKeyLock) {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(SECURE_PREFS_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return@synchronized it.secretKey
        }
        val spec =
            KeyGenParameterSpec.Builder(
                    SECURE_PREFS_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

/** Encrypts [plaintext] with AES-256-GCM and returns `Base64(IV || ciphertext)`. */
private fun encryptRaw(secretKey: SecretKey, plaintext: String): String {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val iv = cipher.iv
    check(iv.size == IV_LENGTH) { "Unexpected GCM IV length: ${iv.size}" }
    val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    val out = ByteArray(IV_LENGTH + ct.size)
    iv.copyInto(out)
    ct.copyInto(out, IV_LENGTH)
    return Base64.encodeToString(out, Base64.NO_WRAP)
}

/** Decodes and decrypts a value previously produced by [encryptRaw]. */
private fun decryptRaw(secretKey: SecretKey, encoded: String): String {
    val bytes = Base64.decode(encoded, Base64.NO_WRAP)
    val iv = bytes.copyOfRange(0, IV_LENGTH)
    val ct = bytes.copyOfRange(IV_LENGTH, bytes.size)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
    return String(cipher.doFinal(ct), Charsets.UTF_8)
}

/**
 * [SharedPreferences] that AES-256-GCM-encrypts every stored value using [secretKey]. Keys are kept
 * in plaintext; each value is stored as `Base64(IV || ciphertext)`.
 */
internal class EncryptingSharedPreferences(
    private val prefs: SharedPreferences,
    private val secretKey: SecretKey,
) : SharedPreferences {

    /** Returns the decrypted plaintext for [encoded], or null if decryption fails. */
    private fun decryptOrNull(encoded: String) =
        runCatching { decryptRaw(secretKey, encoded) }.getOrNull()

    /** Strips [prefix] and applies [parse] if this string starts with [prefix]; else null. */
    private inline fun <T> String.readTypedFromDecrypted(
        prefix: String,
        parse: (String) -> T?,
    ): T? = if (startsWith(prefix)) parse(removePrefix(prefix)) else null

    /** Fetches, decrypts, and parses the value for [key] using the given [prefix] and [parse]. */
    private inline fun <T> readTyped(key: String, prefix: String, parse: (String) -> T?): T? =
        prefs.getString(key, null)?.let(::decryptOrNull)?.readTypedFromDecrypted(prefix, parse)

    /** Returns all key-value pairs with each stored value decrypted. */
    override fun getAll(): MutableMap<String, *> =
        prefs.all
            .mapValues { (_, v) -> (v as? String)?.let { decryptOrNull(it)?.typed() } }
            .toMutableMap()

    /** Returns the decrypted string for [key], or [defValue] if absent or undecryptable. */
    override fun getString(key: String, defValue: String?): String? =
        readTyped(key, P_STRING) { it } ?: defValue

    /** StringSet is not supported; always throws [UnsupportedOperationException]. */
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        throw UnsupportedOperationException("String sets not supported by SecureSharedPreferences")

    /** Returns the decrypted int for [key], or [defValue] if absent or undecryptable. */
    override fun getInt(key: String, defValue: Int): Int =
        readTyped(key, P_INT, String::toIntOrNull) ?: defValue

    /** Returns the decrypted long for [key], or [defValue] if absent or undecryptable. */
    override fun getLong(key: String, defValue: Long): Long =
        readTyped(key, P_LONG, String::toLongOrNull) ?: defValue

    /** Returns the decrypted float for [key], or [defValue] if absent or undecryptable. */
    override fun getFloat(key: String, defValue: Float): Float =
        readTyped(key, P_FLOAT, String::toFloatOrNull) ?: defValue

    /** Returns the decrypted boolean for [key], or [defValue] if absent or undecryptable. */
    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        readTyped(key, P_BOOL, String::toBooleanStrictOrNull) ?: defValue

    /** Returns true if the underlying prefs contain an entry for [key]. */
    override fun contains(key: String): Boolean = prefs.contains(key)

    /** Returns an [EncryptingEditor] that AES-256-GCM-encrypts values before writing. */
    override fun edit(): SharedPreferences.Editor = EncryptingEditor(prefs.edit(), secretKey)

    /** Delegates listener registration to the underlying [prefs] instance. */
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) = prefs.registerOnSharedPreferenceChangeListener(listener)

    /** Delegates listener unregistration to the underlying [prefs] instance. */
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) = prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private fun String.typed(): Any? =
        readTypedFromDecrypted(P_STRING) { it }
            ?: readTypedFromDecrypted(P_BOOL, String::toBooleanStrictOrNull)
            ?: readTypedFromDecrypted(P_INT, String::toIntOrNull)
            ?: readTypedFromDecrypted(P_LONG, String::toLongOrNull)
            ?: readTypedFromDecrypted(P_FLOAT, String::toFloatOrNull)
}

/** [SharedPreferences.Editor] that AES-256-GCM-encrypts every value before writing to [editor]. */
private class EncryptingEditor(
    private val editor: SharedPreferences.Editor,
    private val secretKey: SecretKey,
) : SharedPreferences.Editor {

    private fun enc(plain: String) = encryptRaw(secretKey, plain)

    /** Encrypts [value] and writes it under [key]; removes [key] when [value] is null. */
    override fun putString(key: String, value: String?): SharedPreferences.Editor {
        if (value != null) editor.putString(key, enc(P_STRING + value)) else editor.remove(key)
        return this
    }

    /** Not supported; always throws [UnsupportedOperationException]. */
    override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
        throw UnsupportedOperationException("String sets not supported by SecureSharedPreferences")

    /** Encrypts [value] as a type-prefixed string and writes it under [key]. */
    override fun putInt(key: String, value: Int): SharedPreferences.Editor {
        editor.putString(key, enc(P_INT + value))
        return this
    }

    /** Encrypts [value] as a type-prefixed string and writes it under [key]. */
    override fun putLong(key: String, value: Long): SharedPreferences.Editor {
        editor.putString(key, enc(P_LONG + value))
        return this
    }

    /** Encrypts [value] as a type-prefixed string and writes it under [key]. */
    override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
        editor.putString(key, enc(P_FLOAT + value))
        return this
    }

    /** Encrypts [value] as a type-prefixed string and writes it under [key]. */
    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
        editor.putString(key, enc(P_BOOL + value))
        return this
    }

    /** Delegates removal of [key] to the underlying editor. */
    override fun remove(key: String): SharedPreferences.Editor {
        editor.remove(key)
        return this
    }

    /** Delegates clear to the underlying editor. */
    override fun clear(): SharedPreferences.Editor {
        editor.clear()
        return this
    }

    /** Commits all pending writes synchronously and returns success. */
    override fun commit(): Boolean = editor.commit()

    /** Commits all pending writes asynchronously. */
    override fun apply() = editor.apply()
}
