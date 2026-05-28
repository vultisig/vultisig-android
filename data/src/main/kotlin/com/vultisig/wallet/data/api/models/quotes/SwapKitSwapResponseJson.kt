package com.vultisig.wallet.data.api.models.quotes

import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
    @SerialName("tx") val tx: JsonElement,
    @SerialName("meta") val meta: SwapKitTxMeta,
    @SerialName("targetAddress") val targetAddress: String? = null,
    @SerialName("expectedBuyAmount") val expectedBuyAmount: String? = null,
    // Source-chain fee breakdown on the executed route. The `type == "inbound"` entry is the
    // canonical deposit cost; read it from here (not the /v3/quote route) so a repriced fee on the
    // refreshed /v3/swap reply stays fresh. Mirrors iOS' SwapKitSwapResponse.fees.
    @SerialName("fees") val fees: List<SwapKitFee> = emptyList(),
    @SerialName("providers") val providers: List<String> = emptyList(),
    @SerialName("error") val error: String? = null,
    @SerialName("message") val message: String? = null,
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
