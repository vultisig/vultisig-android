package com.vultisig.wallet.data.api.models.quotes

import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * Body sent to `POST /v3/swap`. Per the SwapKit V3 docs the caller picks a route by [routeId] and
 * supplies the wallet addresses — the upstream resolves the rest from server-side state.
 */
@Serializable
data class SwapKitSwapRequest(
    @SerialName("routeId") val routeId: String,
    @SerialName("sourceAddress") val sourceAddress: String,
    @SerialName("destinationAddress") val destinationAddress: String,
)

/**
 * Response envelope from `POST /v3/swap`. `tx` and `meta` are siblings at the root of the response
 * per SwapKit V3; `tx`'s shape varies by [SwapKitTxMeta.txType] (EVM holds
 * `from/to/data/value/gas`, Solana holds Jupiter's wire format) so it is kept as a raw
 * [JsonElement] and decoded lazily by the caller onto the matching DTO ([SwapKitEvmTx] /
 * [SwapKitSolanaTx]). Unknown `txType` values must be rejected before signing.
 */
@Serializable
data class SwapKitSwapResponseJson(
    @SerialName("swapId") val swapId: String? = null,
    // Defaults to [JsonNull] so deposit-only routes (XRP, Cardano) that omit `tx` or send `tx:
    // null` still decode — routing then lives entirely in [targetAddress]. The pre-built flows
    // (Cardano CBOR, PSBT, SUI, TRON, EVM, Solana) carry the unsigned tx here. Callers treat
    // [JsonNull] as "no tx".
    @SerialName("tx") val tx: JsonElement = JsonNull,
    @SerialName("meta") val meta: SwapKitTxMeta,
    @SerialName("targetAddress") val targetAddress: String? = null,
    @SerialName("expectedBuyAmount") val expectedBuyAmount: String? = null,
    // Max-slippage floor on the executed `/v3/swap` route. Decoded here (mirrors iOS' swap reply)
    // so the client output-deviation guard can be re-run against the SIGNED amount — a proxy that
    // degrades only the swap reply (leaving the `/v3/quote` route clean) would otherwise bypass the
    // guard, since the signed amount is scaled from this reply, not the quote route.
    @SerialName("expectedBuyAmountMaxSlippage") val expectedBuyAmountMaxSlippage: String? = null,
    // Source-chain fee breakdown on the executed route. The `type == "inbound"` entry is the
    // canonical deposit cost; read it from here (not the /v3/quote route) so a repriced fee on the
    // refreshed /v3/swap reply stays fresh. Mirrors iOS' SwapKitSwapResponse.fees.
    @SerialName("fees") val fees: List<SwapKitFee> = emptyList(),
    @SerialName("providers") val providers: List<String> = emptyList(),
    // ERC20 approval transaction for EVM routes that need an allowance. The spender is encoded in
    // [SwapKitApprovalTx.data] (`approve(spender, amount)` calldata), NOT [SwapKitApprovalTx.to]
    // (the asset contract). Used only as a fallback when [SwapKitTxMeta.approvalAddress] is absent.
    @SerialName("approvalTx") val approvalTx: SwapKitApprovalTx? = null,
    // XRP destination tag. SwapKit surfaces it (rarely) as a numeric or string-encoded value at the
    // top level; kept as a raw [JsonElement] and parsed defensively so a number-vs-string wire flip
    // doesn't break decoding. Resolution order (highest first): top-level → [SwapKitTxMeta] →
    // `?dt=`/`|` suffix on [targetAddress]. Mirrors iOS'
    // SwapKitSwapResponse.resolvedDestinationTag.
    @SerialName("destinationTag") val destinationTag: JsonElement? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("message") val message: String? = null,
)

/**
 * ERC20 approval transaction sibling of [SwapKitSwapResponseJson.tx]. [to] is the sell-token asset
 * contract (the `approve` is sent there); the actual spender lives in the `approve(spender,
 * amount)` calldata in [data]. Used only to recover the spender when
 * [SwapKitTxMeta.approvalAddress] is missing.
 */
@Serializable
data class SwapKitApprovalTx(
    @SerialName("to") val to: String? = null,
    @SerialName("data") val data: String? = null,
)

/** Discriminator metadata sitting alongside [SwapKitSwapResponseJson.tx]. */
@Serializable
data class SwapKitTxMeta(
    @SerialName("txType") val txType: String,
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("chain") val chain: String? = null,
    @SerialName("subProvider") val subProvider: String? = null,
    // EVM ERC20 allowance target (the `approve` spender). SwapKit's swap entry contract
    // (`tx.to`, which also equals the top-level [SwapKitSwapResponseJson.targetAddress]) is NOT
    // the spender — it pulls the sell token through a dedicated token-transfer proxy reported
    // here. Approve THIS address; approving `tx.to` reverts with ERC20InsufficientAllowance.
    // Null for native-source / non-EVM routes that need no approval. Mirrors iOS, which derives
    // the spender as `meta.approvalAddress`.
    @SerialName("approvalAddress") val approvalAddress: String? = null,
    // XRP destination tag carried in meta (beats the address suffix, loses to the top-level field).
    // Raw [JsonElement] for the same number-or-string defensiveness as the top-level field.
    @SerialName("destinationTag") val destinationTag: JsonElement? = null,
) {
    /**
     * Lower-cased txType used to dispatch onto an EVM or Solana signer. Computed once at
     * construction so the swap-path filter + signer-pick reads don't reallocate the string. Lives
     * in the class body (not the constructor) so kotlinx.serialization ignores it. Locale.ROOT: on
     * a Turkish device the no-arg lowercase() maps `SERIALIZED_BASE64`'s `I` to dotless `ı`, which
     * would miss the Solana dispatch arm and land every Solana route on UnsupportedTxType.
     */
    val type: String = txType.lowercase(Locale.ROOT)

    companion object {
        const val TYPE_EVM = "evm"
        const val TYPE_SOLANA = "solana"
    }
}

/** EVM `tx` shape. Trust [gas] (mirrors 1inch behaviour); re-estimate [gasPrice]. */
@Serializable
data class SwapKitEvmTx(
    @SerialName("from") val from: String? = null,
    @SerialName("to") val to: String,
    @SerialName("data") val data: String,
    @SerialName("value") val value: String = "0",
    @SerialName("gas") val gas: String? = null,
    @SerialName("gasPrice") val gasPrice: String? = null,
    @SerialName("chainId") val chainId: String? = null,
    @SerialName("nonce") val nonce: String? = null,
)

/** Solana `tx` shape — same wire format Jupiter returns today. */
@Serializable
data class SwapKitSolanaTx(
    @SerialName("message") val message: String? = null,
    @SerialName("swapTransaction") val swapTransaction: String? = null,
)
