package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoVaultRegistry
import com.vultisig.wallet.data.sources.AppDataStore
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Covers the snapshot the DeFi portfolio renders from before the network answers: a write reports
 * only the vaults the user has switched on, so it has to leave the others' amounts alone.
 */
internal class KaminoPositionCacheRepositoryTest {

    /** In-memory [AppDataStore] so reads observe prior writes without Android DataStore. */
    private val store = FakeAppDataStore()

    private val repo = KaminoPositionCacheRepository(store)

    @Test
    fun `a position survives the write that recorded it`() = runTest {
        repo.savePositions(VAULT_ID, mapOf(STEAKHOUSE to BigInteger("500000000")))

        repo.getPositions(VAULT_ID) shouldBe mapOf(STEAKHOUSE to BigInteger("500000000"))
    }

    @Test
    fun `a vault reported as holding nothing stops counting`() = runTest {
        repo.savePositions(VAULT_ID, mapOf(STEAKHOUSE to BigInteger("500000000")))

        repo.savePositions(VAULT_ID, mapOf(STEAKHOUSE to BigInteger.ZERO))

        repo.getPositions(VAULT_ID) shouldBe mapOf(STEAKHOUSE to BigInteger.ZERO)
    }

    @Test
    fun `a vault the write says nothing about keeps its amount`() = runTest {
        // Switching a vault off drops it from every later write, and switching it back on must not
        // find its position gone: a cold start would then under-report the DeFi total.
        repo.savePositions(
            VAULT_ID,
            mapOf(STEAKHOUSE to BigInteger("500000000"), RWA to BigInteger("250000000")),
        )

        repo.savePositions(VAULT_ID, mapOf(STEAKHOUSE to BigInteger("600000000")))

        repo.getPositions(VAULT_ID) shouldBe
            mapOf(STEAKHOUSE to BigInteger("600000000"), RWA to BigInteger("250000000"))
    }

    @Test
    fun `snapshots are independent per vault`() = runTest {
        repo.savePositions(VAULT_ID, mapOf(STEAKHOUSE to BigInteger("500000000")))
        repo.savePositions(OTHER_VAULT_ID, mapOf(STEAKHOUSE to BigInteger("120000000")))

        repo.getPositions(VAULT_ID) shouldBe mapOf(STEAKHOUSE to BigInteger("500000000"))
        repo.getPositions(OTHER_VAULT_ID) shouldBe mapOf(STEAKHOUSE to BigInteger("120000000"))
    }

    @Test
    fun `a vault the allow-list no longer carries is dropped`() = runTest {
        repo.savePositions(
            VAULT_ID,
            mapOf(STEAKHOUSE to BigInteger("500000000"), "retired-vault" to BigInteger("999")),
        )

        repo.getPositions(VAULT_ID) shouldBe mapOf(STEAKHOUSE to BigInteger("500000000"))
    }

    private class FakeAppDataStore : AppDataStore {
        private val prefs = MutableStateFlow<Preferences>(emptyPreferences())

        override suspend fun editData(
            transform: suspend (MutablePreferences) -> Unit
        ): Preferences {
            val mutable = prefs.value.toMutablePreferences()
            transform(mutable)
            val updated = mutable.toPreferences()
            prefs.value = updated
            return updated
        }

        override fun <T> readData(key: Preferences.Key<T>, defaultValue: T): Flow<T> =
            prefs.map { it[key] ?: defaultValue }

        override suspend fun <T> set(key: Preferences.Key<T>, value: T) {
            editData { it[key] = value }
        }

        override fun <T> readData(key: Preferences.Key<T>): Flow<T?> = prefs.map { it[key] }
    }

    private companion object {
        const val VAULT_ID = "vault-id"
        const val OTHER_VAULT_ID = "other-vault-id"

        val STEAKHOUSE = KaminoVaultRegistry.STEAKHOUSE_USDC.address
        val RWA = KaminoVaultRegistry.RWA_USDC.address
    }
}
