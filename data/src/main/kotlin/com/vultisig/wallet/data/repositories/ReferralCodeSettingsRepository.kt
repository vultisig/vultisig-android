package com.vultisig.wallet.data.repositories

import android.annotation.SuppressLint
import android.content.SharedPreferences
import jakarta.inject.Inject
import androidx.core.content.edit

interface ReferralCodeSettingsRepositoryContract {
    fun hasUsedReferralCode(): Boolean
    fun useReferralCode()
}

class ReferralCodeSettingsRepository @Inject constructor(
    private val encryptedSharedPreferences: SharedPreferences
): ReferralCodeSettingsRepositoryContract {
    override fun hasUsedReferralCode(): Boolean {
        return encryptedSharedPreferences.getBoolean(REFERRAL_CODE_KEY, false)
    }

    @SuppressLint("CommitPrefEdits")
    override fun useReferralCode() {
        encryptedSharedPreferences.edit { putBoolean(REFERRAL_CODE_KEY, true) }
    }

    private companion object {
        const val REFERRAL_CODE_KEY = "referral_code"
    }
}