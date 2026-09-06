package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.utils.HttpException
import com.vultisig.wallet.data.api.utils.throwIfUnsuccessful
import com.vultisig.wallet.data.mediator.Message
import com.vultisig.wallet.data.utils.NetworkErrorKind
import com.vultisig.wallet.data.utils.NetworkException
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
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

    /**
     * Posts one TSS message to the relay.
     *
     * @param budget bounded retry budget with a short per-request timeout, for callers that await
     *   the send and need it to fail inside the ceremony stall limit. `null` keeps the shared relay
     *   retry and the client's default timeouts, which is what the legacy GG20
     *   [com.vultisig.wallet.data.tss.TssMessenger.send] path still wants.
     */
    suspend fun sendTssMessage(
        serverUrl: String,
        messageId: String?,
        message: Message,
        budget: RelaySendBudget? = null,
    )

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
            httpClient
                .get("$serverUrl/start/$sessionId")
                .throwIfUnsuccessful()
                .bodyOrThrow<List<String>>()
        }
    }

    override suspend fun startSession(
        serverUrl: String,
        sessionId: String,
        localPartyId: List<String>,
    ) {
        withRelayRetry {
            val response = httpClient.post("$serverUrl/$sessionId") { setBody(localPartyId) }
            if (response.status.value >= 500) {
                val alreadyRegistered =
                    runCatching { getParticipants(serverUrl, sessionId).containsAll(localPartyId) }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrDefault(false)
                if (alreadyRegistered) return@withRelayRetry
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
        withRelayRetry {
            val response =
                httpClient.post("$serverUrl/complete/$sessionId") { setBody(localPartyId) }
            if (response.status.value >= 500) {
                Timber.w(
                    "markLocalPartyComplete: server returned ${response.status.value}, ignoring"
                )
            } else {
                response.throwIfUnsuccessful()
            }
        }
    }

    override suspend fun getCompletedParties(serverUrl: String, sessionId: String): List<String> {
        return httpClient
            .get("$serverUrl/complete/$sessionId")
            .throwIfUnsuccessful()
            .bodyOrThrow<List<String>>()
    }

    override suspend fun getParticipants(serverUrl: String, sessionId: String): List<String> {
        return httpClient
            .get("$serverUrl/$sessionId")
            .throwIfUnsuccessful()
            .bodyOrThrow<List<String>>()
    }

    override suspend fun sendTssMessage(
        serverUrl: String,
        messageId: String?,
        message: Message,
        budget: RelaySendBudget?,
    ) {
        withRelayBudget(budget) { timeoutMillis ->
            httpClient
                .post(serverUrl) {
                    if (!messageId.isNullOrEmpty()) {
                        header(MESSAGE_ID_HEADER_TITLE, messageId)
                    }
                    if (timeoutMillis != null) {
                        timeout { requestTimeoutMillis = timeoutMillis }
                    }
                    setBody(json.encodeToString(message))
                }
                .throwIfUnsuccessful()
        }
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
                .bodyOrThrow<List<Message>>()
        }

    override suspend fun deleteTssMessage(
        serverUrl: String,
        sessionId: String,
        localPartyId: String,
        msgHash: String,
        messageId: String?,
    ) {
        withRelayRetry {
            httpClient
                .delete("$serverUrl/message/$sessionId/$localPartyId/$msgHash") {
                    messageId?.let { header(MESSAGE_ID_HEADER_TITLE, it) }
                }
                .throwIfUnsuccessful()
        }
    }

    override suspend fun markLocalPartyKeysignComplete(
        serverUrl: String,
        messageId: String,
        sig: tss.KeysignResponse,
    ) {
        withRelayRetry {
            httpClient
                .post(serverUrl) {
                    header(MESSAGE_ID_HEADER_TITLE, messageId)
                    setBody(json.encodeToString(sig))
                }
                .throwIfUnsuccessful()
        }
    }

    override suspend fun checkKeysignComplete(
        serverUrl: String,
        messageId: String,
    ): tss.KeysignResponse {
        return httpClient
            .get(serverUrl) { header(MESSAGE_ID_HEADER_TITLE, messageId) }
            .throwIfUnsuccessful()
            .bodyOrThrow<tss.KeysignResponse>()
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
        withRelayRetry {
            httpClient
                .post("$serverUrl/setup-message/$sessionId") {
                    if (!messageId.isNullOrEmpty()) {
                        header(MESSAGE_ID_HEADER_TITLE, messageId)
                    }
                    setBody(message)
                }
                .throwIfUnsuccessful()
        }
    }

    private suspend fun <T> withRelayRetry(block: suspend () -> T): T =
        withRelayBudget(budget = null) { block() }

    /**
     * Runs [block] under a bounded relay retry, retrying only 5xx and transport faults and throwing
     * a 4xx or a cancellation at once.
     *
     * With no [budget] this is the shared relay policy every endpoint has used since #4667: three
     * attempts, 1 s then 2 s of backoff, and the client's own timeouts. A [budget] tightens all
     * three for one call — the awaited TSS send needs its whole retry sequence to finish inside the
     * ceremony stall, so it caps each request and abandons the send rather than sleeping a backoff
     * that would overshoot the deadline.
     *
     * [block] receives the millisecond cap for its request, or `null` when there is none.
     */
    private suspend fun <T> withRelayBudget(
        budget: RelaySendBudget?,
        block: suspend (requestTimeoutMillis: Long?) -> T,
    ): T {
        val maxAttempts = budget?.maxAttempts ?: RELAY_MAX_RETRIES
        var lastException: Exception? = null
        repeat(maxAttempts) { attempt ->
            val requestTimeoutMillis =
                if (budget == null) null
                else
                    budget.requestTimeoutMillisAt(System.nanoTime())
                        ?: throw RelaySendDeadlineExceededException(lastException)
            try {
                return block(requestTimeoutMillis)
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
            } catch (e: NetworkException) {
                if (e.kind != NetworkErrorKind.Transport && e.kind != NetworkErrorKind.Timeout) {
                    throw e
                }
                lastException = e
                Timber.w(
                    e,
                    "Relay request transport failure (${e.kind}), retrying (attempt ${attempt + 1})",
                )
            }
            if (attempt < maxAttempts - 1) {
                val backoff =
                    budget?.backoffAfter(attempt)?.inWholeMilliseconds
                        ?: (RELAY_BACKOFF_MS * (1L shl attempt))
                if (
                    budget != null && !budget.backoffFitsAt(backoff.milliseconds, System.nanoTime())
                ) {
                    throw RelaySendDeadlineExceededException(lastException)
                }
                delay(backoff)
            }
        }
        throw lastException
            ?: IllegalStateException("Relay request failed after $maxAttempts retries")
    }

    companion object {
        private const val MESSAGE_ID_HEADER_TITLE = "message_id"
        private const val MAX_RETRIES = 10
        private const val RELAY_MAX_RETRIES = 3
        private const val RELAY_BACKOFF_MS = 1000L
    }
}
