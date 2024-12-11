package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.utils.FourByteResponseSerializer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.json.Json
import javax.inject.Inject

interface FourByteApi {
    suspend fun decodeFunction(hash: String): String
}

class FourByteApiImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val fourByteResponseSerializer: FourByteResponseSerializer,
) : FourByteApi {
    override suspend fun decodeFunction(hash: String): String {
        val response = httpClient
            .get("https://www.4byte.directory/api/v1/signatures/?format=json&hex_signature=${hash}&ordering=created_at")
        return json.decodeFromString(
            fourByteResponseSerializer,
            response.body<String>()
        ).list.firstOrNull()?.textSignature ?: ""
    }
}