package com.vultisig.wallet.data.networkutils

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent
import kotlinx.serialization.json.Json
import javax.inject.Inject

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
