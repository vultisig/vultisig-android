package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.mediator.Message
import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Characterization tests that pin the SUCCESS-path (HTTP 200) behavior of every method in
 * [SessionApiImpl] that uses `.body<T>()` to deserialize the response.
 *
 * Methods NOT covered here because they are already tested elsewhere:
 * - `markLocalPartyComplete` — covered by [SessionApiTest]
 * - `checkCommittee` (retry semantics) — covered by [SessionApiTest]
 * - `getSetupMessage` (retry loop) — covered by [SessionApiTest] and [SessionApiMessageIdTest]
 *
 * Skipped methods:
 * - `checkKeysignComplete` — uses `.body<tss.KeysignResponse>()`; `tss.KeysignResponse` is a JNI /
 *   native type with no public @Serializable annotation and cannot be constructed from plain JSON.
 */
class SessionApiBodyReadTest {

    private val serverUrl = "http://relay.example"
    private val sessionId = "session-42"
    private val localPartyId = "party-a"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun newApi(body: String): SessionApiImpl =
        SessionApiImpl(
            json = json,
            httpClient = MockHttpClient.respondingWith(HttpStatusCode.OK, body),
        )

    // -------------------------------------------------------------------------
    // checkCommittee — success path (200): returns List<String>
    // -------------------------------------------------------------------------

    @Test
    fun `checkCommittee returns list of party ids on success`() = runBlocking {
        val body = """["party-a","party-b","party-c"]"""
        val result = newApi(body).checkCommittee(serverUrl, sessionId)

        assertEquals(listOf("party-a", "party-b", "party-c"), result)
    }

    @Test
    fun `checkCommittee returns empty list when server returns empty array`() = runBlocking {
        val result = newApi("[]").checkCommittee(serverUrl, sessionId)
        assertEquals(emptyList<String>(), result)
    }

    // -------------------------------------------------------------------------
    // getCompletedParties — success path (200): returns List<String>
    // -------------------------------------------------------------------------

    @Test
    fun `getCompletedParties returns list of completed party ids`() = runBlocking {
        val body = """["party-a","party-b"]"""
        val result = newApi(body).getCompletedParties(serverUrl, sessionId)

        assertEquals(listOf("party-a", "party-b"), result)
    }

    // -------------------------------------------------------------------------
    // getParticipants — success path (200): returns List<String>
    // -------------------------------------------------------------------------

    @Test
    fun `getParticipants returns list of participant ids`() = runBlocking {
        val body = """["alice","bob"]"""
        val result = newApi(body).getParticipants(serverUrl, sessionId)

        assertEquals(listOf("alice", "bob"), result)
    }

    @Test
    fun `getParticipants returns empty list when server returns empty array`() = runBlocking {
        val result = newApi("[]").getParticipants(serverUrl, sessionId)
        assertEquals(emptyList<String>(), result)
    }

    // -------------------------------------------------------------------------
    // getTssMessages — success path (200): returns List<Message>
    // -------------------------------------------------------------------------

    @Test
    fun `getTssMessages returns list of Message objects`() = runBlocking {
        val body =
            """
            [
              {
                "session_id": "session-42",
                "from": "party-a",
                "to": ["party-b"],
                "body": "encrypted-payload",
                "hash": "abc123",
                "sequence_no": 1
              }
            ]
            """
                .trimIndent()
        val result = newApi(body).getTssMessages(serverUrl, sessionId, localPartyId)

        assertEquals(1, result.size)
        val msg = result[0]
        assertEquals("session-42", msg.sessionID)
        assertEquals("party-a", msg.from)
        assertEquals(listOf("party-b"), msg.to)
        assertEquals("encrypted-payload", msg.body)
        assertEquals("abc123", msg.hash)
        assertEquals(1, msg.sequenceNo)
    }

    @Test
    fun `getTssMessages returns empty list when no messages pending`() = runBlocking {
        val result = newApi("[]").getTssMessages(serverUrl, sessionId, localPartyId)
        assertEquals(emptyList<Message>(), result)
    }

    @Test
    fun `getTssMessages returns all messages when response contains multiple`() = runBlocking {
        val body =
            """
            [
              {
                "session_id": "session-42",
                "from": "party-a",
                "to": ["party-b"],
                "body": "payload-1",
                "hash": "hash1",
                "sequence_no": 1
              },
              {
                "session_id": "session-42",
                "from": "party-b",
                "to": ["party-a"],
                "body": "payload-2",
                "hash": "hash2",
                "sequence_no": 2
              }
            ]
            """
                .trimIndent()
        val result = newApi(body).getTssMessages(serverUrl, sessionId, localPartyId)

        assertEquals(2, result.size)
        assertEquals("hash1", result[0].hash)
        assertEquals(1, result[0].sequenceNo)
        assertEquals("hash2", result[1].hash)
        assertEquals(2, result[1].sequenceNo)
    }
}
