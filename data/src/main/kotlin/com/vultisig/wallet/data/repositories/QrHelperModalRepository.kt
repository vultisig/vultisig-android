package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.first

interface QrHelperModalRepository {
    suspend fun isVisited(): Boolean

    suspend fun visited()
}

internal class QrHelperModalRepositoryImpl
@Inject
constructor(private val dataStore: AppDataStore) : QrHelperModalRepository {

    override suspend fun isVisited(): Boolean =
        dataStore
            .readData(key = booleanPreferencesKey(QR_HELPER_MODAL_KEY), defaultValue = false)
            .first()

    override suspend fun visited() {
        dataStore.editData { preferences ->
            preferences.set(key = booleanPreferencesKey(QR_HELPER_MODAL_KEY), value = true)
        }
    }

    companion object {
        private const val QR_HELPER_MODAL_KEY = "qr_helper_modal_key"
    }
}
