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
internal const val SECURE_PREFS_KEY_ALIAS = "vultisig_secure_prefs_key"
private const val IV_LENGTH = 12
private const val GCM_TAG_BITS = 128

private const val P_STRING = "s:"
private const val P_BOOL = "b:"
private const val P_INT = "i:"
private const val P_LONG = "l:"
private const val P_FLOAT = "f:"

/**
 * Builds or retrieves the AES-256-GCM AndroidKeyStore key used to encrypt preference values.
 * StrongBox is not requested to avoid keystore-daemon stalls on certain Pixel/Samsung devices.
 */
internal fun buildSecurePrefsKey(): SecretKey {
    val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    (ks.getEntry(SECURE_PREFS_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
        return it.secretKey
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
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        .apply { init(spec) }
        .generateKey()
}

/** Encrypts [plaintext] with AES-256-GCM and returns `Base64(IV || ciphertext)`. */
private fun encryptRaw(secretKey: SecretKey, plaintext: String): String {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val iv = cipher.iv
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

    /** Returns all key-value pairs with each stored value decrypted. */
    override fun getAll(): MutableMap<String, *> =
        prefs.all
            .mapValues { (_, v) -> (v as? String)?.let { decryptOrNull(it)?.typed() } }
            .toMutableMap()

    /** Returns the decrypted string for [key], or [defValue] if absent or undecryptable. */
    override fun getString(key: String, defValue: String?): String? =
        prefs.getString(key, null)?.let {
            decryptOrNull(it)?.takeIf { d -> d.startsWith(P_STRING) }?.removePrefix(P_STRING)
        } ?: defValue

    /** StringSet is not supported; always returns [defValues]. */
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        defValues

    /** Returns the decrypted int for [key], or [defValue] if absent or undecryptable. */
    override fun getInt(key: String, defValue: Int): Int =
        prefs.getString(key, null)?.let {
            decryptOrNull(it)
                ?.takeIf { d -> d.startsWith(P_INT) }
                ?.removePrefix(P_INT)
                ?.toIntOrNull()
        } ?: defValue

    /** Returns the decrypted long for [key], or [defValue] if absent or undecryptable. */
    override fun getLong(key: String, defValue: Long): Long =
        prefs.getString(key, null)?.let {
            decryptOrNull(it)
                ?.takeIf { d -> d.startsWith(P_LONG) }
                ?.removePrefix(P_LONG)
                ?.toLongOrNull()
        } ?: defValue

    /** Returns the decrypted float for [key], or [defValue] if absent or undecryptable. */
    override fun getFloat(key: String, defValue: Float): Float =
        prefs.getString(key, null)?.let {
            decryptOrNull(it)
                ?.takeIf { d -> d.startsWith(P_FLOAT) }
                ?.removePrefix(P_FLOAT)
                ?.toFloatOrNull()
        } ?: defValue

    /** Returns the decrypted boolean for [key], or [defValue] if absent or undecryptable. */
    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        prefs.getString(key, null)?.let {
            decryptOrNull(it)
                ?.takeIf { d -> d.startsWith(P_BOOL) }
                ?.removePrefix(P_BOOL)
                ?.toBooleanStrictOrNull()
        } ?: defValue

    /** Returns true if the underlying prefs contain an entry for [key]. */
    override fun contains(key: String): Boolean = prefs.contains(key)

    /** Returns an [EncryptingEditor] that AES-256-GCM-encrypts values before writing. */
    override fun edit(): SharedPreferences.Editor = EncryptingEditor(prefs.edit(), secretKey)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) = prefs.registerOnSharedPreferenceChangeListener(listener)

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) = prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private fun String.typed(): Any? =
        when {
            startsWith(P_STRING) -> removePrefix(P_STRING)
            startsWith(P_BOOL) -> removePrefix(P_BOOL).toBooleanStrictOrNull()
            startsWith(P_INT) -> removePrefix(P_INT).toIntOrNull()
            startsWith(P_LONG) -> removePrefix(P_LONG).toLongOrNull()
            startsWith(P_FLOAT) -> removePrefix(P_FLOAT).toFloatOrNull()
            else -> null
        }
}

private class EncryptingEditor(
    private val editor: SharedPreferences.Editor,
    private val secretKey: SecretKey,
) : SharedPreferences.Editor {

    private fun enc(plain: String) = encryptRaw(secretKey, plain)

    override fun putString(key: String, value: String?): SharedPreferences.Editor {
        if (value != null) editor.putString(key, enc(P_STRING + value)) else editor.remove(key)
        return this
    }

    override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
        editor.remove(key)
        return this
    }

    override fun putInt(key: String, value: Int): SharedPreferences.Editor {
        editor.putString(key, enc(P_INT + value))
        return this
    }

    override fun putLong(key: String, value: Long): SharedPreferences.Editor {
        editor.putString(key, enc(P_LONG + value))
        return this
    }

    override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
        editor.putString(key, enc(P_FLOAT + value))
        return this
    }

    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
        editor.putString(key, enc(P_BOOL + value))
        return this
    }

    override fun remove(key: String): SharedPreferences.Editor {
        editor.remove(key)
        return this
    }

    override fun clear(): SharedPreferences.Editor {
        editor.clear()
        return this
    }

    override fun commit(): Boolean = editor.commit()

    override fun apply() = editor.apply()
}
