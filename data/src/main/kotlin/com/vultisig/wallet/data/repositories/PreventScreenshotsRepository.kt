package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface PreventScreenshotsRepository {
    val isEnabled: Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)
}

internal class PreventScreenshotsRepositoryImpl
@Inject
constructor(private val dataStore: AppDataStore) : PreventScreenshotsRepository {

    override val isEnabled: Flow<Boolean> = dataStore.readData(KEY_PREVENT_SCREENSHOTS, false)

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.set(KEY_PREVENT_SCREENSHOTS, enabled)
    }

    private companion object {
        val KEY_PREVENT_SCREENSHOTS = booleanPreferencesKey("prevent_screenshots")
    }
}
