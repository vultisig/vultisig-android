package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CustomRpcRepositoryImplTest {

    /** Minimal in-memory [AppDataStore] backing a single string preference key. */
    private class FakeAppDataStore : AppDataStore {
        private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())

        @Suppress("UNCHECKED_CAST")
        override fun <T> readData(key: Preferences.Key<T>, defaultValue: T): Flow<T> =
            state.map { (it[key] ?: defaultValue) as T }

        override fun <T> readData(key: Preferences.Key<T>): Flow<T?> = state.map { it[key] }

        override suspend fun <T> set(key: Preferences.Key<T>, value: T) {
            val next = mutablePreferencesOf().apply { putAll(state.value.asMap()) }
            next[key] = value
            state.value = next
        }

        override suspend fun editData(
            transform: suspend (MutablePreferences) -> Unit
        ): Preferences {
            val next = mutablePreferencesOf().apply { putAll(state.value.asMap()) }
            transform(next)
            state.value = next
            return next
        }

        @Suppress("UNCHECKED_CAST")
        private fun MutablePreferences.putAll(map: Map<Preferences.Key<*>, Any?>) {
            map.forEach { (k, v) -> this[k as Preferences.Key<Any>] = v as Any }
        }
    }

    private fun repo(store: AppDataStore = FakeAppDataStore()) =
        CustomRpcRepositoryImpl(store, Json)

    @Test
    fun `urlFor returns null when no override is set`() {
        assertNull(repo().urlFor(Chain.Ethereum))
    }

    @Test
    fun `setOverride is visible to a synchronous urlFor read`() = runTest {
        val repo = repo()
        repo.setOverride(Chain.Ethereum, "https://my-node.example/")
        assertEquals("https://my-node.example/", repo.urlFor(Chain.Ethereum))
    }

    @Test
    fun `setOverride trims surrounding whitespace`() = runTest {
        val repo = repo()
        repo.setOverride(Chain.GaiaChain, "  https://cosmos-node.example  ")
        assertEquals("https://cosmos-node.example", repo.urlFor(Chain.GaiaChain))
    }

    @Test
    fun `overrides for one chain do not leak to another`() = runTest {
        val repo = repo()
        repo.setOverride(Chain.Ethereum, "https://eth-node.example/")
        assertEquals("https://eth-node.example/", repo.urlFor(Chain.Ethereum))
        assertNull(repo.urlFor(Chain.Avalanche))
    }

    @Test
    fun `clearOverride restores the default lookup`() = runTest {
        val repo = repo()
        repo.setOverride(Chain.Base, "https://base-node.example/")
        repo.clearOverride(Chain.Base)
        assertNull(repo.urlFor(Chain.Base))
    }

    @Test
    fun `overrides flow reflects the persisted map`() = runTest {
        val repo = repo()
        repo.setOverride(Chain.Ethereum, "https://eth-node.example/")
        repo.setOverride(Chain.Osmosis, "https://osmo-node.example/")

        val overrides = repo.overrides.first()
        assertEquals("https://eth-node.example/", overrides[Chain.Ethereum])
        assertEquals("https://osmo-node.example/", overrides[Chain.Osmosis])
        assertEquals(2, overrides.size)
    }

    @Test
    fun `a fresh repository hydrates overrides persisted by a prior instance`() = runTest {
        val store = FakeAppDataStore()
        repo(store).setOverride(Chain.Ethereum, "https://eth-node.example/")

        // A new repository over the same store decodes the persisted JSON.
        val overrides = repo(store).overrides.first()
        assertEquals("https://eth-node.example/", overrides[Chain.Ethereum])
    }

    @Test
    fun `malformed persisted json decodes to an empty map`() = runTest {
        val store = FakeAppDataStore()
        store.set(
            androidx.datastore.preferences.core.stringPreferencesKey("custom_rpc_overrides"),
            "{ this is not json",
        )
        assertTrue(repo(store).overrides.first().isEmpty())
    }
}
