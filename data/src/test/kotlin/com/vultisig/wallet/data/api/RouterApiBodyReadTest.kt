package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.utils.HttpException
import com.vultisig.wallet.data.common.sha256
import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RouterApiBodyReadTest {

    @Test
    fun `relay returns the exact verified UTF-8 body`() = runTest {
        val body = """{"description":"synthetic café","amount":42}""" + "\n"
        val id = body.sha256()
        val capture = MockHttpClient.RequestCapture()
        MockHttpClient.capturingRequest(HttpStatusCode.OK, body, capture).use { client ->
            assertEquals(body, RouterApiImp(client).getPayload(RELAY_URL, id))
            assertEquals("/router/payload/$id", capture.lastPath)
        }
    }

    @Test
    fun `local mediator returns a verified plain text payload`() = runTest {
        val body = "synthetic local payload"
        val id = body.sha256()
        val capture = MockHttpClient.RequestCapture()
        MockHttpClient.capturingRequest(HttpStatusCode.OK, body, capture).use { client ->
            assertEquals(body, RouterApiImp(client).getPayload(LOCAL_URL, id))
            assertEquals("/payload/$id", capture.lastPath)
        }
    }

    @Test
    fun `a body with a different digest fails on either transport`() = runTest {
        MockHttpClient.respondingWith(HttpStatusCode.OK, "synthetic returned payload").use { client ->
            val api = RouterApiImp(client)
            val id = "synthetic requested payload".sha256()
            for (server in listOf(RELAY_URL, LOCAL_URL)) {
                val error = assertFailsWith<IllegalArgumentException> { api.getPayload(server, id) }
                assertEquals("Signing payload does not match its ID", error.message)
            }
        }
    }

    @Test
    fun `an invalid payload ID fails before any request`() = runTest {
        val callCount = AtomicInteger()
        MockHttpClient.throwing(AssertionError("Unexpected network request")) {
                callCount.incrementAndGet()
            }
            .use { client ->
                assertFailsWith<IllegalArgumentException> {
                    RouterApiImp(client).getPayload(RELAY_URL, "synthetic-id")
                }
                assertEquals(0, callCount.get())
            }
    }

    @Test
    fun `HTTP failures preserve their status`() = runTest {
        MockHttpClient.respondingWith(HttpStatusCode.NotFound, "").use { client ->
            val error =
                assertFailsWith<HttpException> {
                    RouterApiImp(client).getPayload(RELAY_URL, "synthetic payload".sha256())
                }
            assertEquals(HttpStatusCode.NotFound.value, error.statusCode)
        }
    }

    @Test
    fun `oversized bodies fail even without a content length`() = runTest {
        val body = "a".repeat(1024 * 1024 + 1)
        val id = body.sha256()
        HttpClient(MockEngine { respond(body) }).use { client ->
            val error =
                assertFailsWith<IllegalArgumentException> {
                    RouterApiImp(client).getPayload(RELAY_URL, id)
                }
            assertEquals("Invalid signing payload size", error.message)
        }
    }

    @Test
    fun `oversized content length fails before accepting a matching body`() = runTest {
        val body = "synthetic payload"
        val id = body.sha256()
        val headers = headersOf(HttpHeaders.ContentLength, (1024 * 1024 + 1).toString())
        HttpClient(MockEngine { respond(body, headers = headers) }).use { client ->
            val error =
                assertFailsWith<IllegalArgumentException> {
                    RouterApiImp(client).getPayload(RELAY_URL, id)
                }
            assertEquals("Invalid signing payload size", error.message)
        }
    }

    private companion object {
        const val RELAY_URL = "https://relay.example.com/router"
        const val LOCAL_URL = "http://127.0.0.1:18080"
    }
}
