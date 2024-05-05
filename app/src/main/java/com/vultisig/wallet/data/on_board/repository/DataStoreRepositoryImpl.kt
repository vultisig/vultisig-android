package com.vultisig.wallet.data.on_board.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vultisig.wallet.data.common.data_store.AppDataStore
import com.vultisig.wallet.data.on_board.static_data.getOnBoardingPages
import com.vultisig.wallet.on_board.OnBoardRepository
import javax.inject.Inject


class DataStoreRepositoryImpl @Inject constructor(private val appDataStore: AppDataStore) :
    OnBoardRepository {


    override suspend fun saveOnBoardingState(completed: Boolean) {
        appDataStore.editData { preferences ->
            preferences[onBoardingKey] = completed
        }
    }

    override fun readOnBoardingState() =
        appDataStore.readData(onBoardingKey, false)


    override fun onBoardPages() = getOnBoardingPages()


    private companion object PreferencesKey {
        val onBoardingKey = booleanPreferencesKey(name = "on_boarding_completed")
    }
}