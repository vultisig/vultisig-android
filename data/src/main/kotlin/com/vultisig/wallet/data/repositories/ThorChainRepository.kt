package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import timber.log.Timber

interface ThorChainRepository {

    /**
     * Returns the cached THORChain network id, or `null` if the cache is empty **or** the read
     * exceeds [CACHE_READ_TIMEOUT]. A slow DataStore read must not block app startup, so callers
     * should treat `null` as a cache miss and fall back to [fetchNetworkChainId].
     */
    suspend fun getCachedNetworkChainId(): String?

    suspend fun fetchNetworkChainId(): String
}

internal class ThorChainRepositoryImpl
@Inject
constructor(private val thorChainApi: ThorChainApi, private val dataStore: AppDataStore) :
    ThorChainRepository {

    override suspend fun getCachedNetworkChainId(): String? =
        try {
            withTimeout(CACHE_READ_TIMEOUT) {
                dataStore.readData(prefKeyThorChainNetworkId).first()
            }
        } catch (_: TimeoutCancellationException) {
            Timber.w(
                "Cached THORChain network id read timed out after %ds; falling back to live fetch",
                CACHE_READ_TIMEOUT.inWholeSeconds,
            )
            null
        } catch (e: CancellationException) {
            throw e
        }

    override suspend fun fetchNetworkChainId(): String =
        thorChainApi.getNetworkChainId().also { dataStore.set(prefKeyThorChainNetworkId, it) }

    companion object {
        private val prefKeyThorChainNetworkId =
            stringPreferencesKey("pref_key_thor_chain_network_id")
    }
}

private val CACHE_READ_TIMEOUT = 3.seconds
