package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

interface ThorChainRepository {

    suspend fun getCachedNetworkChainId(): String?

    suspend fun fetchNetworkChainId(): String

}

internal class ThorChainRepositoryImpl @Inject constructor(
    private val thorChainApi: ThorChainApi,
    private val dataStore: AppDataStore,
) : ThorChainRepository {

    override suspend fun getCachedNetworkChainId(): String? =
        dataStore.readData(prefKeyThorChainNetworkId)
            .first()

    override suspend fun fetchNetworkChainId(): String =
        thorChainApi.getNetworkChainId()
            .also {
                dataStore.set(prefKeyThorChainNetworkId, it)
            }

    companion object {
        private val prefKeyThorChainNetworkId =
            stringPreferencesKey("pref_key_thor_chain_network_id")
    }

}