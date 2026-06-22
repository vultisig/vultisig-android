package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * DataStore-backed feature flag for the Custom RPC feature (#4787). Default is `true` (returned
 * when the key is absent) — the feature is shown out of the box; users can opt out via Settings →
 * Advanced → Custom RPC. Mirrors [SwapKitConfig] and the iOS `customRPCEnabled` `@AppStorage` flag.
 * When off, the Custom RPC row is not shown in General Settings.
 */
interface CustomRpcConfig {
    /** Live flow of the user's Advanced Settings → Custom RPC toggle. Defaults to `true`. */
    val isFeatureEnabled: Flow<Boolean>

    /** Persists the user's new toggle value. */
    suspend fun setFeatureEnabled(enabled: Boolean)
}

/** [CustomRpcConfig] backed by the shared [AppDataStore] preferences store. */
internal class CustomRpcConfigImpl @Inject constructor(private val dataStore: AppDataStore) :
    CustomRpcConfig {

    override val isFeatureEnabled: Flow<Boolean>
        get() = dataStore.readData(CUSTOM_RPC_ENABLED_KEY, true)

    override suspend fun setFeatureEnabled(enabled: Boolean) {
        dataStore.set(CUSTOM_RPC_ENABLED_KEY, enabled)
    }

    private companion object {
        val CUSTOM_RPC_ENABLED_KEY = booleanPreferencesKey("custom_rpc_enabled")
    }
}
