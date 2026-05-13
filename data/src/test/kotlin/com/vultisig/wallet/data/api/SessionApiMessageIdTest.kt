package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.mediator.Message
import com.vultisig.wallet.data.networkutils.HttpClientConfigurator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Verifies that [SessionApiImpl] correctly passes the `message_id` header to the relay server on
 * relay-message and setup-message operations.
 *
 * This is critical for parallel keygen: each ceremony (ECDSA, EdDSA, MLDSA) isolates its exchange
 * and setup traffic with `message_id` values so concurrent ceremonies don't interfere with each
 * other within the same session.
 */
class SessionApiMessageIdTest {

    @Test
    fun `sendTssMessage includes message_id header when set`() = runTest {
        assertMessageIdHeader("ecdsa") { api ->
            api.sendTssMessage(SERVER_URL, messageId = "ecdsa", message = testMessage())
        }
    }

    @Test
    fun `sendTssMessage omits message_id header when null`() = runTest {
        assertNoMessageIdHeader { api ->
            api.sendTssMessage(SERVER_URL, messageId = null, message = testMessage())
        }
    }

    @Test
    fun `getTssMessages includes message_id header when set`() = runTest {
        assertMessageIdHeader("eddsa") { api ->
            api.getTssMessages(SERVER_URL, SESSION_ID, PARTY_ID, messageId = "eddsa")
        }
    }

    @Test
    fun `getTssMessages omits message_id header when null`() = runTest {
        assertNoMessageIdHeader { api ->
            api.getTssMessages(SERVER_URL, SESSION_ID, PARTY_ID, messageId = null)
        }
    }

    @Test
    fun `deleteTssMessage includes message_id header when set`() = runTest {
        assertMessageIdHeader("mldsa") { api ->
            api.deleteTssMessage(SERVER_URL, SESSION_ID, PARTY_ID, "hash123", messageId = "mldsa")
        }
    }

    @Test
    fun `deleteTssMessage omits message_id header when null`() = runTest {
        assertNoMessageIdHeader { api ->
            api.deleteTssMessage(SERVER_URL, SESSION_ID, PARTY_ID, "hash123", messageId = null)
        }
    }

    @Test
    fun `uploadSetupMessage includes message_id header when set`() = runTest {
        assertMessageIdHeader("ecdsa") { api ->
            api.uploadSetupMessage(SERVER_URL, SESSION_ID, message = "setup", messageId = "ecdsa")
        }
    }

    @Test
    fun `uploadSetupMessage omits message_id header when null`() = runTest {
        assertNoMessageIdHeader { api ->
            api.uploadSetupMessage(SERVER_URL, SESSION_ID, message = "setup", messageId = null)
        }
    }

    @Test
    fun `getSetupMessage includes message_id header when set`() = runTest {
        assertMessageIdHeader("eddsa") { api ->
            api.getSetupMessage(SERVER_URL, SESSION_ID, messageId = "eddsa")
        }
    }

    @Test
    fun `getSetupMessage omits message_id header when null`() = runTest {
        assertNoMessageIdHeader { api ->
            api.getSetupMessage(SERVER_URL, SESSION_ID, messageId = null)
        }
    }

    // -- helpers ---

    private suspend fun assertMessageIdHeader(
        expected: String,
        block: suspend (SessionApi) -> Unit,
    ) {
        var capturedHeader: String? = "NOT_CALLED"
        val api = sessionApi { request -> capturedHeader = request.headers["message_id"] }

        block(api)

        assertEquals(expected, capturedHeader)
    }

    private suspend fun assertNoMessageIdHeader(block: suspend (SessionApi) -> Unit) {
        var capturedHeader: String? = "NOT_CALLED"
        val api = sessionApi { request -> capturedHeader = request.headers["message_id"] }

        block(api)

        assertNull(capturedHeader)
    }

    private fun sessionApi(onRequest: (HttpRequestData) -> Unit): SessionApi {
        val client =
            HttpClient(
                MockEngine { request ->
                    onRequest(request)
                    respond(
                        content = "[]",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            ) {
                HttpClientConfigurator(json).configure(this)
            }
        return SessionApiImpl(json, client)
    }

    private fun testMessage() =
        Message(
            sessionID = SESSION_ID,
            from = PARTY_ID,
            to = listOf("party-b"),
            body = "encrypted-body",
            hash = "abc123",
            sequenceNo = 1,
        )

    private companion object {
        const val SERVER_URL = "https://relay.example"
        const val SESSION_ID = "session-1"
        const val PARTY_ID = "party-a"

        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
