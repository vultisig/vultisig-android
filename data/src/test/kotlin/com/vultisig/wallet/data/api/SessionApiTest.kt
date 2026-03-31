package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class SessionApiTest {

    private val serverUrl = "http://localhost"
    private val sessionId = "test-session"
    private val localPartyId = listOf("party1")

    private fun createApi(status: HttpStatusCode): SessionApiImpl =
        SessionApiImpl(
            json = Json { ignoreUnknownKeys = true },
            httpClient = MockHttpClient.respondingWith(status = status, body = "[]"),
        )

    @Test
    fun `markLocalPartyComplete succeeds on 200`() = runTest {
        val api = createApi(HttpStatusCode.OK)
        api.markLocalPartyComplete(serverUrl, sessionId, localPartyId)
        // no exception
    }

    @Test
    fun `markLocalPartyComplete ignores 500 server error`() = runTest {
        val api = createApi(HttpStatusCode.InternalServerError)
        api.markLocalPartyComplete(serverUrl, sessionId, localPartyId)
        // no exception — warning logged
    }

    @Test
    fun `markLocalPartyComplete throws on 400 client error`() = runTest {
        val api = createApi(HttpStatusCode.BadRequest)
        assertFailsWith<Exception> {
            api.markLocalPartyComplete(serverUrl, sessionId, localPartyId)
        }
    }
}
