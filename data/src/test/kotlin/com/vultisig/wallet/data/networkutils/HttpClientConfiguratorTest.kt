package com.vultisig.wallet.data.networkutils

import com.vultisig.wallet.data.utils.NetworkErrorKind
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/** Pins the retry policy installed by [HttpClientConfigurator]. */
class HttpClientConfiguratorTest {

    private val configurator = HttpClientConfigurator(Json { ignoreUnknownKeys = true })
    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun configuredClient(engine: MockEngine): HttpClient =
        HttpClient(engine) { configurator.configure(this) }

    /**
     * Verifies a safe GET request is retried three times on IOException before throwing
     * NetworkException.
     */
    @Test
    fun `retryOnIOException forSafeMethod retriesThreeTimes`() = runTest {
        var callCount = 0
        configuredClient(
                MockEngine {
                    callCount++
                    throw IOException("Connection failed")
                }
            )
            .use { client ->
                assertFailsWith<NetworkException> { client.get("http://localhost/test") }
                // 1 original call + 3 retries
                assertEquals(4, callCount)
            }
    }

    /**
     * Verifies a safe GET request is retried three times on HTTP 500, succeeding on the fourth
     * call.
     */
    @Test
    fun `retryOn500 forSafeMethod retriesThreeTimes`() = runTest {
        var callCount = 0
        configuredClient(
                MockEngine {
                    if (++callCount == 4) {
                        respond("ok", HttpStatusCode.OK, jsonHeaders)
                    } else {
                        respond("error", HttpStatusCode.InternalServerError, jsonHeaders)
                    }
                }
            )
            .use { client ->
                val response = client.get("http://localhost/test")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(4, callCount)
            }
    }

    /**
     * Verifies a safe GET request is retried three times on HTTP 429, succeeding on the fourth
     * call.
     */
    @Test
    fun `retryOn429 forSafeMethod retriesThreeTimes`() = runTest {
        var callCount = 0
        configuredClient(
                MockEngine {
                    if (++callCount == 4) {
                        respond("ok", HttpStatusCode.OK, jsonHeaders)
                    } else {
                        respond("rate limited", HttpStatusCode.TooManyRequests, jsonHeaders)
                    }
                }
            )
            .use { client ->
                val response = client.get("http://localhost/test")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(4, callCount)
            }
    }

    /**
     * Verifies a safe GET request is retried three times on HTTP 408, succeeding on the fourth
     * call.
     */
    @Test
    fun `retryOn408 forSafeMethod retriesThreeTimes`() = runTest {
        var callCount = 0
        configuredClient(
                MockEngine {
                    if (++callCount == 4) {
                        respond("ok", HttpStatusCode.OK, jsonHeaders)
                    } else {
                        respond("timeout", HttpStatusCode.RequestTimeout, jsonHeaders)
                    }
                }
            )
            .use { client ->
                val response = client.get("http://localhost/test")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(4, callCount)
            }
    }

    /**
     * Verifies an unsafe POST request is NOT retried on HTTP 500 and returns the error response
     * immediately.
     */
    @Test
    fun `retryOn500 forPostMethod doesNotRetry`() = runTest {
        var callCount = 0
        configuredClient(
                MockEngine {
                    callCount++
                    respond("error", HttpStatusCode.InternalServerError, jsonHeaders)
                }
            )
            .use { client ->
                val response = client.post("http://localhost/test")
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertEquals(1, callCount)
            }
    }

    /**
     * Verifies a non-idempotent POST request is NOT resent on IOException. A retry could duplicate
     * a side effect (e.g. a transaction broadcast the node already received), so transport-level
     * retries are restricted to idempotent methods. The IOException surfaces as a NetworkException
     * on the very first failure via HttpCallValidator.
     */
    @Test
    fun `retryOnIOException forPostMethod doesNotRetry`() = runTest {
        var callCount = 0
        configuredClient(
                MockEngine {
                    callCount++
                    throw IOException("Connection failed")
                }
            )
            .use { client ->
                assertFailsWith<NetworkException> { client.post("http://localhost/test") }
                assertEquals(1, callCount)
            }
    }

    /**
     * Verifies a non-idempotent POST request is NOT resent on a read-timeout
     * ([SocketTimeoutException], an IOException subclass). This is the core idempotency guard: a
     * broadcast the node accepted but slow-ACKed must not be auto-rebroadcast. The failure surfaces
     * as a NetworkException on the first attempt.
     */
    @Test
    fun `retryOnSocketTimeout forPostMethod doesNotRetry`() = runTest {
        var callCount = 0
        configuredClient(
                MockEngine {
                    callCount++
                    throw SocketTimeoutException("Read timed out")
                }
            )
            .use { client ->
                assertFailsWith<NetworkException> { client.post("http://localhost/test") }
                assertEquals(1, callCount)
            }
    }

    /**
     * Verifies a safe GET request IS still retried three times on a read-timeout
     * ([SocketTimeoutException]) before throwing NetworkException — idempotent-method transport
     * retries are unchanged.
     */
    @Test
    fun `retryOnSocketTimeout forSafeMethod retriesThreeTimes`() = runTest {
        var callCount = 0
        configuredClient(
                MockEngine {
                    callCount++
                    throw SocketTimeoutException("Read timed out")
                }
            )
            .use { client ->
                assertFailsWith<NetworkException> { client.get("http://localhost/test") }
                // 1 original call + 3 retries
                assertEquals(4, callCount)
            }
    }

    /**
     * A read/socket timeout must surface as [NetworkErrorKind.Timeout] with a timeout message, not
     * as a generic "No internet connection" — collapsing distinct transport failures into one
     * message misled both users and log triage (issue #4956).
     */
    @Test
    fun `socketTimeout isClassifiedAsTimeout`() = runTest {
        configuredClient(MockEngine { throw SocketTimeoutException("Read timed out") }).use { client
            ->
            val ex = assertFailsWith<NetworkException> { client.post("http://localhost/test") }
            assertEquals(NetworkErrorKind.Timeout, ex.kind)
            assertEquals("Connection timed out", ex.message)
            assertEquals(0, ex.httpStatusCode)
        }
    }

    /** A DNS failure (host unreachable) is classified as a connectivity loss. */
    @Test
    fun `unknownHost isClassifiedAsNoConnectivity`() = runTest {
        configuredClient(MockEngine { throw UnknownHostException("no host") }).use { client ->
            val ex = assertFailsWith<NetworkException> { client.post("http://localhost/test") }
            assertEquals(NetworkErrorKind.NoConnectivity, ex.kind)
            assertEquals("No internet connection", ex.message)
        }
    }

    /** Any other transport-level IOException maps to the generic [NetworkErrorKind.Transport]. */
    @Test
    fun `genericIOException isClassifiedAsTransport`() = runTest {
        configuredClient(MockEngine { throw IOException("connection refused") }).use { client ->
            val ex = assertFailsWith<NetworkException> { client.post("http://localhost/test") }
            assertEquals(NetworkErrorKind.Transport, ex.kind)
        }
    }
}
