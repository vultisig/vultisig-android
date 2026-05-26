package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Characterization tests for [RouterApiImp.getPayload] success path. Pins the behavior that a 200
 * OK response is returned verbatim via `.throwIfUnsuccessful().body<String>()` (or the equivalent
 * `bodyOrThrow<String>()` after the refactor). The raw response text is asserted as-is because
 * `body<String>()` on a Ktor client with [ContentNegotiation] returns the body text directly.
 *
 * Note: `uploadPayload` and `shouldUploadPayload` contain no `body<…>()` call and are therefore not
 * covered here.
 */
class RouterApiBodyReadTest {

    private fun newApi(status: HttpStatusCode, body: String): RouterApi =
        RouterApiImp(httpClient = MockHttpClient.respondingWith(status, body))

    @Test
    fun `getPayload returns raw response text on success`() = runBlocking {
        val expectedPayload = """{"key":"value","foo":42}"""
        val api = newApi(HttpStatusCode.OK, expectedPayload)

        val result = api.getPayload(serverURL = "https://relay.example.com", hash = "deadbeef01")

        assertEquals(expectedPayload, result)
    }

    @Test
    fun `getPayload returns plain string body unchanged`() = runBlocking {
        val expectedPayload = "plain-text-payload"
        val api = newApi(HttpStatusCode.OK, expectedPayload)

        val result = api.getPayload(serverURL = "https://relay.example.com", hash = "abc123")

        assertEquals(expectedPayload, result)
    }
}
