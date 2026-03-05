package com.vultisig.wallet.data.notifications

import android.content.SharedPreferences
import androidx.core.content.edit
import com.vultisig.wallet.data.api.DeviceRegistrationRequest
import com.vultisig.wallet.data.api.DeviceUnregisterRequest
import com.vultisig.wallet.data.api.NotificationApi
import com.vultisig.wallet.data.api.NotifyRequest
import com.vultisig.wallet.data.db.dao.VaultNotificationSettingsDao
import com.vultisig.wallet.data.db.models.VaultNotificationSettingsEntity
import com.vultisig.wallet.data.models.Vault
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

@Singleton
class PushNotificationManager
@Inject
constructor(
    private val notificationApi: NotificationApi,
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

    fun observeVaultOptedIn(vaultId: String): Flow<Boolean> {
        return vaultNotificationSettingsDao.observeByVaultId(vaultId).map {
            it?.notificationsEnabled == true
        }
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

    suspend fun setVaultOptIn(vault: Vault, enabled: Boolean) {
        ensureSettingsExist(vault.id)
        vaultNotificationSettingsDao.setEnabled(vault.id, enabled)

        val token = getStoredToken() ?: return
        val vaultId = notificationVaultId(vault)

        if (enabled) {
            notificationApi.registerDevice(
                DeviceRegistrationRequest(
                    vaultId = vaultId,
                    partyName = vault.localPartyID,
                    token = token,
                )
            )
        } else {
            notificationApi.unregisterDevice(
                DeviceUnregisterRequest(vaultId = vaultId, partyName = vault.localPartyID)
            )
        }
    }

    suspend fun setAllVaultsOptIn(vaults: List<Vault>, enabled: Boolean) {
        vaults.forEach { vault -> setVaultOptIn(vault, enabled) }
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
