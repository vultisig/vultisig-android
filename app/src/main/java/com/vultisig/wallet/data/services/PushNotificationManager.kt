package com.vultisig.wallet.data.services

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.core.content.edit
import com.vultisig.wallet.data.api.DeviceRegistrationRequest
import com.vultisig.wallet.data.api.DeviceUnregisterRequest
import com.vultisig.wallet.data.api.NotificationApi
import com.vultisig.wallet.data.api.NotifyRequest
import com.vultisig.wallet.data.db.dao.VaultNotificationSettingsDao
import com.vultisig.wallet.data.db.models.VaultNotificationSettingsEntity
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.isSecureVault
import com.vultisig.wallet.data.repositories.VaultRepository
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

@Singleton
class PushNotificationManager
@Inject
constructor(
    private val notificationApi: NotificationApi,
    private val vaultRepository: VaultRepository,
    private val vaultNotificationSettingsDao: VaultNotificationSettingsDao,
    private val encryptedPrefs: SharedPreferences,
) {
    fun getStoredToken(): String? = encryptedPrefs.getString(FCM_TOKEN_KEY, null)

    fun onNewToken(token: String) {
        encryptedPrefs.edit { putString(FCM_TOKEN_KEY, token) }
    }

    fun notificationVaultId(vault: Vault): String {
        val input = (vault.pubKeyECDSA + vault.hexChainCode).toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun isVaultOptedIn(vaultId: String): Boolean {
        return vaultNotificationSettingsDao.getByVaultId(vaultId)?.notificationsEnabled == true
    }

    fun observeAllSettings(): Flow<List<VaultNotificationSettingsEntity>> {
        return vaultNotificationSettingsDao.observeAll()
    }

    suspend fun hasPromptedVault(vaultId: String): Boolean {
        return vaultNotificationSettingsDao.getByVaultId(vaultId)?.notificationsPrompted == true
    }

    suspend fun markVaultPrompted(vaultId: String) {
        ensureSettingsExist(vaultId)
        vaultNotificationSettingsDao.markPrompted(vaultId)
    }

    @SuppressLint(
        "ImplicitSamInstance"
    ) // False positive: DeviceUnregisterRequest is a data class, not a SAM interface
    suspend fun setVaultOptIn(vaultId: String, enabled: Boolean) {
        val vault = vaultRepository.get(vaultId)?.takeIf { it.isSecureVault() } ?: return
        ensureSettingsExist(vault.id)

        val token = getStoredToken()
        val notificationVaultId = notificationVaultId(vault)

        if (enabled) {
            if (token == null) {
                Timber.w("Cannot enable notifications: no FCM token available")
                return
            }
            notificationApi.registerDevice(
                DeviceRegistrationRequest(
                    vaultId = notificationVaultId,
                    partyName = vault.localPartyID,
                    token = token,
                )
            )
        } else {
            notificationApi.unregisterDevice(
                DeviceUnregisterRequest(
                    vaultId = notificationVaultId,
                    partyName = vault.localPartyID,
                )
            )
        }

        vaultNotificationSettingsDao.setEnabled(vault.id, enabled)
    }

    suspend fun setAllVaultsOptIn(enabled: Boolean) {
        val allVaults = vaultRepository.getAll().filter { it.isSecureVault() }
        allVaults.forEach { vault -> setVaultOptIn(vaultId = vault.id, enabled) }
    }

    suspend fun notifyVaultDevices(vault: Vault, qrCodeData: String) {
        val token = getStoredToken()
        if (token == null) {
            Timber.d("No FCM token available, skipping notification")
            return
        }
        if (!isVaultOptedIn(vault.id)) {
            Timber.d("Vault ${vault.id} is not opted into notifications, skipping")
            return
        }
        notificationApi.notify(
            NotifyRequest(
                vaultId = notificationVaultId(vault),
                vaultName = vault.name,
                localPartyId = vault.localPartyID,
                qrCodeData = qrCodeData,
            )
        )
    }

    private suspend fun ensureSettingsExist(vaultId: String) {
        vaultNotificationSettingsDao.insertIfNotExists(
            VaultNotificationSettingsEntity(vaultId = vaultId)
        )
    }

    companion object {
        private const val FCM_TOKEN_KEY = "fcm_device_token"
    }
}
