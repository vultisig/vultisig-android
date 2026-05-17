@file:Suppress("DEPRECATION")

package com.vultisig.wallet.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore
import timber.log.Timber

private const val ENCRYPTED_PREFS_FILE = "token_encrypted_prefs"
// Stored in a separate file so a clear() on the main prefs cannot reset this sentinel.
private const val MIGRATION_STATE_PREFS = "migration_state_prefs"
private const val MIGRATION_DONE_KEY = "__migrated_from_encrypted_prefs"

/**
 * One-time migration from the legacy androidx.security [EncryptedSharedPreferences] file into the
 * AndroidKeyStore-backed [EncryptingSharedPreferences] used by the current build. Preserves the
 * biometric vault password, referral / external attribution state, and the on-chain security
 * scanner toggle for users upgrading from pre-`v1.0.102` builds that have not yet seen the
 * migration.
 *
 * Idempotent: [MIGRATION_DONE_KEY] is written to a dedicated [MIGRATION_STATE_PREFS] file separate
 * from [newPrefs], so a [SharedPreferences.Editor.clear] on [newPrefs] cannot reset the sentinel
 * and trigger re-migration.
 *
 * Best-effort reaps the legacy [MasterKey] AndroidKeyStore alias and the [MIGRATION_STATE_PREFS]
 * sentinel file once the migration is confirmed done, so they are not left as permanent orphans on
 * devices that have already migrated.
 */
internal fun migrateFromEncryptedSharedPrefs(context: Context, newPrefs: SharedPreferences) {
    val migrationStatePrefs =
        context.getSharedPreferences(MIGRATION_STATE_PREFS, Context.MODE_PRIVATE)
    val legacyFile = File(context.filesDir.parent, "shared_prefs/$ENCRYPTED_PREFS_FILE.xml")
    val sentinelFile = File(context.filesDir.parent, "shared_prefs/$MIGRATION_STATE_PREFS.xml")

    if (migrationStatePrefs.getBoolean(MIGRATION_DONE_KEY, false)) {
        // Already migrated on a previous launch; reap any leftover legacy artifacts.
        if (legacyFile.exists() && !legacyFile.delete()) {
            Timber.w("Failed to delete legacy encrypted prefs file at %s", legacyFile.absolutePath)
        }
        reapLegacyArtifacts(legacyFile, sentinelFile)
        return
    }

    if (!legacyFile.exists()) {
        // Fresh install or user-cleared state. Leave the prefs untouched so we do not create an
        // orphan sentinel file on devices that never carried the legacy data.
        return
    }

    val legacyPrefs =
        try {
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_FILE,
                MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .setRequestStrongBoxBacked(false)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // Broad catch is intentional: Tink on some OEM ROMs throws IllegalStateException,
            // SecurityException, or NPE in addition to GeneralSecurityException / IOException.
            // Losing legacy data is acceptable here; a startup crash is not.
            Timber.e(e, "Cannot open legacy encrypted prefs for migration; discarding legacy data")
            legacyFile.delete()
            reapLegacyArtifacts(legacyFile, sentinelFile)
            return
        }

    val legacyEntries =
        try {
            legacyPrefs.all
        } catch (e: Exception) {
            Timber.e(e, "Cannot read legacy encrypted prefs; discarding legacy data")
            legacyFile.delete()
            reapLegacyArtifacts(legacyFile, sentinelFile)
            return
        }
    val editor = newPrefs.edit()
    for ((key, value) in legacyEntries) {
        when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            else ->
                Timber.w(
                    "Skipping legacy pref of unsupported type %s during migration",
                    value?.javaClass?.simpleName,
                )
        }
    }
    if (editor.commit()) {
        // Use commit() (not apply()) so the sentinel is guaranteed on disk before legacyFile is
        // deleted. An apply() here would allow a process kill to leave the marker unflushed while
        // the legacy file is gone, causing re-migration to overwrite values on the next launch.
        migrationStatePrefs.edit().putBoolean(MIGRATION_DONE_KEY, true).commit()
        if (!legacyFile.delete()) {
            Timber.w("Failed to delete legacy encrypted prefs file at %s", legacyFile.absolutePath)
        }
        Timber.i("Migrated encrypted prefs to secure format")
        reapLegacyArtifacts(legacyFile, sentinelFile)
    } else {
        Timber.w("Failed to commit migrated prefs; legacy file retained for next attempt")
    }
}

/**
 * Best-effort delete of the [MasterKey] AndroidKeyStore alias and the [MIGRATION_STATE_PREFS]
 * sentinel file. The sentinel is only deleted once [legacyFile] is confirmed gone, so a transient
 * legacy-file delete failure cannot leave the sentinel cleared while ciphertext remains on disk
 * (which would re-trigger migration and overwrite user-modified values on the next launch).
 */
private fun reapLegacyArtifacts(legacyFile: File, sentinelFile: File) {
    runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        }
        .onFailure { Timber.w(it, "Failed to delete legacy AndroidKeyStore master-key alias") }

    if (legacyFile.exists()) return
    if (sentinelFile.exists() && !sentinelFile.delete()) {
        Timber.w("Failed to delete legacy migration sentinel at %s", sentinelFile.absolutePath)
    }
}
