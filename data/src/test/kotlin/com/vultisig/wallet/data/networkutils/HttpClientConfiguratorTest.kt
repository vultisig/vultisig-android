package com.vultisig.wallet.data.networkutils

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
import java.util.concurrent.atomic.AtomicInteger
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

    @Test
    fun `retryOnIOException forSafeMethod retriesThreeTimes`() = runTest {
        val callCount = AtomicInteger(0)
        val client =
            HttpClient(
                MockEngine {
                    callCount.incrementAndGet()
                    throw IOException("Connection failed")
                }
            ) {
                configurator.configure(this)
            }
        assertFailsWith<NetworkException> { client.get("http://localhost/test") }
        // 1 original call + 3 retries
        assertEquals(4, callCount.get())
    }

    @Test
    fun `retryOn500 forSafeMethod retriesThreeTimes`() = runTest {
        val callCount = AtomicInteger(0)
        val client =
            HttpClient(
                MockEngine {
                    if (callCount.incrementAndGet() == 4) {
                        respond("ok", HttpStatusCode.OK, jsonHeaders)
                    } else {
                        respond("error", HttpStatusCode.InternalServerError, jsonHeaders)
                    }
                }
            ) {
                configurator.configure(this)
            }
        val response = client.get("http://localhost/test")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(4, callCount.get())
    }

    @Test
    fun `retryOn429 forSafeMethod retriesThreeTimes`() = runTest {
        val callCount = AtomicInteger(0)
        val client =
            HttpClient(
                MockEngine {
                    if (callCount.incrementAndGet() == 4) {
                        respond("ok", HttpStatusCode.OK, jsonHeaders)
                    } else {
                        respond("rate limited", HttpStatusCode.TooManyRequests, jsonHeaders)
                    }
                }
            ) {
                configurator.configure(this)
            }
        val response = client.get("http://localhost/test")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(4, callCount.get())
    }

    @Test
    fun `retryOn408 forSafeMethod retriesThreeTimes`() = runTest {
        val callCount = AtomicInteger(0)
        val client =
            HttpClient(
                MockEngine {
                    if (callCount.incrementAndGet() == 4) {
                        respond("ok", HttpStatusCode.OK, jsonHeaders)
                    } else {
                        respond("timeout", HttpStatusCode.RequestTimeout, jsonHeaders)
                    }
                }
            ) {
                configurator.configure(this)
            }
        val response = client.get("http://localhost/test")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(4, callCount.get())
    }

    @Test
    fun `retryOn500 forPostMethod doesNotRetry`() = runTest {
        val callCount = AtomicInteger(0)
        val client =
            HttpClient(
                MockEngine {
                    callCount.incrementAndGet()
                    respond("error", HttpStatusCode.InternalServerError, jsonHeaders)
                }
            ) {
                configurator.configure(this)
            }
        val response = client.post("http://localhost/test")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(1, callCount.get())
    }
}
