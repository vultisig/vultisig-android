package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.NotificationApi
import com.vultisig.wallet.data.api.models.DeviceType
import com.vultisig.wallet.data.api.models.NotifyRequestJson
import com.vultisig.wallet.data.api.models.RegisterDeviceRequestJson
import com.vultisig.wallet.data.api.models.UnregisterDeviceRequestJson
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

interface NotificationRepository {
    suspend fun registerDevice(vaultId: String, partyName: String)

    suspend fun unregisterDevice(vaultId: String, partyName: String)

    suspend fun isVaultRegistered(vaultId: String): Boolean

    suspend fun notifyParties(
        vaultId: String,
        vaultName: String,
        localPartyId: String,
        qrCodeData: String,
    )
}

internal class NotificationRepositoryImpl
@Inject
constructor(
    private val notificationApi: NotificationApi,
    private val notificationTokenRepository: NotificationTokenRepository,
) : NotificationRepository {

    override suspend fun registerDevice(vaultId: String, partyName: String) {
        val token = notificationTokenRepository.token.firstOrNull() ?: return
        notificationApi.register(
            RegisterDeviceRequestJson(
                vaultId = vaultId,
                partyName = partyName,
                token = token,
                deviceType = DeviceType.Android,
            )
        )
    }

    override suspend fun unregisterDevice(vaultId: String, partyName: String) {
        val token = notificationTokenRepository.token.firstOrNull()
        notificationApi.unregister(
            UnregisterDeviceRequestJson(vaultId = vaultId, partyName = partyName, token = token)
        )
    }

    override suspend fun isVaultRegistered(vaultId: String): Boolean =
        notificationApi.isVaultRegistered(vaultId)

    override suspend fun notifyParties(
        vaultId: String,
        vaultName: String,
        localPartyId: String,
        qrCodeData: String,
    ) {
        notificationApi.notify(
            NotifyRequestJson(
                vaultId = vaultId,
                vaultName = vaultName,
                localPartyId = localPartyId,
                qrCodeData = qrCodeData,
            )
        )
    }
}
