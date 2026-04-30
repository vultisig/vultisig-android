package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.utils.HttpException
import com.vultisig.wallet.data.api.utils.throwIfUnsuccessful
import com.vultisig.wallet.data.mediator.Message
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber

interface SessionApi {
    suspend fun checkCommittee(serverUrl: String, sessionId: String): List<String>

    suspend fun startSession(serverUrl: String, sessionId: String, localPartyId: List<String>)

    suspend fun startWithCommittee(serverUrl: String, sessionId: String, committee: List<String>)

    suspend fun markLocalPartyComplete(
        serverUrl: String,
        sessionId: String,
        localPartyId: List<String>,
    )

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

    suspend fun markLocalPartyKeysignComplete(
        serverUrl: String,
        messageId: String,
        sig: tss.KeysignResponse,
    )

    suspend fun checkKeysignComplete(serverUrl: String, messageId: String): tss.KeysignResponse

    suspend fun getSetupMessage(serverUrl: String, sessionId: String, messageId: String?): String

    suspend fun uploadSetupMessage(
        serverUrl: String,
        sessionId: String,
        message: String,
        messageId: String?,
    )
}

internal class SessionApiImpl
@Inject
constructor(private val json: Json, private val httpClient: HttpClient) : SessionApi {
    override suspend fun checkCommittee(serverUrl: String, sessionId: String): List<String> {
        return withRelayRetry {
            httpClient.get("$serverUrl/start/$sessionId").throwIfUnsuccessful().body<List<String>>()
        }
    }

    override suspend fun startSession(
        serverUrl: String,
        sessionId: String,
        localPartyId: List<String>,
    ) {
        withRelayRetry {
            val response = httpClient.post("$serverUrl/$sessionId") { setBody(localPartyId) }
            if (response.status.value == 409 || response.status.value >= 500) {
                Timber.w("startSession: server returned %d, ignoring", response.status.value)
                return@withRelayRetry
            }
            response.throwIfUnsuccessful()
        }
    }

    override suspend fun startWithCommittee(
        serverUrl: String,
        sessionId: String,
        committee: List<String>,
    ) {
        withRelayRetry {
            httpClient
                .post("$serverUrl/start/$sessionId") { setBody(committee) }
                .throwIfUnsuccessful()
        }
    }

    override suspend fun markLocalPartyComplete(
        serverUrl: String,
        sessionId: String,
        localPartyId: List<String>,
    ) {
        val response = httpClient.post("$serverUrl/complete/$sessionId") { setBody(localPartyId) }
        if (response.status.value >= 500) {
            Timber.w("markLocalPartyComplete: server returned ${response.status.value}, ignoring")
            return
        }
        response.throwIfUnsuccessful()
    }

    override suspend fun getCompletedParties(serverUrl: String, sessionId: String): List<String> {
        return httpClient
            .get("$serverUrl/complete/$sessionId")
            .throwIfUnsuccessful()
            .body<List<String>>()
    }

    override suspend fun getParticipants(serverUrl: String, sessionId: String): List<String> {
        return httpClient.get("$serverUrl/$sessionId").throwIfUnsuccessful().body<List<String>>()
    }

    override suspend fun sendTssMessage(serverUrl: String, messageId: String?, message: Message) {
        httpClient
            .post(serverUrl) {
                if (!messageId.isNullOrEmpty()) {
                    header(MESSAGE_ID_HEADER_TITLE, messageId)
                }
                setBody(json.encodeToString(message))
            }
            .throwIfUnsuccessful()
    }

    override suspend fun getTssMessages(
        serverUrl: String,
        sessionId: String,
        localPartyId: String,
        messageId: String?,
    ): List<Message> =
        withContext(Dispatchers.IO) {
            httpClient
                .get("$serverUrl/message/$sessionId/$localPartyId") {
                    messageId?.let { header(MESSAGE_ID_HEADER_TITLE, it) }
                }
                .throwIfUnsuccessful()
                .body<List<Message>>()
        }

    override suspend fun deleteTssMessage(
        serverUrl: String,
        sessionId: String,
        localPartyId: String,
        msgHash: String,
        messageId: String?,
    ) {
        httpClient
            .delete("$serverUrl/message/$sessionId/$localPartyId/$msgHash") {
                messageId?.let { header(MESSAGE_ID_HEADER_TITLE, it) }
            }
            .throwIfUnsuccessful()
    }

    override suspend fun markLocalPartyKeysignComplete(
        serverUrl: String,
        messageId: String,
        sig: tss.KeysignResponse,
    ) {
        httpClient
            .post(serverUrl) {
                header(MESSAGE_ID_HEADER_TITLE, messageId)
                setBody(json.encodeToString(sig))
            }
            .throwIfUnsuccessful()
    }

    override suspend fun checkKeysignComplete(
        serverUrl: String,
        messageId: String,
    ): tss.KeysignResponse {
        return httpClient
            .get(serverUrl) { header(MESSAGE_ID_HEADER_TITLE, messageId) }
            .throwIfUnsuccessful()
            .body<tss.KeysignResponse>()
    }

    override suspend fun getSetupMessage(
        serverUrl: String,
        sessionId: String,
        messageId: String?,
    ): String {
        var lastException: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                val response =
                    httpClient.get("$serverUrl/setup-message/$sessionId") {
                        if (!messageId.isNullOrEmpty()) {
                            header(MESSAGE_ID_HEADER_TITLE, messageId)
                        }
                    }
                if (response.status.isSuccess()) {
                    return response.body()
                } else {
                    lastException =
                        Exception("HTTP ${response.status.value}: ${response.status.description}")
                }
            } catch (e: CancellationException) {
                Timber.e("Retry setup-message cancelled exceptions")
                throw e
            } catch (e: Exception) {
                Timber.e("Retry setup-message request failed")
                lastException = e
            }
            if (attempt < MAX_RETRIES - 1) {
                Timber.e("Retry setup-message request attempt: ${attempt + 1}")
                delay(1000L)
            }
        }
        throw lastException ?: Exception("Failed to get setup message after $MAX_RETRIES retries")
    }

    override suspend fun uploadSetupMessage(
        serverUrl: String,
        sessionId: String,
        message: String,
        messageId: String?,
    ) {
        httpClient
            .post("$serverUrl/setup-message/$sessionId") {
                if (!messageId.isNullOrEmpty()) {
                    header(MESSAGE_ID_HEADER_TITLE, messageId)
                }
                setBody(message)
            }
            .throwIfUnsuccessful()
    }

    private suspend fun <T> withRelayRetry(block: suspend () -> T): T {
        var lastException: Exception? = null
        repeat(RELAY_MAX_RETRIES) { attempt ->
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.statusCode < 500) throw e
                lastException = e
                Timber.w(
                    "Relay request failed with ${e.statusCode}, retrying (attempt ${attempt + 1})"
                )
            } catch (e: IOException) {
                lastException = e
                Timber.w(e, "Relay request IOException, retrying (attempt ${attempt + 1})")
            }
            if (attempt < RELAY_MAX_RETRIES - 1) {
                delay(RELAY_BACKOFF_MS * (1L shl attempt))
            }
        }
        throw lastException
            ?: IllegalStateException("Relay request failed after $RELAY_MAX_RETRIES retries")
    }

    companion object {
        private const val MESSAGE_ID_HEADER_TITLE = "message_id"
        private const val MAX_RETRIES = 10
        private const val RELAY_MAX_RETRIES = 3
        private const val RELAY_BACKOFF_MS = 1000L
    }
}
