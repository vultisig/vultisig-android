package com.vultisig.wallet.data.utils

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.WebsocketContentConvertException
import io.ktor.serialization.WebsocketDeserializeException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/**
 * Deserializes a successful HTTP response body to [T], or throws [NetworkException] on failure.
 * - **2xx response**: attempts `body<T>()`. If deserialization fails (e.g. unexpected JSON shape),
 *   wraps the error in a [NetworkException] with the original HTTP status code and an error message
 *   extracted from the response body using [errorKey].
 * - **Non-2xx response**: throws [NetworkException] with the HTTP status code and the raw body
 *   text.
 *
 * Prefer this over raw `.body<T>()` in API methods for consistent error wrapping.
 *
 * @param errorKey JSON key to look for when extracting a human-readable error message from error
 *   responses. Defaults to `"message"`.
 * @throws NetworkException always on non-2xx responses, or on 2xx deserialization failure.
 */
suspend inline fun <reified T> HttpResponse.bodyOrThrow(errorKey: String = "message"): T {
    return if (status.isSuccess()) {
        try {
            body()
        } catch (t: JsonConvertException) {
            throw NetworkException(status.value, extractError(this, errorKey), t)
        } catch (t: ContentConvertException) {
            throw NetworkException(status.value, extractError(this, errorKey), t)
        } catch (t: WebsocketDeserializeException) {
            throw NetworkException(status.value, extractError(this, errorKey), t)
        } catch (t: WebsocketContentConvertException) {
            throw NetworkException(status.value, extractError(this, errorKey), t)
        }
    } else {
        throw NetworkException(status.value, bodyAsText())
    }
}

/**
 * Extracts a human-readable error message from an HTTP [response] body.
 *
 * Attempts to parse the body as JSON and recursively search for a value matching [errorKey]. Falls
 * back to the raw body text if parsing fails or the key is not found.
 */
suspend fun extractError(response: HttpResponse, errorKey: String): String {
    val body = response.bodyAsText()

    return try {
        val element = Json.parseToJsonElement(body)

        findValueRecursively(element, errorKey) ?: body
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        Timber.e(t, "Failed to extract error from response")
        body
    }
}

private fun findValueRecursively(element: JsonElement, key: String): String? {
    return when (element) {
        is JsonObject -> {
            element[key]?.takeIf { it is JsonPrimitive && it.isString }?.jsonPrimitive?.content
                ?: element.values
                    .asSequence()
                    .mapNotNull { child -> findValueRecursively(child, key) }
                    .firstOrNull()
        }

        is JsonArray -> {
            element
                .asSequence()
                .mapNotNull { child -> findValueRecursively(child, key) }
                .firstOrNull()
        }

        else -> null
    }
}

/**
 * Categorises a [NetworkException] so callers can show a cause-specific message instead of a single
 * generic "something went wrong". Transport failures are no longer collapsed into one bucket: a
 * read/socket timeout ([Timeout]) is distinct from a genuine lack of connectivity
 * ([NoConnectivity]).
 */
enum class NetworkErrorKind {
    /** Read/socket timeout — the server was reachable but did not respond in time. */
    Timeout,
    /** No connectivity — DNS resolution failed / host unreachable, typically offline. */
    NoConnectivity,
    /** Other transport-level failure (connection refused, SSL handshake, generic I/O). */
    Transport,
    /** The server returned an HTTP error response (4xx / 5xx). */
    Http,
}

/**
 * Represents a network-related error — either a transport failure or an HTTP error response.
 *
 * [httpStatusCode] semantics:
 * - `0` — transport-level failure (no HTTP response was received). Thrown by
 *   [HttpClientConfigurator][com.vultisig.wallet.data.networkutils.HttpClientConfigurator]'s
 *   `HttpCallValidator` when an [java.io.IOException] occurs (DNS failure, timeout, SSL error,
 *   etc.). [kind] then carries the specific transport cause.
 * - `4xx / 5xx` — the server returned an error response. Thrown by [bodyOrThrow] when the HTTP
 *   status is not successful; [kind] defaults to [NetworkErrorKind.Http].
 *
 * Extends [RuntimeException] so it is caught by existing `catch(Exception)` blocks.
 */
class NetworkException(
    val httpStatusCode: Int,
    override val message: String,
    val kind: NetworkErrorKind = NetworkErrorKind.Http,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /**
     * Backwards-compatible constructor for callers that don't classify the failure (HTTP errors).
     */
    constructor(
        httpStatusCode: Int,
        message: String,
        cause: Throwable?,
    ) : this(httpStatusCode, message, NetworkErrorKind.Http, cause)
}

/**
 * Walks this throwable's cause chain and returns the first transport-level [NetworkErrorKind] it
 * can identify, or `null` when the failure isn't a recognisable network/transport error (e.g. a
 * server HTTP error or an application bug). Used by the UI layer to pick a cause-specific error
 * message.
 */
fun Throwable.networkErrorKind(): NetworkErrorKind? {
    var current: Throwable? = this
    while (current != null) {
        when (current) {
            is NetworkException -> if (current.kind != NetworkErrorKind.Http) return current.kind
            is java.net.SocketTimeoutException -> return NetworkErrorKind.Timeout
            is java.net.UnknownHostException -> return NetworkErrorKind.NoConnectivity
        }
        current = current.cause
    }
    return null
}
