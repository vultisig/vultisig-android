package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

internal interface DefaultChainsRepository {
    val selectedDefaultChains: Flow<List<Chain>>
    suspend fun setSelectedDefaultChains(chains: List<Chain>)
    fun getAllDefaultChains(): List<Chain>
}

internal class DefaultChainsRepositoryImpl @Inject constructor(
    private val dataStore: AppDataStore,
    private val gson: Gson
) : DefaultChainsRepository {

    override val selectedDefaultChains: Flow<List<Chain>>
        get() = dataStore.readData(stringPreferencesKey(SELECTED_DEFAULT_CHAINS_KEY), "")
            .map { it ->
                try {
                    gson.fromJson<List<String>>(it, object : TypeToken<List<String>>() {}.type)
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
                value = gson.toJson(chains.map { it.id })
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