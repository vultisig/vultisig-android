package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vultisig.wallet.data.sources.AppDataStore
import com.vultisig.wallet.models.Chain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        get() =
            dataStore.readData(stringPreferencesKey(SELECTED_DEFAULT_CHAINS_KEY), "").map {
                if (it.isEmpty()) emptyList() else
                    gson.fromJson(it, object : TypeToken<List<Chain>>() {}.type)
            }

    override suspend fun setSelectedDefaultChains(chains: List<Chain>) {
        dataStore.editData { preferences ->
            preferences.set(
                key = stringPreferencesKey(SELECTED_DEFAULT_CHAINS_KEY),
                value = gson.toJson(chains)
            )
        }
    }

    override fun getAllDefaultChains(): List<Chain> {
        return DEFAULT_CHAINS_LIST
    }

    companion object {
        const val SELECTED_DEFAULT_CHAINS_KEY = "selected_default_chains_key"
    }

}