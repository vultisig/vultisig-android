package com.vultisig.wallet.data.repositories.onboarding

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface OnboardingSecureBackupRepository {
    suspend fun saveOnboardingState(completed: OnboardingSecureBackupState)
    fun readOnboardingState(): Flow<OnboardingSecureBackupState>
}

internal class OnboardingSecureBackupRepositoryImpl @Inject constructor(private val appDataStore: AppDataStore) :
    OnboardingSecureBackupRepository {

    override suspend fun saveOnboardingState(completed: OnboardingSecureBackupState) {
        appDataStore.editData { preferences ->
            preferences[onBoardingKey] = completed.toString()
        }
    }

    override fun readOnboardingState() =
        appDataStore.readData(onBoardingKey, "NOT_COMPLETED")
            .map { OnboardingSecureBackupState.valueOf(it) }

    private companion object PreferencesKey {
        val onBoardingKey = stringPreferencesKey(name = "on_boarding_secure_backup_test")
    }
}

enum class OnboardingSecureBackupState {
    NOT_COMPLETED, COMPLETED_MAIN, COMPLETED_SUMMARY
}