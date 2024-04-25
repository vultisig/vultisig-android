package com.voltix.wallet.data.on_board.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.voltix.wallet.data.common.data_store.AppDataStore
import com.voltix.wallet.data.on_board.mappers.toDomain
import com.voltix.wallet.data.on_board.static_data.getOnBoardingPages
import com.voltix.wallet.on_board.repository.OnBoardRepository
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


    override fun onBoardPages() = getOnBoardingPages().map { it.toDomain() }


    private companion object PreferencesKey {
        val onBoardingKey = booleanPreferencesKey(name = "on_boarding_completed")
    }
}