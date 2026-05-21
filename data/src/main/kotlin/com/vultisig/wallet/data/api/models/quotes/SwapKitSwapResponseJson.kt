package com.vultisig.wallet.data.api.models.quotes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Body sent to `POST /v3/swap` — echoes the winning route returned by `/v3/quote`. */
@Serializable
data class SwapKitSwapRequest(
    @SerialName("route") val route: SwapKitRoute,
    @SerialName("sourceAddress") val sourceAddress: String,
    @SerialName("destinationAddress") val destinationAddress: String,
)

/**
 * Response envelope from `POST /v3/swap`. SwapKit's `tx` payload is polymorphic — the
 * [SwapKitTxMeta.txType] discriminator drives which downstream signer (EVM, Solana, …) the client
 * builds for. Unknown `txType` values must be rejected before signing.
 */
@Serializable
data class SwapKitSwapResponseJson(
    @SerialName("tx") val tx: SwapKitTx,
    @SerialName("expectedBuyAmount") val expectedBuyAmount: String? = null,
    @SerialName("providers") val providers: List<String> = emptyList(),
    @SerialName("error") val error: String? = null,
    @SerialName("message") val message: String? = null,
)

@Serializable
data class SwapKitTx(
    @SerialName("meta") val meta: SwapKitTxMeta,
    /**
     * Raw transaction payload. Shape varies by [SwapKitTxMeta.txType]: EVM holds
     * `from`/`to`/`data`/`value`/`gas`, Solana holds a base64 `message` matching Jupiter's wire
     * format. Decoded lazily by the caller into the matching DTO ([SwapKitEvmTx] /
     * [SwapKitSolanaTx]).
     */
    @SerialName("payload") val payload: JsonElement,
)

@Serializable
data class SwapKitTxMeta(
    @SerialName("txType") val txType: String,
    @SerialName("chain") val chain: String? = null,
    @SerialName("subProvider") val subProvider: String? = null,
) {
    /** Lower-cased txType used to dispatch onto an EVM or Solana signer. */
    val type: String
        get() = txType.lowercase()

    companion object {
        const val TYPE_EVM = "evm"
        const val TYPE_SOLANA = "solana"
    }
}

/** EVM `tx.payload` shape. Trust [gas] (mirrors 1inch behaviour); re-estimate [gasPrice]. */
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

/** Solana `tx.payload` shape — same wire format Jupiter returns today. */
@Serializable
data class SwapKitSolanaTx(
    @SerialName("message") val message: String? = null,
    @SerialName("swapTransaction") val swapTransaction: String? = null,
)
