package com.vultisig.wallet.data.testutils

import com.vultisig.wallet.data.utils.NetworkErrorKind
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
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
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.json.Json

/**
 * Test utilities for building mock [HttpClient] instances that mirror the production
 * [HttpClientConfigurator][com.vultisig.wallet.data.networkutils.HttpClientConfigurator] setup.
 *
 * These builders install [ContentNegotiation] and [HttpCallValidator] with the same `IOException →
 * NetworkException(httpStatusCode=0)` mapping used in production, ensuring tests validate real
 * behavior.
 *
 * The MockEngine handler runs on the engine's own dispatcher rather than the caller's, so a test
 * that issues requests concurrently serves them on several pool threads at once. Every builder's
 * per-call state is therefore atomic; a plain `var` there loses calls under that fan-out.
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

    /**
     * Holder for the outgoing request bodies captured by [capturingRequest] and
     * [capturingRequestSequence]. [lastBody] is the most recent one; [bodies] keeps every request
     * in order, which is what a paginated call needs — asserting only the last body cannot show
     * that page two carried the cursor page one returned.
     *
     * Synchronised because the MockEngine handler runs on the engine's own dispatcher, not the
     * caller's: a test that issues requests concurrently records from several pool threads at once,
     * and an unguarded list drops entries there.
     */
    class RequestCapture {
        private val lock = Any()
        private val recorded = mutableListOf<Record>()

        /** Body of every request in order of completion. */
        val bodies: List<String>
            get() = snapshot().map(Record::body)

        /**
         * Encoded query string of every request in order, for asserting the paging parameters a
         * walk actually sent (e.g. `offset`/`limit` per page).
         */
        val queries: List<String>
            get() = snapshot().map(Record::query)

        val lastBody: String
            get() = snapshot().lastOrNull()?.body ?: ""

        /** Encoded path of the most recent request, for asserting which node endpoint was hit. */
        val lastPath: String
            get() = snapshot().lastOrNull()?.path ?: ""

        internal fun record(body: String, path: String, query: String = "") {
            synchronized(lock) { recorded += Record(body, path, query) }
        }

        private fun snapshot(): List<Record> = synchronized(lock) { recorded.toList() }

        private data class Record(val body: String, val path: String, val query: String)
    }

    /**
     * Like [respondingWith], but records each outgoing request body into [capture] so a test can
     * assert what was sent (e.g. RPC params).
     */
    fun capturingRequest(
        status: HttpStatusCode,
        body: String,
        capture: RequestCapture,
        jsonFormat: Json = json,
    ): HttpClient =
        HttpClient(
            MockEngine { request ->
                capture.record(
                    body = request.body.toByteArray().decodeToString(),
                    path = request.url.encodedPath,
                    query = request.url.encodedQuery,
                )
                respond(content = body, status = status, headers = JSON_HEADERS)
            }
        ) {
            installDefaults(jsonFormat)
        }

    /**
     * [respondingWithSequence] with [capturingRequest]'s recording, so a test can assert both what
     * came back per call and what each call sent — e.g. that a paginated request forwards the
     * cursor the previous page returned.
     */
    fun capturingRequestSequence(
        capture: RequestCapture,
        vararg responses: Pair<HttpStatusCode, String>,
        jsonFormat: Json = json,
    ): HttpClient {
        require(responses.isNotEmpty()) {
            "capturingRequestSequence requires at least one response"
        }
        val index = AtomicInteger(0)
        return HttpClient(
            MockEngine { request ->
                capture.record(
                    body = request.body.toByteArray().decodeToString(),
                    path = request.url.encodedPath,
                    query = request.url.encodedQuery,
                )
                val (status, body) = responses[minOf(index.getAndIncrement(), responses.size - 1)]
                respond(content = body, status = status, headers = JSON_HEADERS)
            }
        ) {
            installDefaults(jsonFormat)
        }
    }

    /**
     * Builds a client that steps through [responses] in order, pinning the last entry once the
     * sequence is exhausted. Each entry is a [Pair] of (status, body).
     *
     * Pass a custom [jsonFormat] when a response model contains `@Contextual` fields, same as
     * [respondingWith].
     */
    fun respondingWithSequence(
        vararg responses: Pair<HttpStatusCode, String>,
        jsonFormat: Json = json,
    ): HttpClient {
        require(responses.isNotEmpty()) { "respondingWithSequence requires at least one response" }
        val index = AtomicInteger(0)
        return HttpClient(
            MockEngine {
                val i = minOf(index.getAndIncrement(), responses.size - 1)
                val (status, body) = responses[i]
                respond(content = body, status = status, headers = JSON_HEADERS)
            }
        ) {
            installDefaults(jsonFormat)
        }
    }

    /**
     * Builds a client whose response body is produced per call from the zero-based request index.
     * For sequences too long or too open-ended to enumerate — e.g. a paginated connection that
     * never reports a last page.
     */
    fun respondingWithGenerated(
        status: HttpStatusCode = HttpStatusCode.OK,
        jsonFormat: Json = json,
        body: (Int) -> String,
    ): HttpClient {
        val index = AtomicInteger(0)
        return HttpClient(
            MockEngine {
                respond(
                    content = body(index.getAndIncrement()),
                    status = status,
                    headers = JSON_HEADERS,
                )
            }
        ) {
            installDefaults(jsonFormat)
        }
    }

    /**
     * Builds a client that fails its first [failures] calls with the throwable [failWith] produces,
     * then answers [status] / [body] for every call after that. Mirrors the relay fault the awaited
     * TSS send has to survive: a peer's first outbound POSTs are rejected and then let through.
     *
     * [onCall] receives the zero-based call index before each call is served, for counting.
     */
    fun failingThenResponding(
        failures: Int,
        failWith: () -> Throwable,
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "",
        onCall: (Int) -> Unit = {},
    ): HttpClient {
        val index = AtomicInteger(0)
        return HttpClient(
            MockEngine {
                val call = index.getAndIncrement()
                onCall(call)
                if (call < failures) throw failWith()
                respond(content = body, status = status, headers = JSON_HEADERS)
            }
        ) {
            installDefaults()
        }
    }

    /**
     * Builds a client whose transport never answers, so only a caller-imposed request timeout ends
     * the call. Used to prove the awaited relay send caps a hung POST instead of letting it eat the
     * ceremony's stall budget.
     */
    fun hanging(onCall: () -> Unit = {}): HttpClient =
        HttpClient(
            MockEngine {
                onCall()
                awaitCancellation()
            }
        ) {
            installDefaults()
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
        // Inert unless a request opts in with `timeout { … }`, exactly as in production.
        install(HttpTimeout)
    }

    /**
     * Mirrors the production
     * [HttpClientConfigurator][com.vultisig.wallet.data.networkutils.HttpClientConfigurator]
     * classification so transport failures map to the same [NetworkErrorKind] in tests as in
     * production.
     */
    private fun IOException.toNetworkException(): NetworkException =
        when (this) {
            is SocketTimeoutException,
            is HttpRequestTimeoutException ->
                NetworkException(0, "Connection timed out", NetworkErrorKind.Timeout, this)
            is UnknownHostException ->
                NetworkException(0, "No internet connection", NetworkErrorKind.NoConnectivity, this)
            else -> NetworkException(0, "Network request failed", NetworkErrorKind.Transport, this)
        }
}
