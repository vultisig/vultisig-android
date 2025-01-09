package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.utils.throwIfUnsuccessful
import com.vultisig.wallet.data.mediator.Message
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

interface SessionApi {
    suspend fun checkCommittee(serverUrl: String, sessionId: String): List<String>
    suspend fun startSession(serverUrl: String, sessionId: String, localPartyId: List<String>)
    suspend fun startWithCommittee(serverUrl: String, sessionId: String, committee: List<String>)
    suspend fun markLocalPartyComplete(serverUrl: String, sessionId: String, localPartyId: List<String>)
    suspend fun getCompletedParties(serverUrl: String, sessionId: String): List<String>
    suspend fun getParticipants(serverUrl: String, sessionId: String): List<String>
    suspend fun sendTssMessage(serverUrl: String, messageId: String?, message: Message)

    suspend fun getTssMessages(
        serverUrl: String,
        sessionId: String,
        localPartyId: String,
        messageId: String? = null,
    ): List<Message>
    suspend fun deleteTssMessage(
        serverUrl: String,
        sessionId: String,
        localPartyId: String,
        msgHash: String,
        messageId: String?,
    )

    suspend fun markLocalPartyKeysignComplete(serverUrl: String, messageId: String, sig: tss.KeysignResponse)
    suspend fun checkKeysignComplete(serverUrl: String, messageId: String): tss.KeysignResponse

    suspend fun getSetupMessage(serverUrl: String, sessionId: String): String
    suspend fun uploadSetupMessage(serverUrl: String, sessionId: String, message: String)

}

internal class SessionApiImpl @Inject constructor(
    private val json: Json,
    private val httpClient: HttpClient,
) : SessionApi {
    override suspend fun checkCommittee(
        serverUrl: String,
        sessionId: String,
    ) : List<String> {
        return httpClient.get("$serverUrl/start/$sessionId")
            .throwIfUnsuccessful()
            .body<List<String>>()
    }

    override suspend fun startSession(
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

    override suspend fun getParticipants(serverUrl: String, sessionId: String): List<String> {
        return httpClient.get("$serverUrl/$sessionId")
            .throwIfUnsuccessful()
            .body<List<String>>()
    }

    override suspend fun sendTssMessage(serverUrl: String, messageId: String?, message: Message) {
        httpClient.post(serverUrl) {
            messageId?.let {
                header(MESSAGE_ID_HEADER_TITLE, it)
            }
            setBody(json.encodeToString(message))
        }.throwIfUnsuccessful()
    }

    override suspend fun getTssMessages(
        serverUrl: String,
        sessionId: String,
        localPartyId: String,
        messageId: String?,
    ): List<Message> {
        return httpClient.get("$serverUrl/message/$sessionId/$localPartyId") {
            messageId?.let {
                header(MESSAGE_ID_HEADER_TITLE, it)
            }
        }
            .throwIfUnsuccessful()
            .body<List<Message>>()
    }

    override suspend fun deleteTssMessage(
        serverUrl: String,
        sessionId: String,
        localPartyId: String,
        msgHash: String,
        messageId: String?
    ) {
        httpClient.delete("$serverUrl/message/$sessionId/$localPartyId/$msgHash") {
            messageId?.let {
                header(MESSAGE_ID_HEADER_TITLE, it)
            }
        }.throwIfUnsuccessful()
    }

    override suspend fun markLocalPartyKeysignComplete(
        serverUrl: String,
        messageId: String,
        sig: tss.KeysignResponse
    ) {
        httpClient.post(serverUrl) {
            header(MESSAGE_ID_HEADER_TITLE, messageId)
            setBody(json.encodeToString(sig))
        }.throwIfUnsuccessful()
    }

    override suspend fun checkKeysignComplete(serverUrl: String, messageId: String): tss.KeysignResponse {
        return httpClient.get(serverUrl) {
            header(MESSAGE_ID_HEADER_TITLE, messageId)
        }.throwIfUnsuccessful().body<tss.KeysignResponse>()
    }

    override suspend fun getSetupMessage(serverUrl: String, sessionId: String): String {
        return httpClient.get("$serverUrl/setup-message/$sessionId")
            .throwIfUnsuccessful()
            .body()
    }

    override suspend fun uploadSetupMessage(serverUrl: String, sessionId: String, message: String) {
        httpClient.post("$serverUrl/setup-message/$sessionId") {
            setBody(message)
        }.throwIfUnsuccessful()
    }

    companion object {
        private const val MESSAGE_ID_HEADER_TITLE = "message_id"
    }
}