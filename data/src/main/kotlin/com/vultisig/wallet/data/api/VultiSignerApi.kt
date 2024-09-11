package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.signer.JoinKeygenRequestJson
import com.vultisig.wallet.data.api.models.signer.JoinKeysignRequestJson
import com.vultisig.wallet.data.api.utils.throwIfUnsuccessful
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import javax.inject.Inject

internal interface VultiSignerApi {

    suspend fun joinKeygen(
        request: JoinKeygenRequestJson,
    )

    suspend fun joinKeysign(
        requestJson: JoinKeysignRequestJson,
    )

    suspend fun exist(
        publicKeyEcdsa: String,
    )

}

internal class VultiSignerApiImpl @Inject constructor(
    private val http: HttpClient,
) : VultiSignerApi {

    override suspend fun joinKeygen(
        request: JoinKeygenRequestJson,
    ) {
        http.post("https://api.vultisig.com/vault/create") {
            setBody(request)
        }.throwIfUnsuccessful()
    }

    override suspend fun joinKeysign(
        requestJson: JoinKeysignRequestJson,
    ) {
        http.post("https://api.vultisig.com/vault/sign") {
            setBody(requestJson)
        }.throwIfUnsuccessful()
    }

    override suspend fun exist(publicKeyEcdsa: String) {
        http.get("https://api.vultisig.com/vault/exist/$publicKeyEcdsa")
            .throwIfUnsuccessful()
    }

}