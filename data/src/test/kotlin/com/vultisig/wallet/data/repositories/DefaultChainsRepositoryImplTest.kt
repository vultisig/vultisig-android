package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.sources.AppDataStore
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

internal class DefaultChainsRepositoryImplTest {

    @Test
    fun `a since-retired chain id is dropped without discarding the rest of the selection`() =
        runTest {
            val repository = repositoryWith("""["Bitcoin","$RETIRED_CHAIN_ID","Ethereum"]""")

            assertEquals(
                listOf(Chain.Bitcoin, Chain.Ethereum),
                repository.selectedDefaultChains.first(),
            )
        }

    @Test
    fun `a selection of nothing but retired chain ids falls back to the defaults`() = runTest {
        val repository = repositoryWith("""["$RETIRED_CHAIN_ID"]""")

        assertEquals(
            listOf(Chain.ThorChain, Chain.Bitcoin, Chain.BscChain, Chain.Ethereum, Chain.Solana),
            repository.selectedDefaultChains.first(),
        )
    }

    @Test
    fun `an unreadable selection falls back to the defaults`() = runTest {
        val repository = repositoryWith("not json")

        assertEquals(
            listOf(Chain.ThorChain, Chain.Bitcoin, Chain.BscChain, Chain.Ethereum, Chain.Solana),
            repository.selectedDefaultChains.first(),
        )
    }

    private fun repositoryWith(stored: String) =
        DefaultChainsRepositoryImpl(FakeAppDataStore(stored), Json)

    private companion object {
        // A chain id still sitting in old preferences after the chain was removed from [Chain].
        const val RETIRED_CHAIN_ID = "Kujira"
    }
}

private class FakeAppDataStore(private val stored: String) : AppDataStore {

    override suspend fun editData(transform: suspend (MutablePreferences) -> Unit): Preferences =
        mutablePreferencesOf()

    @Suppress("UNCHECKED_CAST")
    override fun <T> readData(key: Preferences.Key<T>, defaultValue: T): Flow<T> =
        flowOf(stored as T)

    override suspend fun <T> set(key: Preferences.Key<T>, value: T) = Unit

    @Suppress("UNCHECKED_CAST")
    override fun <T> readData(key: Preferences.Key<T>): Flow<T?> = flowOf(stored as T)
}
