package com.vultisig.wallet.data.db.migrations

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vultisig.wallet.data.db.dao.VaultDao
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Carries the per-vault backup flag from DataStore onto the vault row, once per install.
 *
 * The flag used to live at `vault_backup/<vaultId>` in preferences and was read with a default of
 * `true`, so a vault nobody had written a flag for counted as backed up. Two save paths never wrote
 * one — fast-vault creation and the MLDSA merge — which made "never exported" indistinguishable
 * from "exported" for precisely the vaults that had never been exported.
 *
 * [MIGRATION_41_42] replaces it with a column defaulting to `false`. Restoring the flags of users
 * who *have* exported is why this class exists: SQL cannot read DataStore, so without a code-side
 * pass everyone who already holds a `.vult` would be told to back up again. A vault with no stored
 * flag stays `false` — unknown is not the same as safe.
 *
 * Called from [com.vultisig.wallet.data.repositories.VaultRepository] ahead of every read of a
 * vault and every write that could carry the flag, so no caller can observe the pre-backfill state
 * and no reshare can be undone by a backfill that lands after it. Safe to call from anywhere: the
 * work happens under a [Mutex] at most once, and concurrent callers await that same run rather than
 * repeating it. After it completes, calls cost a volatile read.
 *
 * A failure leaves the flags where they are and lets the next caller retry, rather than propagating
 * — a preferences read that goes wrong must not take every vault read down with it. The order of
 * writes is what makes the retry safe: rows are written first, and the legacy keys are dropped in
 * the same edit that records completion, so a run that dies midway is always re-derivable from the
 * keys still in place.
 */
@Singleton
internal class VaultBackupStatusBackfill
@Inject
constructor(private val vaultDao: VaultDao, private val appDataStore: AppDataStore) {

    private val mutex = Mutex()

    @Volatile private var isCompleted = false

    suspend fun ensureRun() {
        if (isCompleted) return
        mutex.withLock {
            if (isCompleted) return
            if (appDataStore.readData(BACKFILL_DONE_KEY, false).first()) {
                isCompleted = true
                return
            }
            try {
                backfill()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to move vault backup flags out of preferences")
                return
            }
            isCompleted = true
        }
    }

    private suspend fun backfill() {
        val vaultIds = vaultDao.loadAllIds()
        val backedUp =
            vaultIds.filter { appDataStore.readData(legacyBackupStatusKey(it), false).first() }
        backedUp.forEach { vaultDao.setBackupStatus(vaultId = it, isBackedUp = true) }
        appDataStore.editData { preferences ->
            // Every key with the prefix, not just the ones matching a vault that still exists:
            // deleting a vault never removed its flag, so preferences hold entries for vaults that
            // are long gone.
            preferences
                .asMap()
                .keys
                .filter { it.name.startsWith(LEGACY_KEY_PREFIX) }
                .forEach { preferences -= it }
            preferences[BACKFILL_DONE_KEY] = true
        }
        Timber.i(
            "Moved the backup flag onto the vault row for %d of %d vaults",
            backedUp.size,
            vaultIds.size,
        )
    }

    private companion object {
        const val LEGACY_KEY_PREFIX = "vault_backup/"

        val BACKFILL_DONE_KEY = booleanPreferencesKey(name = "vault_backup_status_backfilled")

        fun legacyBackupStatusKey(vaultId: VaultId) =
            booleanPreferencesKey(name = "$LEGACY_KEY_PREFIX$vaultId")
    }
}
