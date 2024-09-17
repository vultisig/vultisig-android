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

interface KeyApi {
    suspend fun keygenCheckCommittee(serverAddress: String, sessionId: String): List<String>
    suspend fun keygenStart(serverAddress: String, sessionId: String, localPartyId: List<String>)
    suspend fun keygenStartWithCommittee(serverAddress: String, sessionId: String, committee: List<String>)
    suspend fun keygenMarkLocalPartyComplete(serverAddress: String, sessionId: String, localPartyId: List<String>)
    suspend fun keygenGetCompletedParties(serverAddress: String, sessionId: String): List<String>
}

internal class KeyApiImp @Inject constructor(
    private val json: Json,
    private val httpClient: HttpClient,
) : KeyApi {
    // keygen
    override suspend fun keygenCheckCommittee(
        serverAddress: String,
        sessionId: String,
    ) : List<String> {
        val response = httpClient.get("$serverAddress/start/$sessionId")
            .throwIfUnsuccessful()
        return response.body<List<String>>()
    }

    override suspend fun keygenStart(
        serverAddress: String,
        sessionId: String,
        localPartyId: List<String>,
    ) {
        httpClient.post("$serverAddress/$sessionId"){
            setBody(json.encodeToString(localPartyId))
        }.throwIfUnsuccessful()
    }

    override suspend fun keygenStartWithCommittee(
        serverAddress: String,
        sessionId: String,
        committee: List<String>,
    ) {
        httpClient.post("$serverAddress/start/$sessionId"){
            setBody(json.encodeToString(committee))
        }.throwIfUnsuccessful()
    }

    override suspend fun keygenMarkLocalPartyComplete(
        serverAddress: String,
        sessionId: String,
        localPartyId: List<String>,
    ) {
        httpClient.post("$serverAddress/complete/$sessionId"){
            setBody(json.encodeToString(localPartyId))
        }.throwIfUnsuccessful()
    }

    override suspend fun keygenGetCompletedParties(
        serverAddress: String,
        sessionId: String,
    ) : List<String> {
        val response = httpClient.get("$serverAddress/complete/$sessionId")
            .throwIfUnsuccessful()
        return response.body<List<String>>()
    }

}