package com.vultisig.wallet.data.testutils

import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Test utilities for building mock [HttpClient] instances that mirror
 * the production [HttpClientConfigurator][com.vultisig.wallet.data.networkutils.HttpClientConfigurator] setup.
 *
 * These builders install [ContentNegotiation] and [HttpCallValidator] with the same
 * `IOException → NetworkException(httpStatusCode=0)` mapping used in production,
 * ensuring tests validate real behavior.
 */
object MockHttpClient {

    val JSON_HEADERS = headersOf(
        HttpHeaders.ContentType, ContentType.Application.Json.toString()
    )

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /**
     * Builds a client where the transport throws the given [IOException].
     * Mirrors the production setup: `IOException → NetworkException(httpStatusCode=0)`.
     */
    fun throwingIOException(exception: IOException): HttpClient =
        HttpClient(MockEngine { throw exception }) {
            installDefaults()
        }

    /**
     * Builds a client that returns a server response with the given [status] and [body].
     * The [HttpCallValidator] is still installed but won't fire (no transport error).
     */
    fun respondingWith(
        status: HttpStatusCode,
        body: String,
    ): HttpClient = HttpClient(MockEngine {
        respond(content = body, status = status, headers = JSON_HEADERS)
    }) {
        installDefaults()
    }

    /**
     * Installs the standard plugins matching production [com.vultisig.wallet.data.networkutils.HttpClientConfigurator].
     */
    private fun io.ktor.client.HttpClientConfig<*>.installDefaults() {
        install(ContentNegotiation) { json(json, ContentType.Any) }
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
    }
}
