package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface OnBoardRepository {
    suspend fun saveOnBoardingState(completed: Boolean)
    fun readOnBoardingState(): Flow<Boolean>
}

internal class OnBoardRepositoryImpl @Inject constructor(private val appDataStore: AppDataStore) :
    OnBoardRepository {


    override suspend fun saveOnBoardingState(completed: Boolean) {
        appDataStore.editData { preferences ->
            preferences[onBoardingKey] = completed
        }
    }

    override fun readOnBoardingState() =
        appDataStore.readData(onBoardingKey, false)

    private companion object PreferencesKey {
        val onBoardingKey = booleanPreferencesKey(name = "on_boarding_completed")
    }
}