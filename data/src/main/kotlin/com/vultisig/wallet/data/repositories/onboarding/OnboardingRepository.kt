package com.vultisig.wallet.data.repositories.onboarding

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface OnboardingRepository {
    suspend fun saveOnboardingState(completed: Boolean)
    fun readOnboardingState(): Flow<Boolean>
}

internal class OnboardingRepositoryImpl @Inject constructor(private val appDataStore: AppDataStore) :
    OnboardingRepository {

    override suspend fun saveOnboardingState(completed: Boolean) {
        appDataStore.editData { preferences ->
            preferences[onBoardingKey] = completed
        }
    }

    override fun readOnboardingState() =
        appDataStore.readData(onBoardingKey, false)

    private companion object PreferencesKey {
        val onBoardingKey = booleanPreferencesKey(name = "on_boarding_completed")
    }
}