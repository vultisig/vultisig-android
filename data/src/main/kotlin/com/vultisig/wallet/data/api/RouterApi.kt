package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.utils.throwIfUnsuccessful
import com.vultisig.wallet.data.common.sha256
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import javax.inject.Inject

interface RouterApi {
    suspend fun getPayload(serverURL: String, hash: String): String
    suspend fun uploadPayload(serverURL: String, payload: String): String
    suspend fun shouldUploadPayload(payload: String): Boolean
}

internal class RouterApiImp @Inject constructor(
    private val httpClient: HttpClient,
) : RouterApi {
    override suspend fun getPayload(serverURL: String, hash: String): String {
        return httpClient.get("$serverURL/payload/$hash").throwIfUnsuccessful()
            .body<String>()
    }

    override suspend fun uploadPayload(serverURL: String, payload: String): String {
        if (serverURL.isEmpty()) {
            return ""
        }
        val hash = payload.sha256()
        httpClient.post("$serverURL/payload/$hash") {
            setBody(payload)
        }.throwIfUnsuccessful()
        return hash
    }

    override suspend fun shouldUploadPayload(payload: String): Boolean {
        return true
    }
}