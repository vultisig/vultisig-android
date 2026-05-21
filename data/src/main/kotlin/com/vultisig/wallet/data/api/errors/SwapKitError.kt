package com.vultisig.wallet.data.api.errors

/**
 * Typed errors surfaced by the SwapKit aggregator integration. Each variant maps 1:1 to a known
 * failure mode documented at https://docs.swapkit.dev (or a client-side filter outcome) and is
 * paired with a localized message key consumed by the swap UI.
 */
sealed class SwapKitError(message: String) : Exception(message) {

    /** No SwapKit route survived the THORChain/Maya and single-hop filters. */
    class NoRoutes(message: String = "No SwapKit route available") : SwapKitError(message)

    /**
     * SwapKit returned a `tx.meta.txType` that this phase does not support (utxo/cosmos/tron/ton).
     * EVM and Solana are the only honored types in Phase 1.
     */
    class UnsupportedTxType(txType: String) : SwapKitError("Unsupported SwapKit tx type: $txType")

    /** Transport-level failure talking to the Vultisig SwapKit proxy. */
    class Network(message: String) : SwapKitError(message)

    /** SwapKit response shape did not deserialize against the expected V3 schema. */
    class Decoding(message: String) : SwapKitError(message)

    /**
     * Live `/v3/swap` price drifted further from the user's accepted `/v3/quote` than tolerated. UI
     * surfaces a confirmation dialog (matches 1inch UX).
     */
    class QuoteDeviation(message: String = "Output amount deviation too high") :
        SwapKitError(message)

    companion object {
        /** Maps SwapKit V3 documented error code strings into a typed [SwapKitError]. */
        fun fromCode(code: String?, fallbackMessage: String? = null): SwapKitError {
            val raw = code?.trim().orEmpty()
            return when (raw.lowercase()) {
                "noroutesfound",
                "no_routes_found",
                "no_routes" -> NoRoutes(fallbackMessage ?: raw)
                "outputamountdeviationtoohigh",
                "output_amount_deviation_too_high" -> QuoteDeviation(fallbackMessage ?: raw)
                else -> Network(fallbackMessage ?: raw.ifEmpty { "SwapKit request failed" })
            }
        }
    }
}
