package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.utils.throwIfUnsuccessful
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

interface KeygenApi {
    suspend fun checkCommittee(serverUrl: String, sessionId: String): List<String>
    suspend fun start(serverUrl: String, sessionId: String, localPartyId: List<String>)
    suspend fun startWithCommittee(serverUrl: String, sessionId: String, committee: List<String>)
    suspend fun markLocalPartyComplete(serverUrl: String, sessionId: String, localPartyId: List<String>)
    suspend fun getCompletedParties(serverUrl: String, sessionId: String): List<String>
}

internal class KeygenApiImpl @Inject constructor(
    private val json: Json,
    private val httpClient: HttpClient,
) : KeygenApi {
    override suspend fun checkCommittee(
        serverUrl: String,
        sessionId: String,
    ) : List<String> {
        return httpClient.get("$serverUrl/start/$sessionId")
            .throwIfUnsuccessful()
            .body<List<String>>()
    }

    override suspend fun start(
        serverUrl: String,
        sessionId: String,
        localPartyId: List<String>,
    ) {
        httpClient.post("$serverUrl/$sessionId"){
            setBody(localPartyId)
        }.throwIfUnsuccessful()
    }

    override suspend fun startWithCommittee(
        serverUrl: String,
        sessionId: String,
        committee: List<String>,
    ) {
        httpClient.post("$serverUrl/start/$sessionId"){
            setBody(committee)
        }.throwIfUnsuccessful()
    }

    override suspend fun markLocalPartyComplete(
        serverUrl: String,
        sessionId: String,
        localPartyId: List<String>,
    ) {
        httpClient.post("$serverUrl/complete/$sessionId"){
            setBody(localPartyId)
        }.throwIfUnsuccessful()
    }

    override suspend fun getCompletedParties(
        serverUrl: String,
        sessionId: String,
    ) : List<String> {
        return httpClient.get("$serverUrl/complete/$sessionId")
            .throwIfUnsuccessful()
            .body<List<String>>()
    }
}