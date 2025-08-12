package com.vultisig.wallet.data.repositories

import android.annotation.SuppressLint
import android.content.SharedPreferences
import jakarta.inject.Inject
import androidx.core.content.edit

interface ReferralCodeSettingsRepositoryContract {
    fun hasVisitReferralCode(): Boolean
    fun visitReferralCode()
    fun getReferralCreatedBy(vaultId: String): String?
    fun saveReferralCreated(vaultId: String, referralCode: String)
    fun getExternalReferralBy(vaultId: String): String?
    fun saveExternalReferral(vaultId: String, referralCode: String)
}

class ReferralCodeSettingsRepository @Inject constructor(
    private val encryptedSharedPreferences: SharedPreferences
): ReferralCodeSettingsRepositoryContract {
    override fun hasVisitReferralCode(): Boolean {
        return encryptedSharedPreferences.getBoolean(HAS_VISIT_REFERRAL_CODE_KEY, false)
    }

    @SuppressLint("CommitPrefEdits")
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

    override fun saveExternalReferral(vaultId: String, referralCode: String) {
        val key = EXTERNAL_REFERRAL_CODE_KEY + vaultId

        encryptedSharedPreferences.edit { putString(key, referralCode) }
    }

    private companion object {
        const val HAS_VISIT_REFERRAL_CODE_KEY = "has_visit_referral_code"
        const val VAULT_REFERRAL_CODE_KEY = "referral_code_"
        const val EXTERNAL_REFERRAL_CODE_KEY = "external_referral_code_"
    }
}