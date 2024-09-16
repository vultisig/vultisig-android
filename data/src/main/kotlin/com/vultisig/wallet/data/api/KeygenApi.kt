package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.utils.throwIfUnsuccessful
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.client.utils.EmptyContent.headers
import io.ktor.http.charset
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

interface KeygenApi {
    suspend fun checkKeygenCommittee(serverAddress: String, sessionId: String): List<String>
    suspend fun joinKeygen(serverAddress: String, sessionId: String, localPartyId: List<String>)
}

internal class KeygenApiImp @Inject constructor(
    private val json: Json,
    private val httpClient: HttpClient,
) : KeygenApi {
    override suspend fun checkKeygenCommittee(
        serverAddress: String,
        sessionId: String,
    ) : List<String> {
        val response = httpClient.get("$serverAddress/start/$sessionId")
        return response.body<List<String>>()
    }

    override suspend fun joinKeygen(
        serverAddress: String,
        sessionId: String,
        localPartyId: List<String>,
    ) {
        httpClient.post("$serverAddress/$sessionId"){
            setBody(json.encodeToString(localPartyId))
        }.throwIfUnsuccessful()
    }
}