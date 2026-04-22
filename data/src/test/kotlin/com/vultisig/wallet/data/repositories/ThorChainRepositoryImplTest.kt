@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.sources.AppDataStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class ThorChainRepositoryImplTest {

    private val api: ThorChainApi = mockk(relaxed = true)

    @Test
    fun `getCachedNetworkChainId returns cached value when DataStore responds fast`() = runTest {
        val repo = ThorChainRepositoryImpl(api, FakeDataStore(cached = "thorchain-mainnet"))

        assertEquals("thorchain-mainnet", repo.getCachedNetworkChainId())
    }

    @Test
    fun `getCachedNetworkChainId returns null when cache is empty`() = runTest {
        val repo = ThorChainRepositoryImpl(api, FakeDataStore(cached = null))

        assertNull(repo.getCachedNetworkChainId())
    }

    @Test
    fun `getCachedNetworkChainId returns null when read exceeds timeout`() = runTest {
        val repo = ThorChainRepositoryImpl(api, FakeDataStore(hangForever = true))

        val result = async(start = CoroutineStart.UNDISPATCHED) { repo.getCachedNetworkChainId() }
        advanceTimeBy(4.seconds)
        advanceUntilIdle()

        assertNull(result.await())
    }

    @Test
    fun `fetchNetworkChainId writes result to DataStore`() = runTest {
        val store = FakeDataStore()
        coEvery { api.getNetworkChainId() } returns "thorchain-stagenet"
        val repo = ThorChainRepositoryImpl(api, store)

        assertEquals("thorchain-stagenet", repo.fetchNetworkChainId())
        assertEquals("thorchain-stagenet", store.lastWritten)
    }

    private class FakeDataStore(
        private val cached: String? = null,
        private val hangForever: Boolean = false,
    ) : AppDataStore {

        var lastWritten: String? = null
            private set

        override suspend fun editData(
            transform: suspend (MutablePreferences) -> Unit
        ): Preferences = preferencesOf()

        override fun <T> readData(key: Preferences.Key<T>, defaultValue: T): Flow<T> =
            flowOf(defaultValue)

        @Suppress("UNCHECKED_CAST")
        override fun <T> readData(key: Preferences.Key<T>): Flow<T?> =
            if (hangForever) flow { CompletableDeferred<Unit>().await() } else flowOf(cached as T?)

        override suspend fun <T> set(key: Preferences.Key<T>, value: T) {
            lastWritten = value as? String
        }
    }
}
