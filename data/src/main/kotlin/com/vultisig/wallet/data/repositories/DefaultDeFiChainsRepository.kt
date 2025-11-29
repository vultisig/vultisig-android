package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

interface DefaultDeFiChainsRepository {
    suspend fun setDefaultChains(vaultId: String, chains: Set<Chain>)
    fun getDefaultChains(vaultId: String): Flow<Set<Chain>>
}

internal class DefaultDeFiChainsRepositoryImpl @Inject constructor(
    private val dataStore: AppDataStore,
) : DefaultDeFiChainsRepository {

    override suspend fun setDefaultChains(vaultId: String, chains: Set<Chain>) {
        dataStore.editData { preferences ->
            val chainsKey = stringSetPreferencesKey(getChainsKey(vaultId))
            val hasBeenSetKey = booleanPreferencesKey(getHasBeenSetKey(vaultId))
            
            preferences[chainsKey] = chains.map { it.raw }.toSet()
            preferences[hasBeenSetKey] = true
        }
    }

    override fun getDefaultChains(vaultId: String): Flow<Set<Chain>> {
        val chainsKey = stringSetPreferencesKey(getChainsKey(vaultId))
        val hasBeenSetKey = booleanPreferencesKey(getHasBeenSetKey(vaultId))
        
        return combine(
            dataStore.readData(chainsKey, emptySet()),
            dataStore.readData(hasBeenSetKey, false)
        ) { chainRaws, hasBeenSet ->
            if (!hasBeenSet) {
                getDefaultDeFiChains()
            } else {
                chainRaws.mapNotNull { raw ->
                    Chain.entries.find { it.raw == raw }
                }.toSet()
            }
        }
    }

    private fun getChainsKey(vaultId: String) = "defi_chains_$vaultId"
    private fun getHasBeenSetKey(vaultId: String) = "defi_chains_set_$vaultId"

    private fun getDefaultDeFiChains(): Set<Chain> = setOf(
        Chain.ThorChain,
    )
}