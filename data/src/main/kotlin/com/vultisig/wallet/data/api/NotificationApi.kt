package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.NotifyRequestJson
import com.vultisig.wallet.data.api.models.RegisterDeviceRequestJson
import com.vultisig.wallet.data.api.models.UnregisterDeviceRequestJson
import com.vultisig.wallet.data.api.utils.throwIfUnsuccessful
import com.vultisig.wallet.data.common.Endpoints
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import javax.inject.Inject

interface NotificationApi {
    suspend fun register(request: RegisterDeviceRequestJson)

    suspend fun unregister(request: UnregisterDeviceRequestJson)

    suspend fun isVaultRegistered(vaultId: String): Boolean

    suspend fun notify(request: NotifyRequestJson)
}

internal class NotificationApiImpl @Inject constructor(private val http: HttpClient) :
    NotificationApi {

    override suspend fun register(request: RegisterDeviceRequestJson) {
        http.post("$URL/register") { setBody(request) }.throwIfUnsuccessful()
    }

    override suspend fun unregister(request: UnregisterDeviceRequestJson) {
        http.delete("$URL/unregister") { setBody(request) }.throwIfUnsuccessful()
    }

    override suspend fun isVaultRegistered(vaultId: String): Boolean {
        val response = http.get("$URL/vault/$vaultId")
        return response.status.isSuccess()
    }

    override suspend fun notify(request: NotifyRequestJson) {
        http.post("$URL/notify") { setBody(request) }.throwIfUnsuccessful()
    }

    companion object {
        private const val URL = Endpoints.VULTISIG_NOTIFICATION_URL
    }
}
