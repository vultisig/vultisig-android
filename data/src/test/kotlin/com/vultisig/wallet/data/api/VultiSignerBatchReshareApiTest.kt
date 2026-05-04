package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.signer.BatchReshareRequestJson
import com.vultisig.wallet.data.networkutils.HttpClientConfigurator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies that [VultiSignerApiImpl.joinBatchReshare] sends the correct HTTP request to the relay
 * server:
 * - POST method to `/vault/batch/reshare`
 * - JSON body contains all required fields with correct snake_case SerialName mappings
 * - `protocols` and `old_parties` are serialized as JSON arrays
 * - Throws on non-2xx response so the caller can fall back to the legacy `/reshare` endpoint.
 *
 * Mirrors [VultiSignerBatchApiTest] for the keygen counterpart, ensuring both endpoints share the
 * same coverage shape.
 */
class VultiSignerBatchReshareApiTest {

    @Test
    fun `joinBatchReshare sends POST to correct URL path`() = runTest {
        val captured = captureRequest { api -> api.joinBatchReshare(testRequest()) }

        assertEquals(HttpMethod.Post, captured.method)
        assertTrue(
            captured.url.encodedPath.endsWith("/vault/batch/reshare"),
            "Expected URL path to end with /vault/batch/reshare but was ${captured.url.encodedPath}",
        )
    }

    @Test
    fun `joinBatchReshare sends all required fields in JSON body`() = runTest {
        val body = captureRequestBody { api -> api.joinBatchReshare(testRequest()) }

        val obj = Json.parseToJsonElement(body).jsonObject

        assertEquals("old-pk", obj["public_key"]?.jsonPrimitive?.content)
        assertEquals("session-abc", obj["session_id"]?.jsonPrimitive?.content)
        assertEquals("hex-enc-key", obj["hex_encryption_key"]?.jsonPrimitive?.content)
        assertEquals("local-party", obj["local_party_id"]?.jsonPrimitive?.content)
        assertEquals("enc-password", obj["encryption_password"]?.jsonPrimitive?.content)
        assertEquals("user@example.com", obj["email"]?.jsonPrimitive?.content)
    }

    @Test
    fun `joinBatchReshare sends old_parties as JSON array`() = runTest {
        val body = captureRequestBody { api -> api.joinBatchReshare(testRequest()) }

        val obj = Json.parseToJsonElement(body).jsonObject
        val parties = obj["old_parties"]?.jsonArray

        assertTrue(parties is JsonArray, "old_parties must be a JSON array")
        assertEquals(3, parties?.size)
        assertEquals("alice", parties?.get(0)?.jsonPrimitive?.content)
        assertEquals("bob", parties?.get(1)?.jsonPrimitive?.content)
        assertEquals("server", parties?.get(2)?.jsonPrimitive?.content)
    }

    @Test
    fun `joinBatchReshare sends protocols as JSON array`() = runTest {
        val body = captureRequestBody { api -> api.joinBatchReshare(testRequest()) }

        val obj = Json.parseToJsonElement(body).jsonObject
        val protocols = obj["protocols"]?.jsonArray

        assertTrue(protocols is JsonArray, "protocols must be a JSON array")
        assertEquals(2, protocols?.size)
        assertEquals(JsonPrimitive(BatchReshareRequestJson.PROTOCOL_ECDSA), protocols?.get(0))
        assertEquals(JsonPrimitive(BatchReshareRequestJson.PROTOCOL_EDDSA), protocols?.get(1))
    }

    @Test
    fun `joinBatchReshare uses snake_case field names matching server contract`() = runTest {
        val body = captureRequestBody { api -> api.joinBatchReshare(testRequest()) }

        val obj = Json.parseToJsonElement(body).jsonObject
        val expectedKeys =
            setOf(
                "public_key",
                "session_id",
                "hex_encryption_key",
                "local_party_id",
                "old_parties",
                "encryption_password",
                "email",
                "protocols",
            )

        assertEquals(expectedKeys, obj.keys)
    }

    @Test
    fun `joinBatchReshare body excludes keygen-only fields`() = runTest {
        val body = captureRequestBody { api -> api.joinBatchReshare(testRequest()) }
        val obj = Json.parseToJsonElement(body).jsonObject

        // The server batch reshare handler keys off the existing vault, so these do not belong on
        // the wire and would break iOS / Windows compatibility if added.
        assertFalse("name" in obj.keys)
        assertFalse("hex_chain_code" in obj.keys)
        assertFalse("lib_type" in obj.keys)
        assertFalse("old_reshare_prefix" in obj.keys)
    }

    @Test
    fun `joinBatchReshare sends single protocol when only ecdsa requested`() = runTest {
        val body = captureRequestBody { api ->
            api.joinBatchReshare(
                testRequest(protocols = listOf(BatchReshareRequestJson.PROTOCOL_ECDSA))
            )
        }

        val obj = Json.parseToJsonElement(body).jsonObject
        val protocols = obj["protocols"]?.jsonArray

        assertEquals(1, protocols?.size)
        assertEquals(JsonPrimitive("ecdsa"), protocols?.get(0))
    }

    @Test
    fun `PROTOCOL_ECDSA constant matches server expectation`() {
        assertEquals("ecdsa", BatchReshareRequestJson.PROTOCOL_ECDSA)
    }

    @Test
    fun `PROTOCOL_EDDSA constant matches server expectation`() {
        assertEquals("eddsa", BatchReshareRequestJson.PROTOCOL_EDDSA)
    }

    @Test
    fun `joinBatchReshare throws on non-200 response`() = runTest {
        val client =
            HttpClient(
                MockEngine {
                    respond(
                        content = """{"message":"Internal Server Error"}""",
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            ) {
                HttpClientConfigurator(json).configure(this)
            }
        val api = VultiSignerApiImpl(client)

        val exception = runCatching { api.joinBatchReshare(testRequest()) }.exceptionOrNull()

        assertNotNull(exception, "Expected exception on 500 response so the caller can fall back")
    }

    // -- helpers --

    private suspend fun captureRequest(block: suspend (VultiSignerApi) -> Unit): HttpRequestData {
        var captured: HttpRequestData? = null
        val api = vultiSignerApi { request -> captured = request }

        block(api)

        return requireNotNull(captured) { "No request was captured" }
    }

    private suspend fun captureRequestBody(block: suspend (VultiSignerApi) -> Unit): String {
        val request = captureRequest(block)
        val channel = request.body as io.ktor.http.content.OutgoingContent.ByteArrayContent
        // Decode explicitly as UTF-8 so the test is deterministic across environments.
        return String(channel.bytes(), Charsets.UTF_8)
    }

    private fun vultiSignerApi(onRequest: (HttpRequestData) -> Unit): VultiSignerApi {
        val client =
            HttpClient(
                MockEngine { request ->
                    onRequest(request)
                    respond(
                        content = "",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            ) {
                HttpClientConfigurator(json).configure(this)
            }
        return VultiSignerApiImpl(client)
    }

    private fun testRequest(
        protocols: List<String> =
            listOf(BatchReshareRequestJson.PROTOCOL_ECDSA, BatchReshareRequestJson.PROTOCOL_EDDSA)
    ) =
        BatchReshareRequestJson(
            publicKeyEcdsa = "old-pk",
            sessionId = "session-abc",
            hexEncryptionKey = "hex-enc-key",
            localPartyId = "local-party",
            oldParties = listOf("alice", "bob", "server"),
            encryptionPassword = "enc-password",
            email = "user@example.com",
            protocols = protocols,
        )

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
