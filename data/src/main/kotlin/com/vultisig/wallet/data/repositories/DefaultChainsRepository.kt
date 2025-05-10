package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

interface DefaultChainsRepository {
    val selectedDefaultChains: Flow<List<Chain>>
    suspend fun setSelectedDefaultChains(chains: List<Chain>)
    fun getAllDefaultChains(): List<Chain>
}

internal class DefaultChainsRepositoryImpl @Inject constructor(
    private val dataStore: AppDataStore,
    private val json: Json
) : DefaultChainsRepository {

    override val selectedDefaultChains: Flow<List<Chain>>
        get() = dataStore.readData(stringPreferencesKey(SELECTED_DEFAULT_CHAINS_KEY), "")
            .map { it ->
                try {
                    json.decodeFromString<List<String>>(it)
                        .map { Chain.fromRaw(it) }
                } catch (e: Throwable) {
                    Timber.e(e)
                    DEFAULT_CHAINS_LIST
                }.ifEmpty { DEFAULT_CHAINS_LIST }
            }

    override suspend fun setSelectedDefaultChains(chains: List<Chain>) {
        dataStore.editData { preferences ->
            preferences.set(
                key = stringPreferencesKey(SELECTED_DEFAULT_CHAINS_KEY),
                value = json.encodeToString(chains.map { it.id })
            )
        }
    }

    override fun getAllDefaultChains(): List<Chain> {
        return DEFAULT_CHAINS_LIST
    }

    companion object {
        private const val SELECTED_DEFAULT_CHAINS_KEY = "selected_default_chains_key"

        private val DEFAULT_CHAINS_LIST: List<Chain>
            get() = listOf(
                Chain.ThorChain,
                Chain.Bitcoin,
                Chain.BscChain,
                Chain.Ethereum,
                Chain.Solana
            )

    }

}