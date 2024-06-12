package com.vultisig.wallet.common

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

internal class vultisigRelay @Inject constructor(private val appDataStore: AppDataStore) {
    var IsRelayEnabled: Boolean
        get() = runBlocking { appDataStore.readData(relayKey, true).first() }
        set(value) = runBlocking {
            appDataStore.editData { preferences ->
                preferences[relayKey] = value
            }
        }

    companion object {
        private val relayKey = booleanPreferencesKey(name = "relay_enabled")
    }
}