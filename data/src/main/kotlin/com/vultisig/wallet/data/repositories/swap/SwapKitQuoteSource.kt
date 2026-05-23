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
import com.vultisig.wallet.data.api.models.quotes.SwapKitTxMeta
import com.vultisig.wallet.data.api.swapAggregators.SwapKitApi
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import timber.log.Timber

/**
 * SwapKit V3 quote source. Mirrors iOS' `SwapKitService.fetchBestRoute` + `buildSwapTx`:
 * 1. Guard on the user's `SwapKitConfig` opt-in flag and the cached `/providers` enablement for
 *    both source and destination chains — fails closed so a flag-off / cold-cache call never
 *    reaches the proxy.
 * 2. Call `POST /v3/quote`, drop multi-hop routes and any route whose sub-provider is THORChain or
 *    Maya (Vultisig pays those affiliates directly via the existing integrations — routing through
 *    SwapKit would stack a second affiliate fee on top).
 * 3. Rank the survivors by `expectedBuyAmount` (string-decimal, higher wins) and pick the best.
 * 4. Call `POST /v3/swap` with the chosen `routeId` to materialise the unsigned tx, then wrap the
 *    EVM-shaped or Solana-shaped payload into the existing [EVMSwapQuoteJson] envelope so the rest
 *    of the swap pipeline (signing, broadcast, proto round-trip via `oneinchSwapPayload`) needs no
 *    changes for Phase 1.
 *
 * Non-EVM/non-Solana `txType`s (PSBT, TRON, TON, SUI, CARDANO, RIPPLE, …) are out of scope for
 * Phase 1 — they require per-chain signers and the dedicated `SwapKitSwapPayload` proto, which land
 * in subsequent task batches. Such responses surface as [SwapKitError.UnsupportedTxType] so the
 * picker falls back to another provider rather than signing garbage.
 */
internal class SwapKitQuoteSource
@Inject
constructor(
    private val api: SwapKitApi,
    private val config: SwapKitConfig,
    private val providerCache: SwapKitProviderCache,
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

        // Provider-cache gate — both ends must be enabled in the cached /providers snapshot.
        // Fails closed: when the cache can't be hydrated isEnabled returns false, so we skip
        // SwapKit and let another provider take the route rather than firing a /quote that we
        // already expect to fail.
        if (!providerCache.isEnabled(request.srcToken.chain)) {
            throw SwapKitError.NoRoutes(
                "SwapKit not enabled for source chain ${request.srcToken.chain.raw}"
            )
        }
        if (!providerCache.isEnabled(request.dstToken.chain)) {
            throw SwapKitError.NoRoutes(
                "SwapKit not enabled for destination chain ${request.dstToken.chain.raw}"
            )
        }

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
        // TODO(swapkit /track polling, later task): persist swapResponse.swapId on the resulting
        //  SwapTransaction so /track lookups can correlate cross-chain settlement back to this
        //  specific quote. The DTO already carries it; the persistence wiring lands with the
        //  tx-history Phase D work.

        // SwapQuoteResult.Evm is the envelope for both EVM and Solana SwapKit txTypes — Solana
        // signers pull the base64 blob from `tx.data`, matching how JupiterQuoteSource already
        // stages a Solana swap. The Evm-typed result is intentional, not a copy-paste.
        return SwapQuoteResult.Evm(buildEvmQuoteFromSwapKit(swapResponse, request.dstToken))
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
     * Wrap the SwapKit `/v3/swap` response into the EVM-shaped [EVMSwapQuoteJson] envelope used by
     * the existing pipeline. The `tx` blob's wire shape varies by `meta.txType`:
     * - "evm" → decode as [SwapKitEvmTx], fields map 1:1 to [OneInchSwapTxJson]
     * - "solana" → decode as [SwapKitSolanaTx], stash the base64 swap blob in `data` like
     *   [com.vultisig.wallet.data.repositories.swap.JupiterQuoteSource] does so the downstream
     *   Solana signer can pull it the same way it pulls a Jupiter tx today Anything else
     *   (PSBT/TRON/TON/…) is out of scope until the per-chain signers ship.
     */
    private fun buildEvmQuoteFromSwapKit(
        response: SwapKitSwapResponseJson,
        dstToken: Coin,
    ): EVMSwapQuoteJson {
        // SwapKit V3 reports `expectedBuyAmount` as a human-readable decimal string ("42.5"
        // USDC) per the docs. Every other Android quote source (1inch / Kyber / LiFi / Jupiter)
        // stores `EVMSwapQuoteJson.dstAmount` as a raw-units integer string, and the consumer
        // (`SwapQuoteManager`) parses it back with `toBigIntegerOrNull`. Scale at this boundary
        // so the contract stays uniform — otherwise "42.5" becomes ZERO and "2500" becomes 6
        // orders of magnitude smaller than the actual receive amount.
        val rawDstAmount = scaleDecimalToRawUnits(response.expectedBuyAmount, dstToken.decimal)
        // `meta.type` is the lowercase canonical txType (computed by SwapKitTxMeta from `txType`),
        // matched against TYPE_EVM / TYPE_SOLANA. `meta.txType` (raw upstream casing) is used only
        // in the UnsupportedTxType message so diagnostics surface the wire value verbatim — e.g.
        // "PSBT" not "psbt" — without affecting dispatch.
        val txType = response.meta.type
        return when (txType) {
            SwapKitTxMeta.TYPE_EVM -> {
                val evm = decode<SwapKitEvmTx>(response.tx, "evm")
                val gas =
                    evm.gas?.toLongOrNull()?.takeIf { it > 0 }
                        ?: EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT
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
                            // Per-leg swap fees on SwapKit are surfaced via the response-level
                            // `fees[]` array (type=="inbound"|"outbound"|"affiliate"|…). Phase 1
                            // does not parse those out here — gas math lands via the standard EVM
                            // path and the inbound-fee surfacing belongs with the tier-discount
                            // affiliate work in a later task.
                            swapFee = "0",
                            swapFeeTokenContract = "",
                        ),
                )
            }
            SwapKitTxMeta.TYPE_SOLANA -> {
                val solana = decode<SwapKitSolanaTx>(response.tx, "solana")
                val base64 =
                    solana.swapTransaction
                        ?: solana.message
                        ?: throw SwapKitError.Decoding(
                            "SwapKit Solana tx missing both swapTransaction and message"
                        )
                EVMSwapQuoteJson(
                    dstAmount = rawDstAmount,
                    tx =
                        OneInchSwapTxJson(
                            // Solana signers don't read from/to/gas/value/gasPrice off the
                            // OneInchSwapTxJson envelope — they consume the base64 blob in `data`,
                            // matching how JupiterQuoteSource already stages the Jupiter swap.
                            from = "",
                            to = "",
                            data = base64,
                            gas = 0,
                            value = "0",
                            gasPrice = "0",
                            swapFee = "0",
                            swapFeeTokenContract = "",
                        ),
                )
            }
            else -> throw SwapKitError.UnsupportedTxType(response.meta.txType)
        }
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
        /** Sub-provider ids (lower-cased) filtered out of `/v3/quote` results client-side. */
        private val FILTERED_PROVIDERS: Set<String> =
            setOf("thorchain", "thorchain-streaming", "mayachain", "maya")

        /**
         * Render [TokenValue] as a dot-separated decimal string suitable for
         * [SwapKitQuoteRequest.sellAmount]. Strips trailing zeros so "1.0000" → "1" — matches the
         * iOS POSIX formatter behaviour and the wire samples in the SwapKit V3 spec.
         */
        private fun formatSellAmount(tokenValue: TokenValue): String =
            tokenValue.decimal.stripTrailingZeros().toPlainString()
    }
}
