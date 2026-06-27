package com.vultisig.wallet.data.repositories.swap

import androidx.annotation.VisibleForTesting
import com.vultisig.wallet.data.api.errors.SwapKitError
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.api.models.quotes.SwapKitEvmTx
import com.vultisig.wallet.data.api.models.quotes.SwapKitFee
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
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.SwapQuote.Companion.expiredAfter
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Base64
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import timber.log.Timber

/**
 * SwapKit V3 quote source. Mirrors iOS' `SwapKitService.fetchBestRoute` + `buildSwapTx`:
 * 1. Short-circuit on the user's `SwapKitConfig` opt-out flag (default on) before any network I/O.
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
 * Non-EVM/non-Solana `txType`s surface as a fully-formed [SwapQuote.SwapKit] for a per-chain signer
 * to consume: PSBT (Bitcoin, via [com.vultisig.wallet.data.chains.helpers.SwapKitBtcSigner]), TRON
 * (TronWeb object, via [com.vultisig.wallet.data.chains.helpers.SwapKitTronSigner]), SUI (base64
 * PTB, via [com.vultisig.wallet.data.chains.helpers.SwapKitSuiSigner]), CARDANO (pre-built hex
 * CBOR, via [com.vultisig.wallet.data.chains.helpers.SwapKitCardanoSigner]), and TON (transfer
 * array, signed via the native [com.vultisig.wallet.data.crypto.TonHelper] path) are wired today.
 * XRP and deposit-only Cardano carry no tx body — the payload holds an empty [txPayload] plus the
 * deposit address (XRP also the destination tag) and the cosigning peer rebuilds a plain native
 * send (XRP Payment via `RippleHelper`; ADA send via `CardanoHelper`). The remaining types still
 * surface as [SwapKitError.UnsupportedTxType] so the picker falls back to another provider rather
 * than signing garbage, until their signers land. `SOLANA` and the legacy `SERIALIZED_BASE64`
 * discriminator are aliased; SwapKit flipped them once before.
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

    /**
     * Re-fetch only the source-chain inbound fee for the best SwapKit route from `POST /v3/quote`,
     * skipping the `POST /v3/swap` call a full [fetch] makes. The join flow uses this to show the
     * same non-zero swap fee as the initiator (display-only) without minting a throwaway swap route
     * — a fresh `swapId` and deposit `targetAddress` per cosigner — just to read `.fees`. Unlike
     * THORChain/Maya, SwapKit's full quote path stages a swap, so this lighter call keeps the
     * join-side re-fetch idempotent. Returns a zero-valued [TokenValue] in
     * [SwapQuoteRequest.srcToken] when the feature flag is off or no route / inbound-fee entry is
     * found, so the verify screen degrades gracefully rather than stalling.
     */
    suspend fun fetchInboundFee(request: SwapQuoteRequest): TokenValue {
        if (!config.isFeatureEnabled.first()) {
            return TokenValue(BigInteger.ZERO, request.srcToken)
        }
        val quoteResponse =
            api.quote(
                SwapKitQuoteRequest(
                    sellAsset = assetIdentifier(request.srcToken),
                    buyAsset = assetIdentifier(request.dstToken),
                    sellAmount = formatSellAmount(request.tokenValue),
                    sourceAddress = request.srcAddress.ifBlank { null },
                    destinationAddress = request.dstAddress.ifBlank { null },
                    affiliateFee = request.affiliateBps,
                )
            )
        val best =
            pickBestRoute(quoteResponse.routes)
                ?: return TokenValue(BigInteger.ZERO, request.srcToken)
        return TokenValue(inboundFeeRawUnits(request.srcToken, best.fees), request.srcToken)
    }

    private suspend fun fetchInternal(request: SwapQuoteRequest): SwapQuoteResult {
        // Feature-flag kill switch (Advanced Settings → SwapKit). Defaults to on (iOS parity) —
        // short-circuits before any /quote network I/O when the user has opted out.
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
                    // SwapKit expects slippage as a percentage, so convert from basis points (100
                    // bps = 1%). A null tolerance is omitted from the wire so NEAR Intents /
                    // Chainflip negotiate their own per-route slippage instead of being capped.
                    slippage = request.slippageBps?.let { it / 100.0 },
                )
            )

        // Distinguish "upstream returned zero routes" (NoRoutes) from "client filtering dropped
        // every candidate" (RouteFiltered). iOS produces RouteFiltered at SwapService.swift:386
        // for the same client-side gate, and the two surface different localized copies.
        if (quoteResponse.routes.isEmpty()) {
            throw SwapKitError.NoRoutes("SwapKit returned no routes for this pair")
        }

        val best = pickBestRoute(quoteResponse.routes) ?: throw SwapKitError.RouteFiltered

        // Defense-in-depth: independently reject a route whose expected buy amount has slipped
        // below
        // its own max-slippage floor, even if a buggy/compromised proxy skips the server-side
        // `outputAmountDeviationTooHigh` check. A well-formed route always has expectedBuyAmount >=
        // expectedBuyAmountMaxSlippage, so this only fires on inconsistent/manipulated data.
        assertWithinDeviationTolerance(best)

        val routeId =
            best.routeId
                ?: throw SwapKitError.NoRoutes(
                    "SwapKit route has no routeId — cannot call /v3/swap"
                )

        val swapResponse =
            api.swap(
                SwapKitSwapRequest(
                    routeId = routeId,
                    // Honor the request-scoped sender/receiver overrides (passed by
                    // SwapQuoteManager). Fall back to the token's account address only when the
                    // request did not supply one, so a Vault address override (e.g. a different
                    // signer) is never silently replaced by the token default.
                    sourceAddress = request.srcAddress.ifBlank { request.srcToken.address },
                    destinationAddress = request.dstAddress.ifBlank { request.dstToken.address },
                )
            )
        // SwapKit `/v3/swap` swap id, carried onto the resulting transaction (EVM/Solana ride the
        // EVMSwapQuoteJson envelope, which has no slot for it, so it's threaded out-of-band on
        // SwapQuoteResult.Evm). Persisted on the tx-history row so a cross-chain swap's Success is
        // gated on the destination-leg `/track` settlement rather than the source-chain deposit.
        val swapId = swapResponse.swapId?.takeIf { it.isNotBlank() }

        // Re-run the deviation guard against the SIGNED amount: the buy amount that ends up in the
        // payload is scaled from this `/v3/swap` reply, not the `/v3/quote` route checked above. A
        // proxy that degrades only the swap reply (leaving the quote route clean) would otherwise
        // slip past the guard. No-op when the reply omits either amount.
        assertWithinDeviationTolerance(
            best.copy(
                expectedBuyAmount = swapResponse.expectedBuyAmount,
                expectedBuyAmountMaxSlippage = swapResponse.expectedBuyAmountMaxSlippage,
            )
        )

        // Sub-provider tag (Chainflip / NEAR Intents / Garden / Flashnet) — surfaced on the verify
        // screen so the user can reason about ETA and debug routing. Prefer
        // `swapResponse.providers[0]`
        // (the swap call's authoritative routing) and fall back to `meta.subProvider` for forward
        // compat.
        val subProvider =
            swapResponse.providers.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: swapResponse.meta.subProvider?.takeIf { it.isNotBlank() }

        // EVM + Solana ride the EVMSwapQuoteJson envelope (Solana signers pull the base64 blob
        // from `tx.data`, matching how JupiterQuoteSource stages a Solana swap). PSBT (Bitcoin),
        // TRON and TON can't fit that shape, so they surface as a fully-formed SwapQuote.SwapKit
        // on the Native result for a per-chain signer to consume.
        return when (txTypeOf(swapResponse)) {
            TxKind.EVM,
            TxKind.SOLANA ->
                SwapQuoteResult.Evm(
                    data =
                        buildEvmQuoteFromSwapKit(
                            swapResponse,
                            request.srcToken,
                            request.dstToken,
                            best.fees,
                        ),
                    subProvider = subProvider,
                    swapId = swapId,
                )
            TxKind.PSBT,
            TxKind.TRON,
            TxKind.SUI,
            TxKind.CARDANO,
            TxKind.CARDANO_PREBUILT,
            TxKind.TON,
            TxKind.XRP ->
                SwapQuoteResult.Native(
                    buildSwapKitNativeQuote(swapResponse, request, subProvider, best.fees)
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
                // Locale.ROOT: on a Turkish device the no-arg lowercase() maps `I` → dotless `ı`,
                // so `THORCHAIN_STREAMING` would not match `thorchain_streaming` and a Thor/Maya
                // streaming route would slip the filter — stacking a second affiliate fee.
                val primary = route.primaryProviderId.lowercase(Locale.ROOT)
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
     * Client-side output-deviation guard. Rejects [route] with [SwapKitError.QuoteDeviation] when
     * its `expectedBuyAmount` has drifted below `expectedBuyAmountMaxSlippage * (1 - tolerance)`,
     * complementing the server-only `outputAmountDeviationTooHigh` enforcement. No-op when either
     * amount is missing/unparseable — the deviation can't be evaluated without both.
     */
    private fun assertWithinDeviationTolerance(route: SwapKitRoute) {
        val buyAmount =
            route.expectedBuyAmount?.let { runCatching { BigDecimal(it) }.getOrNull() } ?: return
        val maxSlippage =
            route.expectedBuyAmountMaxSlippage?.let { runCatching { BigDecimal(it) }.getOrNull() }
                ?: return
        val floor = maxSlippage.multiply(BigDecimal.ONE - OUTPUT_DEVIATION_TOLERANCE)
        if (buyAmount < floor) {
            throw SwapKitError.QuoteDeviation(
                "SwapKit buy amount $buyAmount below max-slippage floor $floor"
            )
        }
    }

    /**
     * Extract the source-chain inbound fee — the canonical SwapKit-attributed cost of executing the
     * deposit on the source chain. Prefer the `/v3/swap` response's `fees[]` (iOS parity:
     * `SwapKitService.inboundFee(from response)`, so a fee SwapKit reprices on the refreshed
     * `/v3/swap` reply stays fresh), then fall back to the `/v3/quote` route's `fees[]` when the
     * swap reply omits the inbound entry — without the fallback an EVM/Solana route whose response
     * carries no `fees[]` would silently read zero on the verify screen. Used on Solana and
     * cross-chain Chainflip / NEAR routes where the EVM gas envelope would otherwise read zero.
     * Returns `0` only when neither source has the entry, so the UI surfaces "—" rather than
     * blocking the quote.
     */
    private fun inboundFeeRawUnits(
        srcToken: Coin,
        fees: List<SwapKitFee>,
        fallbackFees: List<SwapKitFee> = emptyList(),
    ): BigInteger {
        val prefix = chainPrefix(srcToken.chain) ?: return BigInteger.ZERO
        val inbound =
            findInboundFee(fees, prefix)
                ?: findInboundFee(fallbackFees, prefix)
                ?: return BigInteger.ZERO
        val amount =
            inbound.amount?.let { runCatching { BigDecimal(it) }.getOrNull() }
                ?: return BigInteger.ZERO
        // The inbound fee is denominated in the source chain's NATIVE gas coin (e.g. `ETH.ETH`),
        // never the sell token. Scaling by `srcToken.decimal` under-counts the fee by 10^12 on an
        // ERC-20 source (USDC decimal 6 vs ETH 18), so the verify-screen Network Fee reads dust.
        // Scale by the chain's native decimals — equal to srcToken.decimal on a native-source
        // route.
        return amount.movePointRight(nativeDecimals(srcToken.chain)).toBigInteger()
    }

    private fun findInboundFee(fees: List<SwapKitFee>, prefix: String): SwapKitFee? =
        fees.firstOrNull { fee ->
            fee.type?.equals("inbound", ignoreCase = true) == true && fee.chain == prefix
        }

    /**
     * Native gas-coin decimals for a SwapKit source chain. EVM chains use 18, Solana 9, Bitcoin 8.
     * The inbound fee is always denominated in the source chain's native coin, so it is scaled by
     * these — not the sell token's decimals. Only reached for chains [chainPrefix] already maps.
     */
    private fun nativeDecimals(chain: Chain): Int =
        when (chain) {
            Chain.Solana,
            Chain.Sui,
            Chain.Ton -> 9
            // All UTXO source chains are denominated in their base unit at 8 decimals.
            Chain.Bitcoin,
            Chain.Litecoin,
            Chain.Dogecoin,
            Chain.BitcoinCash,
            Chain.Dash,
            Chain.Zcash -> 8
            Chain.Tron -> 6
            // ADA is denominated in lovelace (1 ADA = 1e6 lovelace).
            Chain.Cardano -> 6
            Chain.Ripple -> 6
            else -> 18
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
        srcToken: Coin,
        dstToken: Coin,
        routeFees: List<SwapKitFee>,
    ): EVMSwapQuoteJson {
        // SwapKit V3 reports `expectedBuyAmount` as a human-readable decimal string ("42.5"
        // USDC) per the docs. Every other Android quote source (1inch / Kyber / LiFi / Jupiter)
        // stores `EVMSwapQuoteJson.dstAmount` as a raw-units integer string, and the consumer
        // (`SwapQuoteManager`) parses it back with `toBigIntegerOrNull`. Scale at this boundary
        // so the contract stays uniform — otherwise "42.5" becomes ZERO and "2500" becomes 6
        // orders of magnitude smaller than the actual receive amount.
        val rawDstAmount = scaleDecimalToRawUnits(response.expectedBuyAmount, dstToken.decimal)
        // Inbound fee in source-chain native units — canonical SwapKit fee surface (iOS' equivalent
        // is SwapKitService.inboundFee). Embedded in `swapFee` with `swapFeeTokenContract = ""`
        // (the native-coin sentinel resolveSwapFee recognises) so the consumer reads it the same
        // way Kyber/Jupiter quote sources surface their per-leg fees.
        val inboundFee = inboundFeeRawUnits(srcToken, response.fees, routeFees).toString()
        return when (txTypeOf(response)) {
            TxKind.EVM -> {
                val evm = decode<SwapKitEvmTx>(response.tx, "evm")
                // SwapKit V3 hex-encodes the EVM tx envelope's numeric fields with a `0x` prefix
                // (wire sample: `"gas":"0x55730"` = 350_000, `"gasPrice":"0x57d86d7"`). The
                // downstream pipeline expects base-10: gas is a `Long`, and SwapQuoteManager /
                // OneInchSwap parse gasPrice + value with `toBigInteger()` — which *throws* on a
                // hex string. Decode hex → decimal here (parseEvmNumber also accepts plain decimal,
                // e.g. the `"value":"0"` this same response carries) so a valid gas estimate isn't
                // read as null and the gasPrice/value parse downstream doesn't crash.
                //
                // Refuse routes that omit `tx.gas`. Falling back to a hardcoded L1-sized constant
                // (e.g. 600_000) over-estimates by multiples on Arbitrum / Optimism / Base / BSC /
                // Polygon / Avalanche and produces a misleading Network Fee on the verify screen.
                // Throwing Decoding here lets the picker drop SwapKit and rank another aggregator
                // rather than ship a wrong number.
                val gas =
                    parseEvmNumber(evm.gas)?.takeIf { it > BigInteger.ZERO }?.toLong()
                        ?: throw SwapKitError.Decoding(
                            "SwapKit EVM tx missing gas estimate (got ${evm.gas}); refusing route"
                        )
                EVMSwapQuoteJson(
                    dstAmount = rawDstAmount,
                    tx =
                        OneInchSwapTxJson(
                            from = evm.from.orEmpty(),
                            to = evm.to,
                            // SwapKit's EVM swap entry contract (`to`, which also equals the
                            // top-level `targetAddress`) is NOT the allowance target: it pulls the
                            // sell token through a dedicated token-transfer proxy reported as
                            // `meta.approvalAddress`. The ERC20 approve must go to that proxy, else
                            // the swap reverts with ERC20InsufficientAllowance(spender=
                            // approvalAddress, allowance=0). Carry it through so the approval
                            // spender
                            // is derived from it downstream instead of from `to`. Matches iOS,
                            // which
                            // derives the spender as `meta.approvalAddress`. Falls back to the
                            // spender decoded from `approvalTx.data` (`approve(spender, amount)`
                            // calldata) when `meta.approvalAddress` is absent — never
                            // `approvalTx.to`,
                            // which is the asset contract, not the spender.
                            allowanceTarget =
                                response.meta.approvalAddress
                                    ?: decodeApprovalSpender(response.approvalTx?.data),
                            data = evm.data,
                            gas = gas,
                            // Refuse a malformed native amount rather than coercing to 0: on a
                            // value-bearing native-source swap a silent 0 would underpay the
                            // destination contract (or send nothing). gasPrice is left as a 0
                            // fallback — it is re-estimated downstream, so 0 only under-prices.
                            value =
                                (parseEvmNumber(evm.value)
                                        ?: throw SwapKitError.MalformedAmount(evm.value))
                                    .toString(),
                            gasPrice = (parseEvmNumber(evm.gasPrice) ?: BigInteger.ZERO).toString(),
                            swapFee = inboundFee,
                            swapFeeTokenContract = "",
                        ),
                )
            }
            TxKind.SOLANA -> {
                // SwapKit V3 returns the Solana `tx` as a bare base64 String (`JsonPrimitive`),
                // not an object with `swapTransaction`/`message` — those nested fields are a
                // transitional v2-era shape. Accept both with `JsonPrimitive` taking precedence.
                val base64 = solanaBase64(response.tx)
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
                            swapFee = inboundFee,
                            swapFeeTokenContract = "",
                        ),
                )
            }
            // PSBT/TRON/SUI/CARDANO/TON/XRP are dispatched to buildSwapKitNativeQuote before
            // reaching here; the arm keeps the `when` exhaustive and refuses anything that slips
            // through.
            TxKind.PSBT,
            TxKind.TRON,
            TxKind.SUI,
            TxKind.CARDANO,
            TxKind.CARDANO_PREBUILT,
            TxKind.TON,
            TxKind.XRP,
            TxKind.UNSUPPORTED -> throw SwapKitError.UnsupportedTxType(response.meta.txType)
        }
    }

    /**
     * Wrap a non-EVM SwapKit route (Bitcoin PSBT, TRON object, TON transfer array) into a
     * fully-formed [SwapQuote.SwapKit] for a per-chain signer to consume. The unsigned-tx bytes
     * land in [SwapKitSwapPayloadJson.txPayload] ([encodeNativeTxPayload]); the inbound fee is the
     * source chain's native-unit deposit cost (same surface as the EVM/Solana path).
     * [targetAddress] is the source-chain deposit address — for TON it doubles as the native
     * transfer destination ([com.vultisig.wallet.data.crypto.TonHelper] signs off `toAddress` /
     * `toAmount`).
     */
    private fun buildSwapKitNativeQuote(
        response: SwapKitSwapResponseJson,
        request: SwapQuoteRequest,
        subProvider: String?,
        routeFees: List<SwapKitFee>,
    ): SwapQuote.SwapKit {
        val srcToken = request.srcToken
        val dstToken = request.dstToken
        val toAmountDecimal = parseExpectedBuyAmount(response.expectedBuyAmount)
        val isXrp = txTypeOf(response) == TxKind.XRP
        // Deposit-only chains (XRP) route entirely on `targetAddress`, so a blank value would stage
        // an unspendable quote — refuse it rather than emit a quote that can't settle. For XRP the
        // address may carry a `?dt=`/`|` destination-tag suffix; strip it so only the bare
        // r-address
        // is used as the payment destination (the tag rides `memo`).
        val rawTargetAddress =
            response.targetAddress?.takeIf { it.isNotBlank() }
                ?: throw SwapKitError.Decoding("SwapKit non-EVM response missing targetAddress")
        val targetAddress =
            if (isXrp) extractTagSuffix(rawTargetAddress).first else rawTargetAddress
        // XRP destination tag (rare): top-level field → meta → address suffix. Carried as a numeric
        // string in `memo`; RippleHelper parses a numeric memo into the RippleOperationPayment's
        // destinationTag. Mirrors iOS' resolvedDestinationTag.
        val memo =
            if (isXrp) resolveDestinationTag(response, rawTargetAddress)?.toString() else null
        val payload =
            SwapKitSwapPayloadJson(
                fromCoin = srcToken,
                toCoin = dstToken,
                fromAmount = request.tokenValue.value,
                toAmountDecimal = toAmountDecimal,
                // Store the canonical discriminator, NOT the raw wire value: SwapKit ships XRP as
                // either `XRP` or `RIPPLE`, and Cardano as `CARDANO` or `CBOR` (and split by `tx`
                // presence into deposit-only vs pre-built), so persisting `meta.txType` verbatim
                // would make the signing-side gate reject those routes. Mirrors iOS, which
                // normalises the payload txType in its buildSwapKit*Payload builders.
                txType = canonicalTxType(response, srcToken.chain),
                txPayload = encodeNativeTxPayload(response),
                targetAddress = targetAddress,
                memo = memo,
                subProvider = subProvider.orEmpty(),
                swapId = response.swapId?.takeIf { it.isNotBlank() }.orEmpty(),
            )
        return SwapQuote.SwapKit(
            expectedDstValue =
                TokenValue(
                    toAmountDecimal.movePointRight(dstToken.decimal).toBigInteger(),
                    dstToken,
                ),
            // Source-chain native deposit fee (BTC 8dp), same surface as the EVM/Solana inbound
            // fee.
            fees = TokenValue(inboundFeeRawUnits(srcToken, response.fees, routeFees), srcToken),
            expiredAt = Clock.System.now() + expiredAfter,
            data = payload,
            subProvider = subProvider,
        )
    }

    /**
     * Encode `tx` into the raw [SwapKitSwapPayloadJson.txPayload] bytes the per-chain signer
     * consumes. The wire shape depends on `meta.txType`:
     * - PSBT → a top-level base64 string ([decodeBinaryTx]).
     * - TRON → a TronWeb-shaped JSON object (`{txID, raw_data, raw_data_hex, …}`). We UTF-8 encode
     *   the object verbatim so the cosigning peer reconstructs it and [SwapKitTronSigner] can pull
     *   `raw_data_hex`. Matches iOS' `buildSwapKitTronPayload`.
     * - TON → a `[{address, amount}]` transfer array. We decode, validate every amount is an
     *   integer (so a malformed transfer is rejected at quote time, not keysign), then re-encode
     *   the validated transfers canonically. Matches iOS' `buildSwapKitTonPayload` byte-for-byte.
     */
    private fun encodeNativeTxPayload(response: SwapKitSwapResponseJson): ByteArray =
        when (txTypeOf(response)) {
            TxKind.TRON -> {
                val obj =
                    response.tx as? JsonObject
                        ?: throw SwapKitError.Decoding("SwapKit TRON tx is not a JSON object")
                // Validate raw_data_hex is a non-blank string here (not just present) so a null /
                // non-string / empty value is rejected in the decoding path — letting quote
                // selection fall back to another provider — instead of surfacing only at keysign in
                // SwapKitTronSigner.
                (obj["raw_data_hex"] as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.content
                    ?.takeIf { it.isNotBlank() }
                    ?: throw SwapKitError.Decoding("SwapKit TRON tx is missing raw_data_hex")
                json.encodeToString(JsonObject.serializer(), obj).encodeToByteArray()
            }
            // Deposit-only Cardano: no `tx` to sign — the keysign side builds a plain ADA send to
            // targetAddress from the blockChainSpecific. Empty payload, mirroring iOS' `.cardano`.
            TxKind.CARDANO -> ByteArray(0)
            // Pre-built Cardano: SwapKit returns the tx as a hex-encoded CBOR string (not base64),
            // mirroring iOS' `Data(hexString:)` path. Decode hex into the raw CBOR envelope bytes
            // the signer hashes and re-frames.
            TxKind.CARDANO_PREBUILT -> decodeHexTx(response.tx)
            TxKind.TON -> {
                val transfers = decodeTonTransfers(response.tx)
                if (transfers.isEmpty()) {
                    throw SwapKitError.Decoding("SwapKit TON tx is an empty transfer array")
                }
                // Reject any non-integer amount up front (nano-TON is an integer) so a malformed
                // transfer falls back to another provider here rather than failing at keysign.
                transfers.forEachIndexed { index, transfer ->
                    transfer.amount.toBigIntegerOrNull()
                        ?: throw SwapKitError.Decoding(
                            "SwapKit TON transfer[$index] amount is not an integer: ${transfer.amount}"
                        )
                }
                json.encodeToString(transfers).encodeToByteArray()
            }
            // XRP is deposit-only: SwapKit returns no transaction body. The cosigning peer rebuilds
            // a plain XRP Payment from the payload's targetAddress / fromAmount / memo, so there is
            // nothing to carry here.
            TxKind.XRP -> ByteArray(0)
            else -> decodeBinaryTx(response.tx)
        }

    /**
     * Decode a hex-encoded unsigned-tx blob (Cardano CBOR) from `tx` into raw bytes for
     * [SwapKitSwapPayloadJson.txPayload]. Tolerates an optional `0x` prefix. Rejects odd-length /
     * non-hex / empty payloads so an unsignable blob is dropped here rather than at sign time.
     */
    private fun decodeHexTx(element: JsonElement?): ByteArray {
        val hex =
            (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
                ?: throw SwapKitError.Decoding("SwapKit Cardano tx is not a hex string")
        val stripped = hex.removePrefix("0x").removePrefix("0X")
        if (stripped.isEmpty() || stripped.length % 2 != 0) {
            throw SwapKitError.Decoding("SwapKit Cardano tx is not valid hex")
        }
        val decoded =
            runCatching {
                    ByteArray(stripped.length / 2) {
                        stripped.substring(it * 2, it * 2 + 2).toInt(16).toByte()
                    }
                }
                .getOrElse {
                    throw SwapKitError.Decoding("SwapKit Cardano tx is not valid hex", it)
                }
        return decoded
    }

    /**
     * Map the route onto the canonical [SwapKitSwapPayloadJson] txType discriminator the signing
     * side dispatches on — independent of the raw `meta.txType` casing/aliasing (e.g. SwapKit's
     * `XRP` vs `RIPPLE`). Only the native (non-EVM/Solana) kinds reach [buildSwapKitNativeQuote].
     */
    private fun canonicalTxType(response: SwapKitSwapResponseJson, srcChain: Chain): String =
        when (txTypeOf(response)) {
            // SwapKit ships a single `psbt` wire type for every UTXO source, but the signing path
            // differs: BTC/LTC are segwit (BIP-143), DOGE/BCH/DASH are legacy P2PKH, ZEC is
            // Sapling-v4 (ZIP-243). Split by the source chain into the per-chain discriminators the
            // dispatcher routes on — matching iOS, which derives the same `PSBT_*` strings from the
            // sell asset.
            TxKind.PSBT -> psbtTxTypeForChain(srcChain)
            TxKind.TRON -> SwapKitSwapPayloadJson.TX_TYPE_TRON
            TxKind.SUI -> SwapKitSwapPayloadJson.TX_TYPE_SUI
            TxKind.TON -> SwapKitSwapPayloadJson.TX_TYPE_TON
            TxKind.XRP -> SwapKitSwapPayloadJson.TX_TYPE_XRP
            // Cardano's wire `CARDANO`/`CBOR` is split by `tx` presence into the deposit-only
            // CARDANO and the pre-built CARDANO_PREBUILT so the cosigning peer (incl. iOS, which
            // emits the same strings) dispatches correctly.
            TxKind.CARDANO -> SwapKitSwapPayloadJson.TX_TYPE_CARDANO
            TxKind.CARDANO_PREBUILT -> SwapKitSwapPayloadJson.TX_TYPE_CARDANO_PREBUILT
            // EVM/Solana ride the EVM envelope and never reach the native-quote builder; fall back
            // to the raw value so an unexpected kind still surfaces a descriptive
            // UnsupportedTxType.
            else -> response.meta.txType
        }

    /**
     * Map a UTXO source chain onto the per-chain PSBT txType discriminator. BTC/LTC share the
     * segwit `PSBT`; DOGE/BCH/DASH/ZEC get their own legacy / Sapling discriminators. An unmapped
     * chain (shouldn't happen — [chainPrefix] gates the route) falls back to the segwit `PSBT`.
     */
    private fun psbtTxTypeForChain(chain: Chain): String =
        when (chain) {
            Chain.Dogecoin -> SwapKitSwapPayloadJson.TX_TYPE_PSBT_DOGE
            Chain.BitcoinCash -> SwapKitSwapPayloadJson.TX_TYPE_PSBT_BCH
            Chain.Dash -> SwapKitSwapPayloadJson.TX_TYPE_PSBT_DASH
            Chain.Zcash -> SwapKitSwapPayloadJson.TX_TYPE_PSBT_ZEC
            else -> SwapKitSwapPayloadJson.TX_TYPE_PSBT
        }

    /**
     * Resolve the XRP destination tag, highest priority first: top-level `destinationTag` → meta →
     * `?dt=`/`|` suffix on the (raw, un-stripped) target address. Each numeric source is parsed
     * defensively (number or string). Mirrors iOS' `resolvedDestinationTag`.
     */
    private fun resolveDestinationTag(
        response: SwapKitSwapResponseJson,
        rawTargetAddress: String,
    ): Long? =
        tagLongOrNull(response.destinationTag)
            ?: tagLongOrNull(response.meta.destinationTag)
            ?: extractTagSuffix(rawTargetAddress).second

    /** Parse a destination tag that may arrive as a JSON number or a numeric string. */
    private fun tagLongOrNull(element: JsonElement?): Long? =
        (element as? JsonPrimitive)?.content?.destinationTagOrNull()

    /**
     * Parse a destination-tag string, rejecting anything outside XRP's uint32 `DestinationTag`
     * range. Signed `toLongOrNull` would otherwise let a negative or `>2^32-1` wire value flow
     * through `memo` onto the uint32 field; iOS rejects the same with `UInt64(...)` and emits no
     * tag.
     */
    private fun String.destinationTagOrNull(): Long? =
        trim().toLongOrNull()?.takeIf { it in 0..0xFFFFFFFFL }

    /**
     * Split an XRP target address into its bare r-address and an optional destination tag carried
     * as a `?dt=N` query parameter or a `|N` suffix. Returns the address verbatim with a null tag
     * when neither form is present. Mirrors iOS' `extractTagSuffix`.
     */
    private fun extractTagSuffix(address: String): Pair<String, Long?> {
        val q = address.indexOf('?')
        if (q >= 0) {
            val bare = address.substring(0, q)
            val tag =
                address
                    .substring(q + 1)
                    .split('&')
                    .filter { it.isNotEmpty() }
                    .firstNotNullOfOrNull { pair ->
                        val parts = pair.split('=', limit = 2)
                        if (parts.size == 2 && parts[0] == "dt") parts[1].destinationTagOrNull()
                        else null
                    }
            return bare to tag
        }
        val pipe = address.indexOf('|')
        if (pipe >= 0) {
            // A `|` unambiguously announces a destination tag. If the suffix isn't a valid uint32
            // tag the response is malformed — fail the route rather than pass `rAddr|abc` through
            // as the XRP destination (unsignable) or silently drop a tag the deposit may require to
            // be credited.
            val tag =
                address.substring(pipe + 1).destinationTagOrNull()
                    ?: throw SwapKitError.Decoding(
                        "SwapKit XRP targetAddress has an invalid destination-tag suffix"
                    )
            return address.substring(0, pipe) to tag
        }
        return address to null
    }

    /**
     * Decode SwapKit's TON `tx` (`[{address, amount}]`) into [SwapKitTonTransfer]s, wrapping any
     * shape mismatch as [SwapKitError.Decoding] so the picker can fall back to another provider.
     */
    private fun decodeTonTransfers(element: JsonElement): List<SwapKitTonTransfer> =
        try {
            json.decodeFromJsonElement<List<SwapKitTonTransfer>>(element)
        } catch (e: Exception) {
            throw SwapKitError.Decoding("Failed to decode SwapKit TON transfer array", cause = e)
        }

    /**
     * Decode a base64 unsigned-tx blob (PSBT today; PTB / CBOR as more chains land) from `tx` into
     * raw bytes for [SwapKitSwapPayloadJson.txPayload]. V3 ships these as a top-level base64
     * [JsonPrimitive].
     */
    private fun decodeBinaryTx(element: JsonElement?): ByteArray {
        val base64 =
            (element as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw SwapKitError.Decoding("SwapKit non-EVM tx is not a base64 string")
        val decoded =
            runCatching { Base64.getDecoder().decode(base64) }
                .getOrElse {
                    throw SwapKitError.Decoding("SwapKit non-EVM tx is not valid base64", it)
                }
        // An empty (or whitespace-only) base64 decodes to zero bytes — refuse it rather than stage
        // an unsignable empty payload that would only surface as a failure at sign time.
        if (decoded.isEmpty()) {
            throw SwapKitError.Decoding("SwapKit non-EVM tx payload is empty")
        }
        return decoded
    }

    /**
     * Map SwapKit's `meta.txType` (raw upstream casing) onto the internal dispatch kind. Accepts
     * both `SOLANA` and `SERIALIZED_BASE64` for the Solana branch — SwapKit flipped that
     * discriminator once before (per iOS commit `382b28f5f`), so the source is permissive.
     */
    private fun txTypeOf(response: SwapKitSwapResponseJson): TxKind =
        when (response.meta.type) {
            "evm" -> TxKind.EVM
            "solana",
            "serialized_base64" -> TxKind.SOLANA
            "psbt" -> TxKind.PSBT
            "tron" -> TxKind.TRON
            "sui" -> TxKind.SUI
            // SwapKit emits both "CARDANO" and the "CBOR" alias for either Cardano shape. The shape
            // is told apart by `tx`, not the discriminator string: a present `tx` is a pre-built
            // CBOR envelope to sign; a null/absent `tx` is the deposit-only flow (route entirely on
            // targetAddress). Mirrors iOS' `.cardanoPrebuilt` vs `.cardano` decode split.
            "cardano",
            "cbor" -> if (response.tx.isTxPresent()) TxKind.CARDANO_PREBUILT else TxKind.CARDANO
            "ton" -> TxKind.TON
            // SwapKit has shipped both `XRP` (canonical) and `RIPPLE` for the same deposit-only
            // flow; accept both so a wire flip doesn't drop the route. Mirrors iOS.
            "xrp",
            "ripple" -> TxKind.XRP
            else -> TxKind.UNSUPPORTED
        }

    /**
     * True when `tx` carries a payload — absent (`null`) and JSON `null` ([JsonNull]) both fail.
     */
    private fun JsonElement?.isTxPresent(): Boolean = this != null && this !is JsonNull

    /**
     * Pull the Solana base64 swap blob out of `tx`. V3 ships it as a top-level [JsonPrimitive];
     * fall back to the legacy [SwapKitSolanaTx] object shape so a transitional response (or a test
     * fixture using either form) is decoded safely.
     */
    private fun solanaBase64(element: JsonElement?): String {
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
        PSBT,
        TRON,
        SUI,
        CARDANO,
        CARDANO_PREBUILT,
        TON,
        XRP,
        UNSUPPORTED,
    }

    /**
     * Convert SwapKit's human-readable `expectedBuyAmount` (decimal string, e.g. "42.5") into the
     * raw-units integer string the rest of the swap pipeline expects. Throws
     * [SwapKitError.MalformedAmount] when the upstream value is missing or unparseable so the user
     * sees the typed "Invalid amount" message instead of a generic "Could not parse response".
     */
    private fun scaleDecimalToRawUnits(decimalString: String?, decimals: Int): String =
        parseExpectedBuyAmount(decimalString).movePointRight(decimals).toBigInteger().toString()

    /**
     * Parse SwapKit's human-readable `expectedBuyAmount` (decimal string, e.g. "42.5") into a
     * [BigDecimal], or throw [SwapKitError.MalformedAmount] when it's missing/unparseable so the
     * user sees the typed "Invalid amount" message. Shared by the EVM/Solana raw-units scaling
     * ([scaleDecimalToRawUnits]) and the native-quote builder so the parse lives in one place.
     */
    private fun parseExpectedBuyAmount(decimalString: String?): BigDecimal =
        decimalString?.let { runCatching { BigDecimal(it) }.getOrNull() }
            // `raw` is surfaced to the user via `swapkit_error_malformed_amount`; pass empty for
            // the
            // null case so the developer-only `(missing)` sentinel never leaks into the form error.
            // The UI maps blank raw onto the generic decoding copy.
            ?: throw SwapKitError.MalformedAmount(raw = decimalString.orEmpty())

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
        val ticker = swapSymbol(coin)
        return if (coin.isNativeToken || coin.contractAddress.isBlank()) {
            "$prefix.$ticker"
        } else {
            "$prefix.$ticker-${coin.contractAddress}"
        }
    }

    /**
     * SwapKit lists the native TON asset as "TON"; the Toncoin → GRAM rebrand (#4984) renamed only
     * the display ticker, so a GRAM-ticker'd native must still swap as TON. Mirrors iOS'
     * `SwapKitService.swapSymbol(chain:ticker:isNativeToken:)`.
     */
    @VisibleForTesting
    internal fun swapSymbol(coin: Coin): String =
        if (coin.chain == Chain.Ton && coin.isNativeToken) "TON" else coin.ticker

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
            Chain.Bitcoin -> "BTC"
            Chain.Litecoin -> "LTC"
            Chain.Dogecoin -> "DOGE"
            Chain.BitcoinCash -> "BCH"
            Chain.Dash -> "DASH"
            Chain.Zcash -> "ZEC"
            Chain.Tron -> "TRON"
            Chain.Sui -> "SUI"
            Chain.Cardano -> "ADA"
            Chain.Ton -> "TON"
            Chain.Ripple -> "XRP"
            else -> null
        }

    /**
     * Parse a SwapKit EVM tx numeric field (gas / gasPrice / value). SwapKit V3 hex-encodes these
     * with a `0x` prefix (e.g. `0x55730`), but a few arrive as a plain base-10 string (`value` is
     * often `"0"`). Decode either form to a [BigInteger]; returns null on a missing/garbage value
     * so the caller can apply its own fallback (refuse the route for gas, zero for gasPrice/value).
     */
    private fun parseEvmNumber(raw: String?): BigInteger? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
                if (trimmed.startsWith("0x", ignoreCase = true)) {
                    BigInteger(trimmed.substring(2), 16)
                } else {
                    BigInteger(trimmed)
                }
            }
            .getOrNull()
    }

    /**
     * Decode the ERC20 `approve` spender from [data] (`approve(spender, amount)` calldata): the
     * 4-byte selector `095ea7b3`, a 32-byte left-padded spender word, then a 32-byte amount.
     * Returns the `0x`-prefixed 20-byte spender, or null when the calldata is absent, has the wrong
     * selector, is too short, or decodes to the zero address. Never returns `approvalTx.to` — that
     * is the asset contract, not the spender.
     */
    private fun decodeApprovalSpender(data: String?): String? {
        val hex = data?.trim()?.removePrefix("0x")?.removePrefix("0X") ?: return null
        // 8 hex selector + 64 hex spender word required; amount word may follow.
        if (hex.length < APPROVE_SELECTOR.length + 64) return null
        if (!hex.startsWith(APPROVE_SELECTOR, ignoreCase = true)) return null
        val spender =
            hex.substring(APPROVE_SELECTOR.length, APPROVE_SELECTOR.length + 64).takeLast(40)
        if (!spender.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
        if (spender.all { it == '0' }) return null
        return "0x$spender"
    }

    private inline fun <reified T> decode(element: JsonElement?, label: String): T {
        val el = element ?: throw SwapKitError.Decoding("SwapKit $label tx payload is missing")
        return try {
            json.decodeFromJsonElement<T>(el)
        } catch (e: Exception) {
            throw SwapKitError.Decoding(
                message = "Failed to decode SwapKit $label tx payload",
                cause = e,
            )
        }
    }

    private companion object {
        /**
         * Tolerance for the client-side output-deviation guard ([assertWithinDeviationTolerance]).
         * A well-formed route has `expectedBuyAmount >= expectedBuyAmountMaxSlippage`, so this
         * small buffer below the max-slippage floor only rejects inconsistent/manipulated data
         * while tolerating benign rounding.
         */
        private val OUTPUT_DEVIATION_TOLERANCE: BigDecimal = BigDecimal("0.01")

        /** ERC20 `approve(address,uint256)` 4-byte function selector (hex, no `0x`). */
        private const val APPROVE_SELECTOR = "095ea7b3"

        /**
         * Sub-provider ids (lower-cased, underscored — matches the wire format of SwapKit's
         * `/providers` and `route.providers[]` entries: `THORCHAIN`, `THORCHAIN_STREAMING`,
         * `MAYACHAIN`, `MAYACHAIN_STREAMING`). Earlier dashed / 4-letter variants (`maya`,
         * `thorchain-streaming`) silently let the streaming sub-providers through, which would
         * stack a SwapKit affiliate fee on top of Vultisig's native Thor/Maya integrations.
         */
        private val FILTERED_PROVIDERS: Set<String> =
            setOf("thorchain", "thorchain_streaming", "mayachain", "mayachain_streaming")

        /**
         * Render [TokenValue] as a dot-separated decimal string suitable for
         * [SwapKitQuoteRequest.sellAmount]. Strips trailing zeros so "1.0000" → "1" — matches the
         * iOS POSIX formatter behaviour and the wire samples in the SwapKit V3 spec.
         */
        private fun formatSellAmount(tokenValue: TokenValue): String =
            tokenValue.decimal.stripTrailingZeros().toPlainString()
    }
}
