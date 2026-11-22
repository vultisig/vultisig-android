package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.utils.throwIfUnsuccessful
import com.vultisig.wallet.data.common.sha256
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.utils.io.readRemaining
import javax.inject.Inject
import kotlinx.io.readByteArray

interface RouterApi {
    suspend fun getPayload(serverURL: String, hash: String): String

    suspend fun uploadPayload(serverURL: String, payload: String): String

    suspend fun shouldUploadPayload(payload: String): Boolean
}

internal class RouterApiImp @Inject constructor(private val httpClient: HttpClient) : RouterApi {
    override suspend fun getPayload(serverURL: String, hash: String): String {
        require(PAYLOAD_ID.matches(hash)) { "Invalid signing payload ID" }
        return httpClient
            .prepareGet("$serverURL/payload/$hash") { header(HttpHeaders.CacheControl, "no-store") }
            .execute { response ->
                response.throwIfUnsuccessful()
                val length = response.contentLength()
                require(length == null || length in 1..MAX_PAYLOAD_BYTES.toLong()) {
                    "Invalid signing payload size"
                }
                val bytes =
                    response.bodyAsChannel().readRemaining(MAX_PAYLOAD_BYTES + 1L).readByteArray()
                require(bytes.size in 1..MAX_PAYLOAD_BYTES) { "Invalid signing payload size" }
                val payload = bytes.decodeToString(throwOnInvalidSequence = true)
                // Android and iOS both address relay payloads by the SHA-256 of their UTF-8 body.
                require(payload.sha256().equals(hash, ignoreCase = true)) {
                    "Signing payload does not match its ID"
                }
                payload
            }
    }

    override suspend fun uploadPayload(serverURL: String, payload: String): String {
        if (serverURL.isEmpty()) {
            return ""
        }
        require(
            payload.length in 1..MAX_PAYLOAD_BYTES &&
                payload.encodeToByteArray().size <= MAX_PAYLOAD_BYTES
        ) {
            "Invalid signing payload size"
        }
        val hash = payload.sha256()
        httpClient.post("$serverURL/payload/$hash") { setBody(payload) }.throwIfUnsuccessful()
        return hash
    }

    override suspend fun shouldUploadPayload(payload: String): Boolean {
        return payload.length > 2048
    }

    private companion object {
        val PAYLOAD_ID = Regex("[0-9a-fA-F]{64}")
        const val MAX_PAYLOAD_BYTES = 1024 * 1024
    }
}
