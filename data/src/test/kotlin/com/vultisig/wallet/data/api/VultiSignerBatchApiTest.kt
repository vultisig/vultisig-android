package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.signer.BatchKeygenRequestJson
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies that [VultiSignerApiImpl.joinBatchKeygen] sends the correct HTTP request to the relay
 * server:
 * - POST method to `/vault/batch/keygen`
 * - JSON body contains all required fields with correct snake_case SerialName mappings
 * - `protocols` field is serialized as a JSON array
 */
class VultiSignerBatchApiTest {

    @Test
    fun `joinBatchKeygen sends POST to correct URL path`() = runTest {
        val captured = captureRequest { api -> api.joinBatchKeygen(testRequest()) }

        assertEquals(HttpMethod.Post, captured.method)
        assertTrue(
            captured.url.encodedPath.endsWith("/vault/batch/keygen"),
            "Expected URL path to end with /vault/batch/keygen but was ${captured.url.encodedPath}",
        )
    }

    @Test
    fun `joinBatchKeygen sends all required fields in JSON body`() = runTest {
        val body = captureRequestBody { api -> api.joinBatchKeygen(testRequest()) }

        val json = Json.parseToJsonElement(body).jsonObject

        assertEquals("test-vault", json["name"]?.jsonPrimitive?.content)
        assertEquals("session-abc", json["session_id"]?.jsonPrimitive?.content)
        assertEquals("hex-enc-key", json["hex_encryption_key"]?.jsonPrimitive?.content)
        assertEquals("hex-chain", json["hex_chain_code"]?.jsonPrimitive?.content)
        assertEquals("local-party", json["local_party_id"]?.jsonPrimitive?.content)
        assertEquals("enc-password", json["encryption_password"]?.jsonPrimitive?.content)
        assertEquals("user@example.com", json["email"]?.jsonPrimitive?.content)
    }

    @Test
    fun `joinBatchKeygen sends protocols as JSON array`() = runTest {
        val body = captureRequestBody { api -> api.joinBatchKeygen(testRequest()) }

        val json = Json.parseToJsonElement(body).jsonObject
        val protocols = json["protocols"]?.jsonArray

        assertTrue(protocols is JsonArray, "protocols must be a JSON array")
        assertEquals(2, protocols?.size)
        assertEquals(JsonPrimitive(BatchKeygenRequestJson.PROTOCOL_ECDSA), protocols?.get(0))
        assertEquals(JsonPrimitive(BatchKeygenRequestJson.PROTOCOL_EDDSA), protocols?.get(1))
    }

    @Test
    fun `joinBatchKeygen uses snake_case field names matching server contract`() = runTest {
        val body = captureRequestBody { api -> api.joinBatchKeygen(testRequest()) }

        val json = Json.parseToJsonElement(body).jsonObject
        val expectedKeys =
            setOf(
                "name",
                "session_id",
                "hex_encryption_key",
                "hex_chain_code",
                "local_party_id",
                "encryption_password",
                "email",
                "lib_type",
                "protocols",
            )

        assertEquals(expectedKeys, json.keys)
    }

    @Test
    fun `joinBatchKeygen sends single protocol when only ecdsa requested`() = runTest {
        val body = captureRequestBody { api ->
            api.joinBatchKeygen(
                testRequest(protocols = listOf(BatchKeygenRequestJson.PROTOCOL_ECDSA))
            )
        }

        val json = Json.parseToJsonElement(body).jsonObject
        val protocols = json["protocols"]?.jsonArray

        assertEquals(1, protocols?.size)
        assertEquals(JsonPrimitive("ecdsa"), protocols?.get(0))
    }

    @Test
    fun `PROTOCOL_ECDSA constant matches server expectation`() {
        assertEquals("ecdsa", BatchKeygenRequestJson.PROTOCOL_ECDSA)
    }

    @Test
    fun `PROTOCOL_EDDSA constant matches server expectation`() {
        assertEquals("eddsa", BatchKeygenRequestJson.PROTOCOL_EDDSA)
    }

    @Test
    fun `joinBatchKeygen throws on non-200 response`() = runTest {
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

        val exception = runCatching { api.joinBatchKeygen(testRequest()) }.exceptionOrNull()

        assertTrue(exception != null, "Expected exception on 500 response")
    }

    @Test
    fun `joinBatchKeygen defaults lib_type to DKLS`() = runTest {
        val body = captureRequestBody { api -> api.joinBatchKeygen(testRequest()) }

        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals(
            1,
            json["lib_type"]?.jsonPrimitive?.content?.toInt(),
            "lib_type must default to 1 (DKLS) so the server stores the correct vault type",
        )
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
        return String(channel.bytes())
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
            listOf(BatchKeygenRequestJson.PROTOCOL_ECDSA, BatchKeygenRequestJson.PROTOCOL_EDDSA)
    ) =
        BatchKeygenRequestJson(
            vaultName = "test-vault",
            sessionId = "session-abc",
            hexEncryptionKey = "hex-enc-key",
            hexChainCode = "hex-chain",
            localPartyId = "local-party",
            encryptionPassword = "enc-password",
            email = "user@example.com",
            libType = 1,
            protocols = protocols,
        )

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
