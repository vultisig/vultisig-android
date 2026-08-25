package com.vultisig.wallet.data.passcode

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * DataStore-backed feature flag for the passcode app-lock. Default is `true` — the feature is
 * generally available, with an opt-out via the Advanced Settings screen.
 *
 * The flag gates whether the feature is *offered*, not whether it is *enforced*. A passcode that
 * has already been set keeps locking the app and keeps its keyshares encrypted even after the flag
 * is turned off, and Settings keeps showing the entry so the user can always turn it off again.
 * Anything else would leave a user holding an encrypted vault with no way back.
 */
interface PasscodeConfig {
    /** Live flow of the Advanced Settings → Passcode toggle. Defaults to `true`. */
    val isFeatureEnabled: Flow<Boolean>

    /** Persists the user's new toggle value. */
    suspend fun setFeatureEnabled(enabled: Boolean)
}

/** [PasscodeConfig] backed by the shared [AppDataStore] preferences store. */
internal class PasscodeConfigImpl @Inject constructor(private val dataStore: AppDataStore) :
    PasscodeConfig {

    override val isFeatureEnabled: Flow<Boolean>
        get() = dataStore.readData(PASSCODE_ENABLED_KEY, true)

    override suspend fun setFeatureEnabled(enabled: Boolean) {
        dataStore.set(PASSCODE_ENABLED_KEY, enabled)
    }

    private companion object {
        val PASSCODE_ENABLED_KEY = booleanPreferencesKey("passcode_feature_enabled")
    }
}
