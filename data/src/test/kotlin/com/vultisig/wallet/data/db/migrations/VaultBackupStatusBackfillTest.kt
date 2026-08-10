package com.vultisig.wallet.data.db.migrations

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.db.dao.VaultDao
import com.vultisig.wallet.data.sources.FakeAppDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class VaultBackupStatusBackfillTest {

    private lateinit var vaultDao: VaultDao
    private lateinit var appDataStore: FakeAppDataStore

    @BeforeEach
    fun setUp() {
        vaultDao = mockk(relaxUnitFun = true)
        appDataStore = FakeAppDataStore()
    }

    private fun backfill() = VaultBackupStatusBackfill(vaultDao, appDataStore)

    private fun legacyKey(vaultId: String) = booleanPreferencesKey(name = "vault_backup/$vaultId")

    private val doneKey = booleanPreferencesKey(name = "vault_backup_status_backfilled")

    /**
     * Verifies a vault whose owner had already exported keeps its flag.
     *
     * This is the reason the class exists: the SQL migration starts every row at not-backed-up, so
     * without this pass everyone already holding a `.vult` would be told to back up again.
     */
    @Test
    fun `carries a legacy exported flag onto the vault row`() = runTest {
        coEvery { vaultDao.loadAllIds() } returns listOf("vault-1")
        appDataStore.set(legacyKey("vault-1"), true)

        backfill().ensureRun()

        coVerify { vaultDao.setBackupStatus(vaultId = "vault-1", isBackedUp = true) }
    }

    /**
     * Verifies a vault with no stored flag is left not-backed-up.
     *
     * Preferences answered that read with `true`, which is what made a fast vault nobody had ever
     * exported — a save path that never wrote a flag — indistinguishable from an exported one.
     * Unknown has to mean not backed up, or the flag cannot gate anything.
     */
    @Test
    fun `leaves a vault with no legacy flag alone, rather than assuming it is safe`() = runTest {
        coEvery { vaultDao.loadAllIds() } returns listOf("never-exported")

        backfill().ensureRun()

        coVerify(exactly = 0) { vaultDao.setBackupStatus(any(), any()) }
    }

    /** Verifies an explicit legacy `false` stays false rather than being written as true. */
    @Test
    fun `leaves a vault whose legacy flag was cleared not backed up`() = runTest {
        coEvery { vaultDao.loadAllIds() } returns listOf("vault-1")
        appDataStore.set(legacyKey("vault-1"), false)

        backfill().ensureRun()

        coVerify(exactly = 0) { vaultDao.setBackupStatus(any(), any()) }
    }

    /**
     * Verifies the legacy keys are dropped and completion recorded, so nothing reads them again.
     */
    @Test
    fun `drops the legacy keys and records completion`() = runTest {
        coEvery { vaultDao.loadAllIds() } returns listOf("vault-1", "vault-2")
        appDataStore.set(legacyKey("vault-1"), true)

        backfill().ensureRun()

        assertNull(appDataStore.readData(legacyKey("vault-1")).first())
        assertTrue(appDataStore.readData(doneKey, false).first())
    }

    /**
     * Verifies a flag left behind by a deleted vault is dropped too.
     *
     * Deleting a vault never removed its flag, so preferences hold entries for vaults that no
     * longer exist. Sweeping by prefix clears them; keying the removal off the surviving vaults
     * would leave them there for good, since nothing reads these keys after this pass.
     */
    @Test
    fun `drops a flag orphaned by a deleted vault`() = runTest {
        coEvery { vaultDao.loadAllIds() } returns listOf("vault-1")
        appDataStore.set(legacyKey("deleted-vault"), true)

        backfill().ensureRun()

        assertNull(appDataStore.readData(legacyKey("deleted-vault")).first())
        coVerify(exactly = 0) { vaultDao.setBackupStatus(any(), any()) }
    }

    /** Verifies keys belonging to other features survive the sweep. */
    @Test
    fun `leaves unrelated preferences alone`() = runTest {
        coEvery { vaultDao.loadAllIds() } returns listOf("vault-1")
        val hint = stringPreferencesKey(name = "vault_fast_sign_hint/vault-1")
        appDataStore.set(hint, "hint")

        backfill().ensureRun()

        assertEquals("hint", appDataStore.readData(hint).first())
    }

    /**
     * Verifies a second instance — a fresh process, with nothing cached in memory — does no work
     * once completion is recorded.
     *
     * This is what keeps a stale flag from coming back. A vault reshared after the backfill has a
     * cleared flag on its row, and a second run that read the legacy keys again would put the old
     * `true` back over it.
     */
    @Test
    fun `a later run in a new process does nothing`() = runTest {
        appDataStore.set(doneKey, true)

        backfill().ensureRun()

        coVerify(exactly = 0) { vaultDao.loadAllIds() }
        coVerify(exactly = 0) { vaultDao.setBackupStatus(any(), any()) }
    }

    /** Verifies repeat calls on one instance do the work once. */
    @Test
    fun `repeated calls run the pass once`() = runTest {
        coEvery { vaultDao.loadAllIds() } returns listOf("vault-1")
        val backfill = backfill()

        backfill.ensureRun()
        backfill.ensureRun()
        backfill.ensureRun()

        coVerify(exactly = 1) { vaultDao.loadAllIds() }
    }

    /**
     * Verifies concurrent callers wait on the one run instead of each starting their own — every
     * vault read goes through here, and the first ones can arrive together.
     */
    @Test
    fun `concurrent callers share a single run`() = runTest {
        val readIds = CompletableDeferred<Unit>()
        coEvery { vaultDao.loadAllIds() } coAnswers
            {
                readIds.await()
                listOf("vault-1")
            }
        val backfill = backfill()

        launch { backfill.ensureRun() }
        launch { backfill.ensureRun() }
        advanceUntilIdle()
        readIds.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { vaultDao.loadAllIds() }
    }

    /**
     * Verifies a failed pass neither propagates nor gives up.
     *
     * Every vault read runs this, so a preferences or database hiccup must not turn into a failed
     * vault read; and since nothing else will ever carry the flags over, the next caller has to
     * retry.
     */
    @Test
    fun `a failed run does not propagate and is retried`() = runTest {
        coEvery { vaultDao.loadAllIds() } throws RuntimeException("db") andThen listOf("vault-1")
        appDataStore.set(legacyKey("vault-1"), true)
        val backfill = backfill()

        backfill.ensureRun()
        coVerify(exactly = 0) { vaultDao.setBackupStatus(any(), any()) }

        backfill.ensureRun()

        coVerify(exactly = 1) { vaultDao.setBackupStatus(vaultId = "vault-1", isBackedUp = true) }
    }
}
