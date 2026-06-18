package com.vultisig.wallet.data.networkutils

import com.vultisig.wallet.data.utils.NetworkErrorKind
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json

/**
 * Configures the shared [io.ktor.client.HttpClient] with content negotiation, error handling, and
 * retry policy.
 *
 * Plugin installation order matters:
 * 1. **ContentNegotiation** — JSON serialization/deserialization.
 * 2. **DefaultRequest** — default headers for all requests.
 * 3. **HttpCallValidator** — converts transport-level [IOException]s into [NetworkException] with
 *    `httpStatusCode = 0` (client-side error) and a [NetworkErrorKind] identifying the specific
 *    transport cause (timeout vs. no connectivity vs. other). This runs *after* [HttpRequestRetry]
 *    exhausts its retries.
 * 4. **HttpRequestRetry** — retries idempotent (GET/HEAD/OPTIONS) requests up to 3 times with
 *    exponential backoff: on transport [IOException]s/read-timeouts and on 5xx/429/408 responses.
 *    Non-idempotent requests (e.g. POST broadcasts) are never auto-retried, so a write the server
 *    received but slow-ACKed is not silently resent.
 *
 * The retry plugin fires first: if all retries fail, the [IOException] propagates to
 * [HttpCallValidator], which wraps it in a [NetworkException].
 */
class HttpClientConfigurator @Inject constructor(private val json: Json) {
    fun <T : HttpClientEngineConfig> configure(config: HttpClientConfig<T>) {
        with(config) {
            install(ContentNegotiation) { json(json, ContentType.Any) }

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

            install(HttpRequestRetry) {
                exponentialDelay()

                // Retry transport failures (IOException, read-timeout) only for idempotent
                // methods. A non-idempotent request — e.g. a transaction broadcast the node
                // already received but slow-ACKed — must not be silently resent. The
                // CancellationException guard is required because HttpRequestRetry passes the
                // cause straight to this predicate without filtering out coroutine cancellation.
                retryOnExceptionIf(maxRetries = 3) { request, cause ->
                    isSafeMethod(request.method) && cause !is CancellationException
                }

                retryIf { request, response ->
                    val status = response.status.value

                    val isServerError = status >= 500
                    val isRetriableStatus =
                        isServerError ||
                            status == HttpStatusCode.TooManyRequests.value ||
                            status == HttpStatusCode.RequestTimeout.value

                    isSafeMethod(request.method) && isRetriableStatus
                }
            }
        }
    }
}

/** Idempotent HTTP methods that are safe to retry automatically. */
private fun isSafeMethod(method: HttpMethod): Boolean =
    method == HttpMethod.Get || method == HttpMethod.Head || method == HttpMethod.Options

/**
 * Maps a transport-level [IOException] to a classified [NetworkException]. A read/socket timeout is
 * reported as a timeout rather than a lack of connectivity — wrapping every [IOException] as "No
 * internet connection" was misleading both to users and when triaging logs (see issue #4956).
 */
private fun IOException.toNetworkException(): NetworkException =
    when (this) {
        is SocketTimeoutException ->
            NetworkException(0, "Connection timed out", NetworkErrorKind.Timeout, this)
        is UnknownHostException ->
            NetworkException(0, "No internet connection", NetworkErrorKind.NoConnectivity, this)
        else -> NetworkException(0, "Network request failed", NetworkErrorKind.Transport, this)
    }
