package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.FyndErrorResponseJson
import com.vultisig.wallet.data.api.models.quotes.FyndQuoteRequestJson
import com.vultisig.wallet.data.api.models.quotes.FyndQuoteResponseJson
import com.vultisig.wallet.data.models.Chain
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import kotlinx.serialization.json.Json

/**
 * Where Fynd quotes come from. Production goes through the Vultisig proxy ([PROXY_BASE_URL]),
 * which holds the Fynd API key and signs the client fee. [apiKey] is set only by debug builds that
 * call the hosted Fynd API directly; such quotes can't carry a signed fee.
 */
interface FyndConfig {
    val baseUrl: String
    val apiKey: String?
    val forceEnabled: Boolean

    val isDirect: Boolean
        get() = !apiKey.isNullOrBlank()

    val feeReceiver: String?
        get() = FyndApiImpl.FEE_RECEIVER

    companion object {
        const val PROXY_BASE_URL = "https://api.vultisig.com/fynd"
    }
}

interface FyndApi {
    suspend fun quote(chain: Chain, request: FyndQuoteRequestJson): FyndQuoteResponseJson
}

internal class FyndApiImpl
@Inject
constructor(
    private val httpClient: HttpClient,
    private val config: FyndConfig,
    private val json: Json,
) : FyndApi {

    override suspend fun quote(
        chain: Chain,
        request: FyndQuoteRequestJson,
    ): FyndQuoteResponseJson {
        val chainName = requireNotNull(fyndChainName(chain)) { "Fynd does not support ${chain.raw}" }
        val response =
            httpClient.post("${config.baseUrl.trimEnd('/')}/v1/$chainName/quote") {
                contentType(ContentType.Application.Json)
                config.apiKey?.takeIf { it.isNotBlank() }?.let { header(HttpHeaders.Authorization, it) }
                setBody(json.encodeToString(FyndQuoteRequestJson.serializer(), request))
            }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            if (response.status == HttpStatusCode.TooManyRequests) {
                throw SwapException.RateLimitExceeded("Fynd rate limit exceeded")
            }
            val error =
                runCatching { json.decodeFromString(FyndErrorResponseJson.serializer(), body) }
                    .getOrNull()
            throw fyndStatusException(
                error?.code ?: response.status.value.toString(),
                error?.error ?: response.status.description,
            )
        }
        return json.decodeFromString(FyndQuoteResponseJson.serializer(), body)
    }

    companion object {
        /**
         * Vultisig's Fynd client fee receiver. Fynd requires the receiver to EIP-712 sign every
         * quote, so this is the proxy's fee-signer address (fees accrue to its vault balance in the
         * router and get swept to the treasury), not the multisig treasury used by Kyber.
         *
         * TODO-confirm: set once infra provisions the proxy signer. While null, proxy-mode Fynd
         * quotes that need a fee are refused rather than sent without one.
         */
        val FEE_RECEIVER: String? = null
    }
}

internal fun fyndChainName(chain: Chain): String? =
    when (chain) {
        Chain.Ethereum -> "ethereum"
        Chain.Base -> "base"
        Chain.Arbitrum -> "arbitrum"
        else -> null
    }

internal fun fyndStatusException(code: String, detail: String = code): SwapException =
    when (code.lowercase()) {
        "no_route_found",
        "insufficient_liquidity" -> SwapException.SwapRouteNotAvailable("Fynd: $detail")
        "timeout" -> SwapException.TimeOut("Fynd: $detail")
        "429" -> SwapException.RateLimitExceeded("Fynd rate limit exceeded")
        else -> SwapException.UnkownSwapError("Fynd quote failed: $code ($detail)")
    }
