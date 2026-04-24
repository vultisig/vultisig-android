package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.utils.HttpException
import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/** Unit tests for [SessionApiImpl] retry semantics and relay-error handling. */
class SessionApiTest {

    private val serverUrl = "http://localhost"
    private val sessionId = "test-session"
    private val localPartyId = listOf("party1")
    private val json = Json { ignoreUnknownKeys = true }

    private fun createApi(status: HttpStatusCode): SessionApiImpl =
        SessionApiImpl(
            json = json,
            httpClient = MockHttpClient.respondingWith(status = status, body = "[]"),
        )

    private fun createApiWithSequence(
        vararg responses: Pair<HttpStatusCode, String>
    ): SessionApiImpl =
        SessionApiImpl(json = json, httpClient = MockHttpClient.respondingWithSequence(*responses))

    /** Verifies markLocalPartyComplete succeeds without exception on HTTP 200. */
    @Test
    fun `markLocalPartyComplete succeeds on 200`() = runTest {
        val api = createApi(HttpStatusCode.OK)
        api.markLocalPartyComplete(serverUrl, sessionId, localPartyId)
        // no exception
    }

    /** Verifies markLocalPartyComplete swallows HTTP 500 and logs a warning without throwing. */
    @Test
    fun `markLocalPartyComplete ignores 500 server error`() = runTest {
        val api = createApi(HttpStatusCode.InternalServerError)
        api.markLocalPartyComplete(serverUrl, sessionId, localPartyId)
        // no exception — warning logged
    }

    /** Verifies markLocalPartyComplete throws on HTTP 400 client error. */
    @Test
    fun `markLocalPartyComplete throws on 400 client error`() = runTest {
        val api = createApi(HttpStatusCode.BadRequest)
        assertFailsWith<Exception> {
            api.markLocalPartyComplete(serverUrl, sessionId, localPartyId)
        }
    }

    /**
     * Verifies checkCommittee retries three times with exponential backoff then throws on
     * persistent HTTP 500.
     */
    @Test
    fun `withRelayRetry on500 retriesThreeTimesWithExponentialBackoff`() = runTest {
        val api = createApi(HttpStatusCode.InternalServerError)
        assertFailsWith<HttpException> { api.checkCommittee(serverUrl, sessionId) }
        // attempt 0 → delay(1000), attempt 1 → delay(2000), attempt 2 → throw
        assertEquals(3000L, currentTime)
    }

    /**
     * Verifies checkCommittee succeeds and returns the result on the third attempt after two 500
     * errors.
     */
    @Test
    fun `withRelayRetry onSuccessOnThirdAttempt returnsResult`() = runTest {
        val api =
            createApiWithSequence(
                HttpStatusCode.InternalServerError to "error",
                HttpStatusCode.InternalServerError to "error",
                HttpStatusCode.OK to "[]",
            )
        val result = api.checkCommittee(serverUrl, sessionId)
        assertEquals(emptyList<String>(), result)
        assertEquals(3000L, currentTime)
    }

    /**
     * Verifies checkCommittee rethrows CancellationException immediately without any retry delay.
     */
    @Test
    fun `withRelayRetry onCancellationException rethrowsImmediately noRetry`() = runTest {
        var callCount = 0
        val client =
            HttpClient(
                MockEngine {
                    callCount++
                    throw CancellationException("cancelled")
                }
            ) {}
        val api = SessionApiImpl(json = json, httpClient = client)
        assertFailsWith<CancellationException> { api.checkCommittee(serverUrl, sessionId) }
        assertEquals(1, callCount)
        assertEquals(0L, currentTime)
    }

    /** Verifies checkCommittee rethrows 4xx HttpException immediately without any retry delay. */
    @Test
    fun `withRelayRetry on4xx rethrowsImmediately noRetry`() = runTest {
        val api = createApi(HttpStatusCode.NotFound)
        assertFailsWith<HttpException> { api.checkCommittee(serverUrl, sessionId) }
        assertEquals(0L, currentTime)
    }

    /**
     * Verifies getSetupMessage retries 10 times then throws, accumulating 9000 ms of backoff delay.
     */
    @Test
    fun `getSetupMessage repeats10Times thenThrows`() = runTest {
        val api = createApi(HttpStatusCode.InternalServerError)
        assertFailsWith<Exception> { api.getSetupMessage(serverUrl, sessionId, null) }
        // 9 delays of 1000 ms between 10 attempts
        assertEquals(9000L, currentTime)
    }
}
