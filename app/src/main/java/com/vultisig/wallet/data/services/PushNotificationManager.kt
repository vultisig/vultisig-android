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

@SuppressLint(
    "ImplicitSamInstance"
) // False positive: DeviceUnregisterRequest is a data class, not a SAM interface
@Singleton
class PushNotificationManager
@Inject
constructor(
    private val notificationApi: NotificationApi,
    private val vaultRepository: VaultRepository,
    private val vaultNotificationSettingsDao: VaultNotificationSettingsDao,
    private val encryptedPrefs: SharedPreferences,
    private val fcmTokenProvider: FcmTokenProvider,
) {
    fun getStoredToken(): String? = encryptedPrefs.getString(FCM_TOKEN_KEY, null)

    /**
     * Persists a freshly minted token. Synchronous by design: `FirebaseMessagingService` stops
     * itself once `onNewToken` returns, so a token handed to a coroutine can be lost when the
     * service's scope is torn down mid-flight. The network half is re-registration, which
     * [PushRegistrationWorker] owns.
     */
    fun storeToken(token: String) {
        encryptedPrefs.edit { putString(FCM_TOKEN_KEY, token) }
    }

    suspend fun refreshTokenIfNeeded() {
        currentToken()
    }

    /**
     * The token to register with, minting and persisting one if none is stored yet.
     *
     * @return null when no token could be obtained; the caller should retry later rather than treat
     *   this as a permanent state.
     */
    suspend fun currentToken(): String? {
        getStoredToken()?.let {
            return it
        }
        return try {
            fcmTokenProvider.fetchToken().also { storeToken(it) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "Failed to fetch FCM token")
            null
        }
    }

    /**
     * Re-points every opted-in vault at [token] on the server.
     *
     * @return false when at least one vault failed, so the caller can reschedule. Registrations
     *   that are never retried leave the server holding a dead token while the local toggle still
     *   reads ON — pushes stop and never heal.
     */
    suspend fun reRegisterOptedInVaults(token: String): Boolean {
        val enabledSettings = vaultNotificationSettingsDao.getAllEnabled()
        var allSucceeded = true
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w(e, "Failed to re-register vault %s with new FCM token", vault.id)
                allSucceeded = false
            }
        }
        return allSucceeded
    }

    suspend fun hasOptedInVaults(): Boolean =
        vaultNotificationSettingsDao.getAllEnabled().isNotEmpty()

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
            vaultNotificationSettingsDao.setEnabled(vault.id, enabled)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw PushNotificationError.ApiFailure(e)
        }
    }

    suspend fun setVaultsOptIn(vaultOptIns: List<Pair<String, Boolean>>) {
        val anyEnabling = vaultOptIns.any { (_, enabled) -> enabled }
        if (anyEnabling) {
            refreshTokenIfNeeded()
            if (getStoredToken() == null) throw PushNotificationError.TokenNotAvailable()
        }
        val token = getStoredToken()

        var successCount = 0
        var failureCount = 0
        vaultOptIns.forEach { (vaultId, enabled) ->
            val vault =
                vaultRepository.get(vaultId)?.takeIf { it.isSecureVault() }
                    ?: run {
                        Timber.w("Vault $vaultId not found or not supported, skipping")
                        failureCount++
                        return@forEach
                    }
            ensureSettingsExist(vault.id)
            try {
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
                vaultNotificationSettingsDao.setEnabled(vault.id, enabled)
                successCount++
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w(e, "Failed to update notification opt-in for vault ${vault.id}")
                failureCount++
            }
        }
        if (failureCount > 0) {
            throw PushNotificationError.PartialFailure(successCount, failureCount)
        }
    }

    suspend fun setAllVaultsOptIn(enabled: Boolean) {
        val allVaults = vaultRepository.getAll().filter { it.isSecureVault() }
        setVaultsOptIn(allVaults.map { it.id to enabled })
    }

    suspend fun notifyVaultDevices(vault: Vault, qrCodeData: String) {
        val token = getStoredToken()
        if (token == null) {
            Timber.d("No FCM token available, skipping notification")
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

    class PartialFailure(val successCount: Int, val failureCount: Int) :
        PushNotificationError("Updated $successCount vaults, $failureCount failed")
}
