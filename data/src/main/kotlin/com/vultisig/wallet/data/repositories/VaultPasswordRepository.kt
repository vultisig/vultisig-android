package com.vultisig.wallet.data.repositories

import android.content.SharedPreferences
import jakarta.inject.Inject
import androidx.core.content.edit

private const val PREFIX = "vault_password_"

interface VaultPasswordRepository {
    fun savePassword(vaultId: String, password: String)
    fun getPassword(vaultId: String): String?
    fun clearPassword(vaultId: String)
}

internal class VaultPasswordRepositoryImpl @Inject constructor(
    private val encryptedSharedPreferences: SharedPreferences

): VaultPasswordRepository {

    override fun savePassword(vaultId: String, password: String) {
        encryptedSharedPreferences.edit() { putString(PREFIX + vaultId, password) }
    }

    override fun getPassword(vaultId: String): String? {
        return encryptedSharedPreferences.getString(PREFIX + vaultId, null)
    }

    override fun clearPassword(vaultId: String) {
        encryptedSharedPreferences.edit() { remove(PREFIX + vaultId) }
    }
}