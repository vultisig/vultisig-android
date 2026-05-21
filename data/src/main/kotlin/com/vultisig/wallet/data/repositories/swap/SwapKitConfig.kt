package com.vultisig.wallet.data.repositories.swap

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * DataStore-backed feature flag for the SwapKit aggregator. Default is `false` — when the toggle is
 * off the [SwapKitQuoteSource] short-circuits without network I/O, so behaviour is identical to
 * before the SwapKit integration shipped.
 */
interface SwapKitConfig {
    /** Live flow of the user's Advanced Settings → SwapKit toggle. Defaults to `false`. */
    val isFeatureEnabled: Flow<Boolean>

    /** Persists the user's new toggle value. */
    suspend fun setFeatureEnabled(enabled: Boolean)
}

/** [SwapKitConfig] backed by the shared [AppDataStore] preferences store. */
internal class SwapKitConfigImpl @Inject constructor(private val dataStore: AppDataStore) :
    SwapKitConfig {

    override val isFeatureEnabled: Flow<Boolean>
        get() = dataStore.readData(SWAPKIT_ENABLED_KEY, false)

    override suspend fun setFeatureEnabled(enabled: Boolean) {
        dataStore.set(SWAPKIT_ENABLED_KEY, enabled)
    }

    companion object {
        /** DataStore key persisting the user's Advanced Settings → SwapKit toggle. */
        val SWAPKIT_ENABLED_KEY = booleanPreferencesKey("swapkit_enabled")
    }
}
