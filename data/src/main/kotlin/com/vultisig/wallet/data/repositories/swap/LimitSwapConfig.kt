package com.vultisig.wallet.data.repositories.swap

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * DataStore-backed feature flag for the THORChain Limit Swap ("Place Limit Order") flow. Default is
 * `false` — the feature is opt-in via Settings → Advanced → Limit Orders. Combined (AND) with the
 * remote `limit-swap` kill switch in `SwapFormViewModel`.
 */
interface LimitSwapConfig {
    /** Live flow of the user's Advanced Settings → Limit Swap toggle. Defaults to `false`. */
    val isFeatureEnabled: Flow<Boolean>

    /** Persists the user's new toggle value. */
    suspend fun setFeatureEnabled(enabled: Boolean)
}

/** [LimitSwapConfig] backed by the shared [AppDataStore] preferences store. */
internal class LimitSwapConfigImpl @Inject constructor(private val dataStore: AppDataStore) :
    LimitSwapConfig {

    override val isFeatureEnabled: Flow<Boolean>
        get() = dataStore.readData(LIMIT_SWAP_ENABLED_KEY, false)

    override suspend fun setFeatureEnabled(enabled: Boolean) {
        dataStore.set(LIMIT_SWAP_ENABLED_KEY, enabled)
    }

    private companion object {
        val LIMIT_SWAP_ENABLED_KEY = booleanPreferencesKey("limit_swap_enabled")
    }
}
