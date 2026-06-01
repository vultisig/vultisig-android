package com.vultisig.wallet.data.repositories.swap

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
 * (TronWeb object, via [com.vultisig.wallet.data.chains.helpers.SwapKitTronSigner]), and SUI
 * (base64 PTB, via [com.vultisig.wallet.data.chains.helpers.SwapKitSuiSigner]) are wired today. XRP
 * is deposit-only — SwapKit returns no tx body, so the payload carries an empty [txPayload] plus
 * the deposit address + destination tag and the cosigning peer rebuilds a plain XRP Payment via the
 * existing `RippleHelper`. The remaining types (TON, CARDANO, …) still surface as
 * [SwapKitError.UnsupportedTxType] so the picker falls back to another provider rather than signing
 * garbage, until their signers land. `SOLANA` and the legacy `SERIALIZED_BASE64` discriminator are
 * aliased; SwapKit flipped them once before.
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
                )
            )

        // Distinguish "upstream returned zero routes" (NoRoutes) from "client filtering dropped
        // every candidate" (RouteFiltered). iOS produces RouteFiltered at SwapService.swift:386
        // for the same client-side gate, and the two surface different localized copies.
        if (quoteResponse.routes.isEmpty()) {
            throw SwapKitError.NoRoutes("SwapKit returned no routes for this pair")
        }

        val best = pickBestRoute(quoteResponse.routes) ?: throw SwapKitError.RouteFiltered

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
        // TODO(swapkit /track polling, later task): persist swapResponse.swapId on the resulting
        //  SwapTransaction so /track lookups can correlate cross-chain settlement back to this
        //  specific quote. The DTO already carries it; the persistence wiring lands with the
        //  tx-history Phase D work.

        // Sub-provider tag (Chainflip / NEAR Intents / Garden / Flashnet) — surfaced on the verify
        // screen so the user can reason about ETA and debug routing. Prefer
        // `swapResponse.providers[0]`
        // (the swap call's authoritative routing) and fall back to `meta.subProvider` for forward
        // compat.
        val subProvider =
            swapResponse.providers.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: swapResponse.meta.subProvider?.takeIf { it.isNotBlank() }

        // EVM + Solana ride the EVMSwapQuoteJson envelope (Solana signers pull the base64 blob from
        // `tx.data`, matching how JupiterQuoteSource stages a Solana swap). PSBT (Bitcoin) and the
        // other non-EVM txTypes can't fit that shape, so they surface as a fully-formed
        // SwapQuote.SwapKit on the Native result for a per-chain signer to consume.
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
                )
            TxKind.PSBT,
            TxKind.TRON,
            TxKind.SUI,
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
            Chain.Solana -> 9
            Chain.Sui -> 9
            Chain.Bitcoin -> 8
            Chain.Tron -> 6
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
                            // derives the spender as `meta.approvalAddress`.
                            allowanceTarget = response.meta.approvalAddress,
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
            // PSBT/TRON/SUI/XRP are dispatched to buildSwapKitNativeQuote before reaching here; the
            // arm keeps the `when` exhaustive and refuses anything that slips through.
            TxKind.PSBT,
            TxKind.TRON,
            TxKind.SUI,
            TxKind.XRP,
            TxKind.UNSUPPORTED -> throw SwapKitError.UnsupportedTxType(response.meta.txType)
        }
    }

    /**
     * Wrap a non-EVM SwapKit route (Phase 2: Bitcoin / PSBT) into a fully-formed
     * [SwapQuote.SwapKit] for a per-chain signer to consume. The unsigned-tx bytes (base64 PSBT)
     * land in [SwapKitSwapPayloadJson.txPayload]; the inbound fee is the source chain's native-unit
     * deposit cost (same surface as the EVM/Solana path). The signing side is wired separately —
     * until it (and the provider table) enable SwapKit on these chains, this path isn't reached in
     * production; it's exercised by unit tests so the data contract is pinned ahead of the signer.
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
                txType = response.meta.txType,
                txPayload = encodeNativeTxPayload(response),
                targetAddress = targetAddress,
                memo = memo,
                subProvider = subProvider.orEmpty(),
                swapId = response.swapId.orEmpty(),
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
            // XRP is deposit-only: SwapKit returns no transaction body. The cosigning peer rebuilds
            // a plain XRP Payment from the payload's targetAddress / fromAmount / memo, so there is
            // nothing to carry here.
            TxKind.XRP -> ByteArray(0)
            else -> decodeBinaryTx(response.tx)
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
        (element as? JsonPrimitive)?.content?.trim()?.toLongOrNull()

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
                        if (parts.size == 2 && parts[0] == "dt") parts[1].toLongOrNull() else null
                    }
            return bare to tag
        }
        val pipe = address.indexOf('|')
        if (pipe >= 0) {
            val tag = address.substring(pipe + 1).toLongOrNull()
            if (tag != null) return address.substring(0, pipe) to tag
        }
        return address to null
    }

    /**
     * Decode a base64 unsigned-tx blob (PSBT today; PTB / CBOR as more chains land) from `tx` into
     * raw bytes for [SwapKitSwapPayloadJson.txPayload]. V3 ships these as a top-level base64
     * [JsonPrimitive].
     */
    private fun decodeBinaryTx(element: JsonElement): ByteArray {
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
            // SwapKit has shipped both `XRP` (canonical) and `RIPPLE` for the same deposit-only
            // flow; accept both so a wire flip doesn't drop the route. Mirrors iOS.
            "xrp",
            "ripple" -> TxKind.XRP
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
        PSBT,
        TRON,
        SUI,
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
            Chain.Bitcoin -> "BTC"
            Chain.Tron -> "TRON"
            Chain.Sui -> "SUI"
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
