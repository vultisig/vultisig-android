package com.vultisig.wallet.data.services

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.firebase.messaging.FirebaseMessaging
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.DeviceRegistrationRequest
import com.vultisig.wallet.data.api.DeviceUnregisterRequest
import com.vultisig.wallet.data.api.NotificationApi
import com.vultisig.wallet.data.api.NotifyRequest
import com.vultisig.wallet.data.db.dao.VaultNotificationSettingsDao
import com.vultisig.wallet.data.db.models.VaultNotificationSettingsEntity
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.isSecureVault
import com.vultisig.wallet.data.repositories.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

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

    suspend fun onNewToken(token: String) {
        encryptedPrefs.edit { putString(FCM_TOKEN_KEY, token) }
        reRegisterAllOptedInVaults(token)
    }

    suspend fun refreshTokenIfNeeded() {
        if (getStoredToken() != null) return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            onNewToken(token)
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch FCM token")
        }
    }

    private suspend fun reRegisterAllOptedInVaults(token: String) {
        val enabledSettings = vaultNotificationSettingsDao.getAllEnabled()
        enabledSettings.forEach { settings ->
            val vault =
                vaultRepository.get(settings.vaultId)?.takeIf { it.isSecureVault() }
                    ?: return@forEach
            try {
                notificationApi.registerDevice(
                    DeviceRegistrationRequest(
                        vaultId = notificationVaultId(vault),
                        partyName = vault.localPartyID,
                        token = token,
                    )
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to re-register vault ${vault.id} with new FCM token")
            }
        }
    }

    private fun notificationVaultId(vault: Vault): String {
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
        val vault = vaultRepository.get(vaultId) ?: throw PushNotificationError.VaultNotFound()
        if (!vault.isSecureVault()) throw PushNotificationError.VaultNotSupported()

        ensureSettingsExist(vault.id)

        if (enabled) {
            refreshTokenIfNeeded()
        }

        val token = getStoredToken()
        val notificationVaultId = notificationVaultId(vault)

        try {
            if (enabled) {
                if (token == null) throw PushNotificationError.TokenNotAvailable()
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
        } catch (e: PushNotificationError) {
            throw e
        } catch (e: Exception) {
            throw PushNotificationError.ApiFailure(e)
        }

        vaultNotificationSettingsDao.setEnabled(vault.id, enabled)
    }

    suspend fun setAllVaultsOptIn(enabled: Boolean) {
        val allVaults = vaultRepository.getAll().filter { it.isSecureVault() }

        if (enabled) refreshTokenIfNeeded()
        val token = if (enabled) {
            getStoredToken() ?: throw PushNotificationError.TokenNotAvailable()
        } else null

        val succeededVaults = mutableListOf<Vault>()
        try {
            allVaults.forEach { vault ->
                val notificationVaultId = notificationVaultId(vault)
                if (enabled) {
                    notificationApi.registerDevice(
                        DeviceRegistrationRequest(
                            vaultId = notificationVaultId,
                            partyName = vault.localPartyID,
                            token = token!!,
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
                succeededVaults.add(vault)
            }
            vaultNotificationSettingsDao.setEnabledForAll(allVaults.map { it.id }, enabled)
        } catch (e: Exception) {
            if (succeededVaults.isNotEmpty()) {
                rollbackApiCalls(succeededVaults, wasEnabling = enabled, token = token)
                vaultNotificationSettingsDao.setEnabledForAll(
                    succeededVaults.map { it.id },
                    !enabled
                )
            }
            throw e as? PushNotificationError ?: PushNotificationError.ApiFailure(e)
        }
    }

    private suspend fun rollbackApiCalls(
        vaults: List<Vault>,
        wasEnabling: Boolean,
        token: String?,
    ) {
        vaults.forEach { vault ->
            try {
                val notificationVaultId = notificationVaultId(vault)
                if (wasEnabling) {
                    notificationApi.unregisterDevice(
                        DeviceUnregisterRequest(
                            vaultId = notificationVaultId,
                            partyName = vault.localPartyID,
                        )
                    )
                } else if (token != null) {
                    notificationApi.registerDevice(
                        DeviceRegistrationRequest(
                            vaultId = notificationVaultId,
                            partyName = vault.localPartyID,
                            token = token,
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to rollback API call for vault ${vault.id}")
            }
        }
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

sealed class PushNotificationError(message: String) : Exception(message) {
    class VaultNotFound : PushNotificationError("Vault not found")
    class VaultNotSupported : PushNotificationError("Vault does not support notifications")
    class TokenNotAvailable : PushNotificationError("No FCM token available")
    class ApiFailure(cause: Throwable) : PushNotificationError("API call failed: ${cause.message}")
}

fun PushNotificationError.toStringRes(): Int = when (this) {
    is PushNotificationError.VaultNotFound -> R.string.push_notification_vault_not_found
    is PushNotificationError.VaultNotSupported -> R.string.push_notification_error_vault_not_supported
    is PushNotificationError.TokenNotAvailable -> R.string.push_notification_error_token_not_available
    is PushNotificationError.ApiFailure -> R.string.push_notification_error_api_failure
}
