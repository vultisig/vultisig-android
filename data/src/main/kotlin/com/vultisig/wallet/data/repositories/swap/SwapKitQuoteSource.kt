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

        return SwapQuoteResult.Evm(buildEvmQuoteFromSwapKit(swapResponse))
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
                val primary = route.primaryProviderId
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
    private fun buildEvmQuoteFromSwapKit(response: SwapKitSwapResponseJson): EVMSwapQuoteJson {
        val txType = response.meta.type
        return when (txType) {
            SwapKitTxMeta.TYPE_EVM -> {
                val evm = decode<SwapKitEvmTx>(response.tx, "evm")
                val gas =
                    evm.gas?.toLongOrNull()?.takeIf { it > 0 }
                        ?: EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT
                EVMSwapQuoteJson(
                    dstAmount = response.expectedBuyAmount.orEmpty(),
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
                    dstAmount = response.expectedBuyAmount.orEmpty(),
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
     * SwapKit asset identifier: `CHAIN.TICKER` or `CHAIN.TICKER-CONTRACT` for ERC-20-style tokens.
     */
    private fun assetIdentifier(coin: Coin): String {
        val prefix = chainPrefix(coin.chain) ?: coin.ticker
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
