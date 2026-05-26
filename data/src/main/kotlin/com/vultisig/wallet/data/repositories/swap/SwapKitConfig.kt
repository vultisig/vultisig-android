package com.vultisig.wallet.data.repositories.swap

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * DataStore-backed feature flag for the SwapKit aggregator. Default is `true`, matching iOS
 * (`SwapKitConfig.isFeatureEnabled` returns true when the key is absent). SwapKit is an additional
 * competing quote source that the picker still ranks by net output, so it only wins when it beats
 * the existing providers; users can opt out via Settings → Advanced → SwapKit. When off,
 * [SwapKitQuoteSource] short-circuits without any network I/O.
 */
interface SwapKitConfig {
    /** Live flow of the user's Advanced Settings → SwapKit toggle. Defaults to `true`. */
    val isFeatureEnabled: Flow<Boolean>

    /** Persists the user's new toggle value. */
    suspend fun setFeatureEnabled(enabled: Boolean)
}

/** [SwapKitConfig] backed by the shared [AppDataStore] preferences store. */
internal class SwapKitConfigImpl @Inject constructor(private val dataStore: AppDataStore) :
    SwapKitConfig {

    override val isFeatureEnabled: Flow<Boolean>
        get() = dataStore.readData(SWAPKIT_ENABLED_KEY, true)

    override suspend fun setFeatureEnabled(enabled: Boolean) {
        dataStore.set(SWAPKIT_ENABLED_KEY, enabled)
    }

    companion object {
        /** DataStore key persisting the user's Advanced Settings → SwapKit toggle. */
        private val SWAPKIT_ENABLED_KEY = booleanPreferencesKey("swapkit_enabled")
    }
}
