package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.utils.throwIfUnsuccessful
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegistrationRequest(
    @SerialName("vault_id") val vaultId: String,
    @SerialName("party_name") val partyName: String,
    @SerialName("token") val token: String,
    @SerialName("device_type") val deviceType: String = "android",
)

@Serializable
data class DeviceUnregisterRequest(
    @SerialName("vault_id") val vaultId: String,
    @SerialName("party_name") val partyName: String,
)

@Serializable
data class NotifyRequest(
    @SerialName("vault_id") val vaultId: String,
    @SerialName("vault_name") val vaultName: String,
    @SerialName("local_party_id") val localPartyId: String,
    @SerialName("qr_code_data") val qrCodeData: String,
)

interface NotificationApi {
    suspend fun registerDevice(request: DeviceRegistrationRequest)

    suspend fun unregisterDevice(request: DeviceUnregisterRequest)

    suspend fun isVaultRegistered(vaultId: String): Boolean

    suspend fun notify(request: NotifyRequest)
}

internal class NotificationApiImpl @Inject constructor(private val http: HttpClient) :
    NotificationApi {

    override suspend fun registerDevice(request: DeviceRegistrationRequest) {
        http.post("$NOTIFICATION_BASE_URL/register") { setBody(request) }.throwIfUnsuccessful()
    }

    override suspend fun unregisterDevice(request: DeviceUnregisterRequest) {
        http.delete("$NOTIFICATION_BASE_URL/unregister") { setBody(request) }.throwIfUnsuccessful()
    }

    override suspend fun isVaultRegistered(vaultId: String): Boolean {
        return http.get("$NOTIFICATION_BASE_URL/vault/$vaultId").status.isSuccess()
    }

    override suspend fun notify(request: NotifyRequest) {
        http.post("$NOTIFICATION_BASE_URL/notify") { setBody(request) }.throwIfUnsuccessful()
    }

    companion object {
        private const val NOTIFICATION_BASE_URL = "https://api.vultisig.com/notification"
    }
}
