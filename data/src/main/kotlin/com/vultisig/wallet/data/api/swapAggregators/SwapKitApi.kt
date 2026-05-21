package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.api.errors.SwapKitError
import com.vultisig.wallet.data.api.models.quotes.SwapKitProvidersResponseJson
import com.vultisig.wallet.data.api.models.quotes.SwapKitQuoteRequest
import com.vultisig.wallet.data.api.models.quotes.SwapKitQuoteResponseJson
import com.vultisig.wallet.data.api.models.quotes.SwapKitSwapRequest
import com.vultisig.wallet.data.api.models.quotes.SwapKitSwapResponseJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Ktor client for SwapKit V3 hit through the Vultisig proxy at
 * `https://api.vultisig.com/swapkit-a/v3` (the proxy attaches the partner API key server-side, so
 * the device never holds it).
 *
 * Exposed surface in Phase 1:
 * - [quote] — POST /v3/quote: candidate routes
 * - [swap] — POST /v3/swap: unsigned tx for the winning route
 * - [providers] — GET /providers: per-provider chain enablement (cached upstream, 24h TTL)
 */
interface SwapKitApi {

    /** Fetches candidate routes. Caller filters out THORChain/Maya and multi-hop. */
    suspend fun quote(request: SwapKitQuoteRequest): SwapKitQuoteResponseJson

    /** Resolves the winning route into an unsigned transaction payload. */
    suspend fun swap(request: SwapKitSwapRequest): SwapKitSwapResponseJson

    /** Returns the set of chains each sub-provider currently routes on. */
    suspend fun providers(): SwapKitProvidersResponseJson
}

internal class SwapKitApiImpl
@Inject
constructor(private val httpClient: HttpClient, private val json: Json) : SwapKitApi {

    override suspend fun quote(request: SwapKitQuoteRequest): SwapKitQuoteResponseJson =
        execute("$BASE_URL/quote") {
            httpClient.post("$BASE_URL/quote") {
                headers { accept(ContentType.Application.Json) }
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
        }

    override suspend fun swap(request: SwapKitSwapRequest): SwapKitSwapResponseJson =
        execute("$BASE_URL/swap") {
            httpClient.post("$BASE_URL/swap") {
                headers { accept(ContentType.Application.Json) }
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
        }

    override suspend fun providers(): SwapKitProvidersResponseJson =
        execute("$BASE_URL/providers") {
            httpClient.get("$BASE_URL/providers") {
                headers { accept(ContentType.Application.Json) }
            }
        }

    private suspend inline fun <reified T> execute(
        url: String,
        crossinline call: suspend () -> HttpResponse,
    ): T =
        try {
            withTimeout(REQUEST_TIMEOUT_MS) {
                val response = call()
                if (!response.status.isSuccess()) {
                    val body = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
                    val code = extractErrorCode(body)
                    val status = HttpStatusCode.fromValue(response.status.value).description
                    throw SwapKitError.fromCode(
                        code = code,
                        fallbackMessage = body.ifEmpty { status },
                    )
                }
                try {
                    response.body<T>()
                } catch (e: SerializationException) {
                    throw SwapKitError.Decoding(
                        message = e.message ?: "Failed to decode SwapKit response",
                        cause = e,
                    )
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw SwapKitError.Network(message = "SwapKit request to $url timed out", cause = e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SwapKitError) {
            throw e
        } catch (e: Exception) {
            throw SwapKitError.Network(
                message = e.message ?: "SwapKit request to $url failed",
                cause = e,
            )
        }

    /** Pulls a SwapKit error `code` out of a JSON error body, if present. */
    private fun extractErrorCode(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
                val element = json.parseToJsonElement(body)
                val obj = element as? JsonObject ?: return null
                (obj["code"] ?: obj["error"])?.let { it as? JsonPrimitive }?.content
            }
            .getOrNull()
    }

    companion object {
        /** Vultisig SwapKit proxy. iOS uses `/swapkit/`, Windows `/swapkit-win/`. */
        private const val BASE_URL = "https://api.vultisig.com/swapkit-a/v3"
        private const val REQUEST_TIMEOUT_MS = 30_000L
    }
}
