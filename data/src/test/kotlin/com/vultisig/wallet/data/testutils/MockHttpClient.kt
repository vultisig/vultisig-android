package com.vultisig.wallet.data.testutils

import com.vultisig.wallet.data.utils.NetworkErrorKind
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.json.Json

/**
 * Test utilities for building mock [HttpClient] instances that mirror the production
 * [HttpClientConfigurator][com.vultisig.wallet.data.networkutils.HttpClientConfigurator] setup.
 *
 * These builders install [ContentNegotiation] and [HttpCallValidator] with the same `IOException →
 * NetworkException(httpStatusCode=0)` mapping used in production, ensuring tests validate real
 * behavior.
 */
object MockHttpClient {

    /** Pre-built JSON content-type headers for use in mock engine responses. */
    val JSON_HEADERS = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * Builds a client where the transport throws the given [IOException]. Mirrors the production
     * setup: `IOException → NetworkException(httpStatusCode=0)`.
     */
    fun throwingIOException(exception: IOException): HttpClient =
        HttpClient(MockEngine { throw exception }) { installDefaults() }

    /**
     * Builds a production-shaped client whose transport throws the given [exception]. Each request
     * invokes [onCall] before throwing — pass an `AtomicInteger.incrementAndGet()` lambda when you
     * need a call counter. Used for non-IOException transport failures (e.g.
     * [kotlin.coroutines.cancellation.CancellationException]) where the standard plugins must still
     * be installed so the client matches the shape used in production.
     */
    fun throwing(exception: Throwable, onCall: () -> Unit = {}): HttpClient =
        HttpClient(
            MockEngine {
                onCall()
                throw exception
            }
        ) {
            installDefaults()
        }

    /**
     * Builds a client that returns a server response with the given [status] and [body]. The
     * [HttpCallValidator] is still installed but won't fire (no transport error).
     *
     * Pass a custom [jsonFormat] when the response (or request) model contains `@Contextual` fields
     * — e.g. `@Contextual BigInteger` — so [ContentNegotiation] can (de)serialize them. Build it
     * with the matching contextual serializer, e.g. `Json { serializersModule = SerializersModule {
     * contextual(BigIntegerSerializerImpl()) } }`.
     */
    fun respondingWith(status: HttpStatusCode, body: String, jsonFormat: Json = json): HttpClient =
        HttpClient(
            MockEngine { respond(content = body, status = status, headers = JSON_HEADERS) }
        ) {
            installDefaults(jsonFormat)
        }

    /** Mutable holder for the last outgoing request body captured by [capturingRequest]. */
    class RequestCapture {
        var lastBody: String = ""
    }

    /**
     * Like [respondingWith], but records each outgoing request body into [capture] so a test can
     * assert what was sent (e.g. RPC params). The MockEngine block runs sequentially within a
     * single coroutine, so a plain field is sufficient.
     */
    fun capturingRequest(
        status: HttpStatusCode,
        body: String,
        capture: RequestCapture,
        jsonFormat: Json = json,
    ): HttpClient =
        HttpClient(
            MockEngine { request ->
                capture.lastBody = request.body.toByteArray().decodeToString()
                respond(content = body, status = status, headers = JSON_HEADERS)
            }
        ) {
            installDefaults(jsonFormat)
        }

    /**
     * Builds a client that steps through [responses] in order, pinning the last entry once the
     * sequence is exhausted. Each entry is a [Pair] of (status, body). The MockEngine block runs
     * sequentially within a single coroutine, so a plain mutable [Int] is sufficient.
     */
    fun respondingWithSequence(vararg responses: Pair<HttpStatusCode, String>): HttpClient {
        require(responses.isNotEmpty()) { "respondingWithSequence requires at least one response" }
        var index = 0
        return HttpClient(
            MockEngine {
                val i = minOf(index++, responses.size - 1)
                val (status, body) = responses[i]
                respond(content = body, status = status, headers = JSON_HEADERS)
            }
        ) {
            installDefaults()
        }
    }

    /**
     * Installs the standard plugins matching production
     * [com.vultisig.wallet.data.networkutils.HttpClientConfigurator].
     */
    private fun io.ktor.client.HttpClientConfig<*>.installDefaults(jsonFormat: Json = json) {
        install(ContentNegotiation) { json(jsonFormat, ContentType.Any) }
        install(DefaultRequest) {
            headers.appendIfNameAbsent(
                HttpHeaders.ContentType,
                ContentType.Application.Json.toString(),
            )
        }
        install(HttpCallValidator) {
            handleResponseExceptionWithRequest { cause, _ ->
                if (cause is IOException) {
                    throw cause.toNetworkException()
                }
            }
        }
    }

    /**
     * Mirrors the production
     * [HttpClientConfigurator][com.vultisig.wallet.data.networkutils.HttpClientConfigurator]
     * classification so transport failures map to the same [NetworkErrorKind] in tests as in
     * production.
     */
    private fun IOException.toNetworkException(): NetworkException =
        when (this) {
            is SocketTimeoutException ->
                NetworkException(0, "Connection timed out", NetworkErrorKind.Timeout, this)
            is UnknownHostException ->
                NetworkException(0, "No internet connection", NetworkErrorKind.NoConnectivity, this)
            else -> NetworkException(0, "Network request failed", NetworkErrorKind.Transport, this)
        }
}
