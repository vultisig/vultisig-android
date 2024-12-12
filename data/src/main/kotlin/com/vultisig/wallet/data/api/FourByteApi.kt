package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.FourByteResponseJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import javax.inject.Inject

internal interface FourByteApi {
    suspend fun decodeFunction(hash: String): String?
}

internal class FourByteApiImpl @Inject constructor(
    private val httpClient: HttpClient,
) : FourByteApi {
    override suspend fun decodeFunction(hash: String): String? {
        val response = httpClient
            .get("https://www.4byte.directory/api/v1/signatures/?format=json&hex_signature=${hash}&ordering=created_at")
        return response.body<FourByteResponseJson>()
            .list
            .firstOrNull()
            ?.textSignature
    }
}