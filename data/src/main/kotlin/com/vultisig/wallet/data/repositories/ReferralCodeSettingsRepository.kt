package com.vultisig.wallet.data.repositories

import android.content.SharedPreferences
import jakarta.inject.Inject
import androidx.core.content.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vultisig.wallet.data.sources.AppDataStore
import kotlinx.coroutines.flow.first

interface ReferralCodeSettingsRepositoryContract {
    fun hasVisitReferralCode(): Boolean
    fun visitReferralCode()
    fun getReferralCreatedBy(vaultId: String): String?
    fun saveReferralCreated(vaultId: String, referralCode: String)
    fun getExternalReferralBy(vaultId: String): String?
    fun saveExternalReferral(vaultId: String, referralCode: String?)
    fun getCurrentVaultId(): String?
    fun setCurrentVaultId(vaultId: String)
    suspend fun setAsShown()
    suspend fun isShown(): Boolean
}

class ReferralCodeSettingsRepository @Inject constructor(
    private val encryptedSharedPreferences: SharedPreferences,
    private val appDataStore: AppDataStore,
) : ReferralCodeSettingsRepositoryContract {
    override fun hasVisitReferralCode(): Boolean {
        return encryptedSharedPreferences.getBoolean(HAS_VISIT_REFERRAL_CODE_KEY, false)
    }

    override fun visitReferralCode() {
        encryptedSharedPreferences.edit { putBoolean(HAS_VISIT_REFERRAL_CODE_KEY, true) }
    }

    override fun getReferralCreatedBy(vaultId: String): String? {
        val key = VAULT_REFERRAL_CODE_KEY + vaultId

        return encryptedSharedPreferences.getString(key, null)
    }

    override fun saveReferralCreated(vaultId: String, referralCode: String) {
        val key = VAULT_REFERRAL_CODE_KEY + vaultId

        encryptedSharedPreferences.edit { putString(key, referralCode) }
    }

    override fun getExternalReferralBy(vaultId: String): String? {
        val key = EXTERNAL_REFERRAL_CODE_KEY + vaultId

        return encryptedSharedPreferences.getString(key, null)
    }

    override fun saveExternalReferral(vaultId: String, referralCode: String?) {
        val key = EXTERNAL_REFERRAL_CODE_KEY + vaultId

        encryptedSharedPreferences.edit { putString(key, referralCode) }
    }

    override fun getCurrentVaultId(): String? {
        return encryptedSharedPreferences.getString(CURRENT_VAULT_CODE_KEY, null)
    }

    override fun setCurrentVaultId(vaultId: String) {
        encryptedSharedPreferences.edit { putString(CURRENT_VAULT_CODE_KEY, vaultId) }
    }

    override suspend fun setAsShown() {
        appDataStore.editData { preferences ->
            preferences[SHOW_REFERRAL_HOW_IT_WORKS] = true
        }
    }

    override suspend fun isShown() =
        appDataStore
            .readData(SHOW_REFERRAL_HOW_IT_WORKS, false)
            .first()


    private companion object {
        const val HAS_VISIT_REFERRAL_CODE_KEY = "has_visit_referral_code"
        const val VAULT_REFERRAL_CODE_KEY = "referral_code_"
        const val EXTERNAL_REFERRAL_CODE_KEY = "external_referral_code_"
        const val CURRENT_VAULT_CODE_KEY = "current_vault_key"

        private val SHOW_REFERRAL_HOW_IT_WORKS = booleanPreferencesKey("SHOW_REFERRAL_HOW_IT_WORKS")
    }
}