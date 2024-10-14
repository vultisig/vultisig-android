package com.vultisig.wallet.data.api.utils

import com.vultisig.wallet.data.api.models.RpcError
import com.vultisig.wallet.data.api.models.RpcPayload
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class RpcResponseJson(
    @SerialName("id")
    val id: Int,
    @SerialName("result")
    val result: JsonElement? = null,
    @SerialName("error")
    val error: RpcError? = null,
)

internal suspend inline fun <reified T> HttpClient.postRpc(
    url: String,
    method: String,
    params: JsonArray,
    id: Int = 1,
): T = post(url) {
    setBody(
        RpcPayload(
            method = method,
            params = params,
            id = id
        )
    )
}.body()