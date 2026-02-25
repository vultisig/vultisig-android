package com.vultisig.wallet.data.networkutils

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
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject

/**
 * Configures the shared [io.ktor.client.HttpClient] with content negotiation, error handling, and retry policy.
 *
 * Plugin installation order matters:
 * 1. **ContentNegotiation** — JSON serialization/deserialization.
 * 2. **DefaultRequest** — default headers for all requests.
 * 3. **HttpCallValidator** — converts transport-level [IOException]s into
 *    [NetworkException] with `httpStatusCode = 0` (client-side error).
 *    This runs *after* [HttpRequestRetry] exhausts its retries.
 * 4. **HttpRequestRetry** — retries on [IOException] (up to 3 times with exponential backoff)
 *    and on 5xx/429/408 responses for safe HTTP methods.
 *
 * The retry plugin fires first: if all retries fail, the [IOException] propagates to
 * [HttpCallValidator], which wraps it in a [NetworkException].
 */
class HttpClientConfigurator @Inject constructor(
    private val json: Json
) {
    fun <T : HttpClientEngineConfig> configure(config: HttpClientConfig<T>) {
        with(config) {
            install(ContentNegotiation) {
                json(json, ContentType.Any)
            }

            install(DefaultRequest) {
                headers.appendIfNameAbsent(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString()
                )
            }

            install(HttpCallValidator) {
                handleResponseExceptionWithRequest { cause, _ ->
                    if (cause is IOException) {
                        throw NetworkException(
                            httpStatusCode = 0,
                            message = "No internet connection",
                            cause = cause,
                        )
                    }
                }
            }

            install(HttpRequestRetry) {
                exponentialDelay()
                retryOnException(
                    maxRetries = 3,
                    retryOnTimeout = true
                )

                retryIf { request, response ->
                    val method = request.method
                    val status = response.status.value

                    val isSafeMethod = method == HttpMethod.Get ||
                            method == HttpMethod.Head ||
                            method == HttpMethod.Options

                    val isServerError = status >= 500
                    val isRetriableStatus = isServerError ||
                            status == HttpStatusCode.TooManyRequests.value ||
                            status == HttpStatusCode.RequestTimeout.value

                    isSafeMethod && isRetriableStatus
                }
            }
        }
    }
}
