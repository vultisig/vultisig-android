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
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.JsonConvertException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Ktor client for SwapKit V3 hit through the Vultisig proxy at `https://api.vultisig.com/swapkit-a`
 * (the proxy attaches the partner API key server-side, so the device never holds it).
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

/** Ktor-backed [SwapKitApi] hitting the Vultisig SwapKit proxy with a 30s per-request timeout. */
internal class SwapKitApiImpl
@Inject
constructor(private val httpClient: HttpClient, private val json: Json) : SwapKitApi {

    override suspend fun quote(request: SwapKitQuoteRequest): SwapKitQuoteResponseJson =
        execute(QUOTE_URL) {
            httpClient.post(QUOTE_URL) {
                headers { accept(ContentType.Application.Json) }
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
        }

    override suspend fun swap(request: SwapKitSwapRequest): SwapKitSwapResponseJson =
        execute(SWAP_URL) {
            httpClient.post(SWAP_URL) {
                headers { accept(ContentType.Application.Json) }
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
        }

    override suspend fun providers(): SwapKitProvidersResponseJson =
        execute(PROVIDERS_URL) {
            httpClient.get(PROVIDERS_URL) { headers { accept(ContentType.Application.Json) } }
        }

    private suspend inline fun <reified T> execute(
        url: String,
        crossinline call: suspend () -> HttpResponse,
    ): T =
        try {
            withTimeout(REQUEST_TIMEOUT_MS) {
                val response = call()
                if (!response.status.isSuccess()) {
                    val body =
                        try {
                            response.bodyAsText()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            ""
                        }
                    val code = extractErrorCode(body)
                    val status = response.status.description
                    throw SwapKitError.fromCode(
                        code = code,
                        fallbackMessage = body.ifEmpty { status },
                        httpStatus = response.status.value,
                    )
                }
                // Deliberately uses raw `body<T>()` rather than the project-wide `bodyOrThrow`
                // helper: SwapKit needs typed `SwapKitError.Decoding` for the swap UI, not the
                // generic `NetworkException` that `bodyOrThrow` wraps deserialization failures in.
                // Ktor's content-negotiation plugin wraps decode failures in `JsonConvertException`
                // (a subclass of `ContentConvertException`), not `kotlinx.serialization`'s
                // `SerializationException`, so we catch the Ktor types here.
                try {
                    response.body<T>()
                } catch (e: JsonConvertException) {
                    throw SwapKitError.Decoding(
                        message = e.message ?: "Failed to decode SwapKit response",
                        cause = e,
                    )
                } catch (e: ContentConvertException) {
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

    /**
     * Pulls a SwapKit error `code` out of a JSON error body, if present. Searches recursively so
     * envelopes like `{"error": {"code": ...}}` or `{"errors": [{"code": ...}]}` still resolve to a
     * typed [SwapKitError]; falls back to a top-level `error` string when no `code` is found.
     */
    private fun extractErrorCode(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
                val element = json.parseToJsonElement(body)
                findStringRecursively(element, "code")
                    ?: (element as? JsonObject)?.get("error")?.let { it as? JsonPrimitive }?.content
            }
            .getOrNull()
    }

    private fun findStringRecursively(element: JsonElement, key: String): String? =
        when (element) {
            is JsonObject ->
                (element[key] as? JsonPrimitive)?.content
                    ?: element.values
                        .asSequence()
                        .mapNotNull { findStringRecursively(it, key) }
                        .firstOrNull()
            is JsonArray ->
                element.asSequence().mapNotNull { findStringRecursively(it, key) }.firstOrNull()
            else -> null
        }

    companion object {
        /** Vultisig SwapKit proxy base. iOS uses `/swapkit/`, Windows `/swapkit-win/`. */
        private const val BASE_URL = "https://api.vultisig.com/swapkit-a"

        /**
         * Per the SwapKit V3 docs `/quote` and `/swap` sit under `/v3` whereas `/providers` is
         * unversioned. The constants below pin both paths in lockstep with the error message used
         * inside [execute] so they can never drift.
         */
        private const val QUOTE_URL = "$BASE_URL/v3/quote"
        private const val SWAP_URL = "$BASE_URL/v3/swap"
        private const val PROVIDERS_URL = "$BASE_URL/providers"
        private const val REQUEST_TIMEOUT_MS = 30_000L
    }
}
