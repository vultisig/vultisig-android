package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.errors.SwapKitError
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.api.models.quotes.SwapKitEvmTx
import com.vultisig.wallet.data.api.models.quotes.SwapKitQuoteRequest
import com.vultisig.wallet.data.api.models.quotes.SwapKitRoute
import com.vultisig.wallet.data.api.models.quotes.SwapKitSolanaTx
import com.vultisig.wallet.data.api.models.quotes.SwapKitSwapRequest
import com.vultisig.wallet.data.api.models.quotes.SwapKitSwapResponseJson
import com.vultisig.wallet.data.api.models.quotes.SwapKitTonTransfer
import com.vultisig.wallet.data.api.swapAggregators.SwapKitApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import timber.log.Timber

/**
 * SwapKit V3 quote source. Mirrors iOS' `SwapKitService.fetchBestRoute` + `buildSwapTx`:
 * 1. Short-circuit on the user's `SwapKitConfig` opt-in flag (default off) before any network I/O.
 *    No cache gate beyond that — iOS' `SwapKitProviderCache` returns `true` on cache-miss and lets
 *    `/v3/quote` surface unsupported chains via `noRoutesFound`; the Android source matches that
 *    fail-open semantics so a single bad-network app launch doesn't hide SwapKit until refresh.
 * 2. Call `POST /v3/quote`, drop multi-hop routes and any route whose sub-provider is THORChain or
 *    Maya (Vultisig pays those affiliates directly via the existing integrations — routing through
 *    SwapKit would stack a second affiliate fee on top). Filter uses the wire-format provider ids
 *    (`thorchain`, `thorchain_streaming`, `mayachain`, `mayachain_streaming`) so streaming variants
 *    don't slip through.
 * 3. Rank the survivors by `expectedBuyAmount` (string-decimal, higher wins) and pick the best.
 * 4. Call `POST /v3/swap` with the chosen `routeId` to materialise the unsigned tx, then wrap the
 *    EVM-shaped or Solana-shaped payload into the existing [EVMSwapQuoteJson] envelope so the rest
 *    of the swap pipeline (signing, broadcast, proto round-trip via `oneinchSwapPayload`) needs no
 *    changes for Phase 1. The route's `fees[]` entry with `type=="inbound"` for the source chain
 *    becomes `tx.swapFee` (raw native units) so the displayed Network Fee reconciles against the
 *    actual on-chain debit — especially on Solana / Chainflip / NEAR where the EVM gas envelope
 *    reads zero. EVM routes that omit `tx.gas` are refused (`SwapKitError.Decoding`) rather than
 *    backstopped with the L1-sized default, which would over-estimate L2 fees by multiples.
 *
 * TON routes flow through [SwapQuoteResult.SwapKit] carrying a [SwapKitSwapPayloadJson] with
 * `txType="TON"` and the canonical transfer JSON bytes; the keysign-side dispatcher decodes those
 * into `KeysignPayload.signTon` so the existing TonHelper signing path picks them up unchanged.
 * Other non-EVM `txType`s (PSBT, TRON, SUI, CARDANO, RIPPLE) surface as
 * [SwapKitError.UnsupportedTxType] until their per-chain signers ship.
 */
internal class SwapKitQuoteSource
@Inject
constructor(
    private val api: SwapKitApi,
    private val config: SwapKitConfig,
    private val json: Json,
) : SwapQuoteSource {

    override suspend fun fetch(request: SwapQuoteRequest): SwapQuoteResult {
        return try {
            fetchInternal(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SwapKitError) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "SwapKit quote fetch failed")
            throw SwapKitError.Network(
                message = e.message ?: "SwapKit quote fetch failed",
                cause = e,
            )
        }
    }

    private suspend fun fetchInternal(request: SwapQuoteRequest): SwapQuoteResult {
        // Feature-flag kill switch (Advanced Settings → SwapKit). Defaults to off — short-circuits
        // before any /quote network I/O so the proxy stays cold until the user opts in.
        if (!config.isFeatureEnabled.first()) {
            throw SwapKitError.NoRoutes("SwapKit feature flag disabled")
        }

        // No provider-cache gate here on purpose. iOS' SwapKitProviderCache returns `true` on a
        // cache-miss and lets `/v3/quote` surface the real error so a single bad-network app
        // launch doesn't silently hide SwapKit until the next refresh. Android's cache currently
        // returns `false` on cache-miss (fail-closed), which would diverge. Letting `/v3/quote`
        // be the authority — it returns a clear `noRoutesFound` for unsupported chains — keeps
        // the user opt-in and the swap picker behaviour aligned with iOS.

        val quoteResponse =
            api.quote(
                SwapKitQuoteRequest(
                    sellAsset = assetIdentifier(request.srcToken),
                    buyAsset = assetIdentifier(request.dstToken),
                    // SwapKit interprets sellAmount as the human-readable decimal amount of the
                    // source asset (e.g. "0.0086" BNB), NOT raw base units. Sending the wei value
                    // makes the upstream read it as ~8.6 quadrillion BNB and every provider
                    // returns noRoutesFound. Match iOS' formatSellAmount: strip trailing zeros so
                    // "1.0000" → "1" and avoid any locale-introduced separators.
                    sellAmount = formatSellAmount(request.tokenValue),
                    sourceAddress = request.srcAddress.ifBlank { null },
                    destinationAddress = request.dstAddress.ifBlank { null },
                    affiliateFee = request.affiliateBps,
                )
            )

        val best =
            pickBestRoute(quoteResponse.routes)
                ?: throw SwapKitError.NoRoutes(
                    "No SwapKit route survived single-hop + Thor/Maya filter"
                )

        val routeId =
            best.routeId
                ?: throw SwapKitError.NoRoutes(
                    "SwapKit route has no routeId — cannot call /v3/swap"
                )

        val swapResponse =
            api.swap(
                SwapKitSwapRequest(
                    routeId = routeId,
                    sourceAddress = request.srcToken.address,
                    destinationAddress = request.dstToken.address,
                )
            )
        // TODO: persist swapResponse.swapId on the SwapTransaction so /track lookups can correlate
        //  cross-chain settlement back to this quote once tx-history /track polling lands.
        val resolvedSubProvider =
            swapResponse.providers.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: swapResponse.meta.subProvider?.takeIf { it.isNotBlank() }
        return when (txTypeOf(swapResponse)) {
            TxKind.EVM,
            TxKind.SOLANA ->
                SwapQuoteResult.Evm(
                    data =
                        buildEvmQuoteFromSwapKit(
                            swapResponse,
                            request.srcToken,
                            request.dstToken,
                            best,
                        ),
                    subProvider = resolvedSubProvider,
                )
            TxKind.TON ->
                SwapQuoteResult.SwapKit(
                    buildSwapKitTonQuote(
                        swapResponse,
                        request.srcToken,
                        request.dstToken,
                        best,
                        resolvedSubProvider,
                    )
                )
            TxKind.UNSUPPORTED -> throw SwapKitError.UnsupportedTxType(swapResponse.meta.txType)
        }
    }

    /**
     * Drop multi-hop (>1 sub-provider) and routes whose primary sub-provider is THORChain or Maya —
     * Vultisig pays those affiliates directly via its existing native integrations, so routing them
     * through SwapKit would stack a second affiliate fee.
     */
    private fun pickBestRoute(routes: List<SwapKitRoute>): SwapKitRoute? {
        val filtered =
            routes.filter { route ->
                if (route.providers.size != 1) return@filter false
                // SwapKitRoute.primaryProviderId already lower-cases the wire value, but
                // .lowercase() again here is a deliberate belt-and-braces: if a future change
                // to the DTO ever drops the normalization, the filter still excludes Thor/Maya
                // sub-providers rather than silently letting them through (which would stack a
                // second affiliate fee on top of Vultisig's native Thor/Maya integrations).
                val primary = route.primaryProviderId.lowercase()
                primary !in FILTERED_PROVIDERS
            }
        if (filtered.isEmpty()) return null

        return filtered
            .mapNotNull { route ->
                val amount =
                    route.expectedBuyAmount?.let { runCatching { BigDecimal(it) }.getOrNull() }
                amount?.let { route to it }
            }
            .maxByOrNull { it.second }
            ?.first
    }

    /**
     * Extract the source-chain inbound fee from `route.fees[]` — the canonical SwapKit-attributed
     * cost of executing the deposit on the source chain. Mirrors iOS' `SwapKitService.inboundFee`
     * so the displayed Network Fee reconciles against the actual on-chain debit (especially on
     * Solana and cross-chain Chainflip / NEAR routes where the EVM gas envelope would otherwise
     * read zero). Returns `0` when the entry is absent so the UI surfaces "—" rather than blocking
     * the quote.
     */
    private fun inboundFeeRawUnits(srcToken: Coin, route: SwapKitRoute): BigInteger {
        val prefix = chainPrefix(srcToken.chain) ?: return BigInteger.ZERO
        val inbound =
            route.fees.firstOrNull { fee ->
                fee.type?.equals("inbound", ignoreCase = true) == true && fee.chain == prefix
            } ?: return BigInteger.ZERO
        val amount =
            inbound.amount?.let { runCatching { BigDecimal(it) }.getOrNull() }
                ?: return BigInteger.ZERO
        return amount.movePointRight(srcToken.decimal).toBigInteger()
    }

    /**
     * EVM or Solana SwapKit response → [EVMSwapQuoteJson]. `dstAmount` is scaled to raw units and
     * the inbound fee is embedded on `tx.swapFee` so consumers read it through the same
     * resolveSwapFee path as Kyber/Jupiter. EVM routes that omit `tx.gas` are refused — the L1
     * default over-estimates Arbitrum / Optimism / Base / Polygon by multiples.
     */
    private fun buildEvmQuoteFromSwapKit(
        response: SwapKitSwapResponseJson,
        srcToken: Coin,
        dstToken: Coin,
        route: SwapKitRoute,
    ): EVMSwapQuoteJson {
        val rawDstAmount = scaleDecimalToRawUnits(response.expectedBuyAmount, dstToken.decimal)
        val inboundFee = inboundFeeRawUnits(srcToken, route).toString()
        return when (txTypeOf(response)) {
            TxKind.EVM -> {
                val evm = decode<SwapKitEvmTx>(response.tx, "evm")
                val gas =
                    evm.gas?.toLongOrNull()?.takeIf { it > 0 }
                        ?: throw SwapKitError.Decoding(
                            "SwapKit EVM tx missing gas estimate (got ${evm.gas}); refusing route"
                        )
                EVMSwapQuoteJson(
                    dstAmount = rawDstAmount,
                    tx =
                        OneInchSwapTxJson(
                            from = evm.from.orEmpty(),
                            to = evm.to,
                            data = evm.data,
                            gas = gas,
                            value = evm.value,
                            gasPrice = evm.gasPrice.orEmpty(),
                            swapFee = inboundFee,
                            swapFeeTokenContract = "",
                        ),
                )
            }
            TxKind.SOLANA -> {
                val base64 = solanaBase64(response.tx)
                EVMSwapQuoteJson(
                    dstAmount = rawDstAmount,
                    // Solana signers read the base64 blob from `data`; from/to/gas/value/gasPrice
                    // on the EVM envelope are unused for Solana — matches JupiterQuoteSource.
                    tx =
                        OneInchSwapTxJson(
                            from = "",
                            to = "",
                            data = base64,
                            gas = 0,
                            value = "0",
                            gasPrice = "0",
                            swapFee = inboundFee,
                            swapFeeTokenContract = "",
                        ),
                )
            }
            TxKind.TON,
            TxKind.UNSUPPORTED ->
                throw IllegalStateException("Non-EVM/Solana txType reached EVM builder")
        }
    }

    /**
     * TON SwapKit response → [SwapQuote.SwapKit]. SwapKit returns `tx` as a `[{address, amount}]`
     * array; the canonical JSON bytes are stashed on [SwapKitSwapPayloadJson.txPayload] for
     * cross-device transit. The keysign-side dispatcher decodes them back to populate
     * `KeysignPayload.signTon` so the existing TonHelper signing path picks them up unchanged.
     */
    private fun buildSwapKitTonQuote(
        response: SwapKitSwapResponseJson,
        srcToken: Coin,
        dstToken: Coin,
        route: SwapKitRoute,
        subProvider: String?,
    ): com.vultisig.wallet.data.models.SwapQuote.SwapKit {
        val transfers = decodeTonTransfers(response.tx)
        val first =
            transfers.firstOrNull()
                ?: throw SwapKitError.Decoding("SwapKit TON tx is an empty transfer array")
        val fromAmount =
            first.amount.toBigIntegerOrNull()
                ?: throw SwapKitError.Decoding(
                    "SwapKit TON transfer amount is not an integer: ${first.amount}"
                )
        val canonicalBytes = json.encodeToString(transfers).toByteArray(Charsets.UTF_8)
        val toAmountDecimal =
            response.expectedBuyAmount?.let { runCatching { BigDecimal(it) }.getOrNull() }
                ?: throw SwapKitError.Decoding(
                    "SwapKit expectedBuyAmount missing or unparseable: ${response.expectedBuyAmount}"
                )
        val payload =
            SwapKitSwapPayloadJson(
                fromCoin = srcToken,
                toCoin = dstToken,
                fromAmount = fromAmount,
                toAmountDecimal = toAmountDecimal,
                txType = TX_TYPE_TON,
                txPayload = canonicalBytes,
                targetAddress = response.targetAddress.orEmpty().ifBlank { first.address },
                inboundAddress = null,
                memo = null,
                subProvider = subProvider.orEmpty(),
                swapId = response.swapId.orEmpty(),
            )
        return com.vultisig.wallet.data.models.SwapQuote.SwapKit(
            expectedDstValue =
                TokenValue(
                    value = toAmountDecimal.movePointRight(dstToken.decimal).toBigInteger(),
                    token = dstToken,
                ),
            fees = TokenValue(value = inboundFeeRawUnits(srcToken, route), token = srcToken),
            expiredAt =
                kotlinx.datetime.Clock.System.now() +
                    com.vultisig.wallet.data.models.SwapQuote.expiredAfter,
            data = payload,
            subProvider = subProvider,
        )
    }

    private fun decodeTonTransfers(element: JsonElement): List<SwapKitTonTransfer> =
        try {
            json.decodeFromJsonElement<List<SwapKitTonTransfer>>(element)
        } catch (e: Exception) {
            throw SwapKitError.Decoding("Failed to decode SwapKit TON transfer array", cause = e)
        }

    private fun txTypeOf(response: SwapKitSwapResponseJson): TxKind =
        when (response.meta.type) {
            "evm" -> TxKind.EVM
            "solana",
            "serialized_base64" -> TxKind.SOLANA
            "ton" -> TxKind.TON
            else -> TxKind.UNSUPPORTED
        }

    /**
     * Pull the Solana base64 swap blob out of `tx`. V3 ships it as a top-level [JsonPrimitive];
     * fall back to the legacy [SwapKitSolanaTx] object shape so a transitional response (or a test
     * fixture using either form) is decoded safely.
     */
    private fun solanaBase64(element: JsonElement): String {
        (element as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.let {
                return it
            }
        if (element is JsonObject) {
            val nested = decode<SwapKitSolanaTx>(element, "solana")
            nested.swapTransaction?.let {
                return it
            }
            nested.message?.let {
                return it
            }
        }
        throw SwapKitError.Decoding(
            "SwapKit Solana tx is neither a base64 string nor a known object shape"
        )
    }

    private enum class TxKind {
        EVM,
        SOLANA,
        TON,
        UNSUPPORTED,
    }

    /**
     * Convert SwapKit's human-readable `expectedBuyAmount` (decimal string, e.g. "42.5") into the
     * raw-units integer string the rest of the swap pipeline expects. Throws
     * [SwapKitError.Decoding] when the upstream value is missing or unparseable so a malformed
     * payload surfaces explicitly rather than masking as a zero quote.
     */
    private fun scaleDecimalToRawUnits(decimalString: String?, decimals: Int): String {
        val parsed =
            decimalString?.let { runCatching { BigDecimal(it) }.getOrNull() }
                ?: throw SwapKitError.Decoding(
                    "SwapKit expectedBuyAmount missing or not a decimal: $decimalString"
                )
        return parsed.movePointRight(decimals).toBigInteger().toString()
    }

    /**
     * SwapKit asset identifier: `CHAIN.TICKER` or `CHAIN.TICKER-CONTRACT` for ERC-20-style tokens.
     */
    private fun assetIdentifier(coin: Coin): String {
        // The provider-cache gate upstream of this call should already block any chain we don't
        // have a SwapKit prefix mapping for. Throwing NoRoutes here (rather than falling back to
        // `coin.ticker`) is defense-in-depth: a future cache change that enables a chain we
        // haven't mapped would otherwise mint a garbage identifier (e.g. `ETH.ETH` for ZkSync.ETH)
        // and surface as `helpers_invalid_asset_identifier` 500s from the proxy.
        val prefix =
            chainPrefix(coin.chain)
                ?: throw SwapKitError.NoRoutes(
                    "SwapKit asset identifier missing chain prefix for ${coin.chain.raw}"
                )
        val ticker = coin.ticker
        return if (coin.isNativeToken || coin.contractAddress.isBlank()) {
            "$prefix.$ticker"
        } else {
            "$prefix.$ticker-${coin.contractAddress}"
        }
    }

    private fun chainPrefix(chain: Chain): String? =
        when (chain) {
            Chain.Ethereum -> "ETH"
            Chain.BscChain -> "BSC"
            Chain.Avalanche -> "AVAX"
            Chain.Arbitrum -> "ARB"
            Chain.Optimism -> "OP"
            Chain.Base -> "BASE"
            Chain.Polygon -> "POL"
            Chain.Solana -> "SOL"
            Chain.Ton -> "TON"
            else -> null
        }

    private inline fun <reified T> decode(element: JsonElement, label: String): T =
        try {
            json.decodeFromJsonElement<T>(element)
        } catch (e: Exception) {
            throw SwapKitError.Decoding(
                message = "Failed to decode SwapKit $label tx payload",
                cause = e,
            )
        }

    private companion object {
        /** [SwapKitSwapPayloadJson.txType] tag for SwapKit TON routes. */
        const val TX_TYPE_TON = "TON"

        /**
         * Wire-format sub-provider ids excluded client-side to avoid double affiliate-fee charges.
         */
        private val FILTERED_PROVIDERS: Set<String> =
            setOf("thorchain", "thorchain_streaming", "mayachain", "mayachain_streaming")

        /** Dot-separated decimal `sellAmount`. Trailing zeros stripped so "1.0000" → "1". */
        private fun formatSellAmount(tokenValue: TokenValue): String =
            tokenValue.decimal.stripTrailingZeros().toPlainString()
    }
}
