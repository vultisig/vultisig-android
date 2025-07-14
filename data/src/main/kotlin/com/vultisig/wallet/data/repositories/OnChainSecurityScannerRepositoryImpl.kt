package com.vultisig.wallet.data.repositories

import android.content.SharedPreferences
import androidx.core.content.edit
import jakarta.inject.Inject

interface OnChainSecurityScannerRepository {
    fun getSecurityScannerStatus(): Boolean
    fun saveSecurityScannerStatus(enable: Boolean)
}

internal class OnChainSecurityScannerRepositoryImpl @Inject constructor(
    private val encryptedSharedPreferences: SharedPreferences
): OnChainSecurityScannerRepository {
    override fun getSecurityScannerStatus(): Boolean {
        return encryptedSharedPreferences.getBoolean(SECURITY_SCANNER_KEY, true)
    }

    override fun saveSecurityScannerStatus(enable: Boolean) {
        encryptedSharedPreferences.edit { putBoolean(SECURITY_SCANNER_KEY, enable) }
    }

    private companion object {
        const val SECURITY_SCANNER_KEY = "security_scanner"
    }
}