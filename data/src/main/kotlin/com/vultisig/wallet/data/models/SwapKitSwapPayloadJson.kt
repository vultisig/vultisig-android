package com.vultisig.wallet.data.models

import java.math.BigDecimal
import java.math.BigInteger

/**
 * SwapKit-routed swap whose wire shape doesn't fit [EVMSwapPayloadJson] — BTC PSBT, TON transfer
 * array, ADA CBOR, TRON object, SUI PTB. [txType] is the discriminator the signing-side dispatcher
 * uses to pick the per-chain signer. Peers that don't recognise a [txType] MUST fall through to an
 * "unsupported provider" UI rather than crash.
 */
data class SwapKitSwapPayloadJson(
    val fromCoin: Coin,
    val toCoin: Coin,
    val fromAmount: BigInteger,
    val toAmountDecimal: BigDecimal,
    /** `meta.txType` verbatim — `PSBT`, `TON`, `SUI`, `CARDANO`, `CARDANO_PREBUILT`, `TRON`, … */
    val txType: String,
    /** Unsigned-tx bytes; binary for PSBT/PTB/CBOR, UTF-8 canonical JSON for TON/TRON. */
    val txPayload: ByteArray,
    /** Source-chain deposit address. For deposit-only chains (Cardano) the only routing info. */
    val targetAddress: String,
    /** THORChain-style inbound vault — rare since we filter Thor/Maya client-side. */
    val inboundAddress: String? = null,
    val memo: String? = null,
    /** Sub-protocol tag from `route.providers[0]` — `CHAINFLIP`, `NEAR`, `GARDEN`, … */
    val subProvider: String = "",
    /** Persisted for analytics; not accepted by `/track` (keys off broadcast hash + chain id). */
    val swapId: String = "",
) {
    // Override equals/hashCode so the ByteArray field compares by content (default is reference).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SwapKitSwapPayloadJson
        return fromCoin == other.fromCoin &&
            toCoin == other.toCoin &&
            fromAmount == other.fromAmount &&
            toAmountDecimal.compareTo(other.toAmountDecimal) == 0 &&
            txType == other.txType &&
            txPayload.contentEquals(other.txPayload) &&
            targetAddress == other.targetAddress &&
            inboundAddress == other.inboundAddress &&
            memo == other.memo &&
            subProvider == other.subProvider &&
            swapId == other.swapId
    }

    override fun hashCode(): Int {
        var result = fromCoin.hashCode()
        result = 31 * result + toCoin.hashCode()
        result = 31 * result + fromAmount.hashCode()
        result = 31 * result + toAmountDecimal.stripTrailingZeros().hashCode()
        result = 31 * result + txType.hashCode()
        result = 31 * result + txPayload.contentHashCode()
        result = 31 * result + targetAddress.hashCode()
        result = 31 * result + (inboundAddress?.hashCode() ?: 0)
        result = 31 * result + (memo?.hashCode() ?: 0)
        result = 31 * result + subProvider.hashCode()
        result = 31 * result + swapId.hashCode()
        return result
    }

    companion object {
        /** `meta.txType` discriminator for the Bitcoin PSBT signing path. */
        const val TX_TYPE_PSBT = "PSBT"

        /**
         * `meta.txType` discriminator for the TRON signing path. SwapKit returns a TronWeb-shaped
         * object (`txID` / `raw_data` / `raw_data_hex`) JSON-encoded into [txPayload]; the signer
         * hashes `raw_data_hex` and re-assembles the broadcast envelope. Mirrors iOS' `"TRON"`.
         */
        const val TX_TYPE_TRON = "TRON"

        /**
         * `meta.txType` discriminator for the TON signing path. SwapKit returns `tx` as a
         * `[{address, amount}]` array (raw nano-TON amounts) JSON-encoded into [txPayload]. A TON
         * SwapKit swap is a plain native transfer to the deposit [targetAddress] — routing is by
         * the deposit address itself (Chainflip / NEAR), no memo — so signing reuses the existing
         * [com.vultisig.wallet.data.crypto.TonHelper] native path off `toAddress` / `toAmount`
         * rather than a dedicated signer. Mirrors iOS' `"TON"`.
         */
        const val TX_TYPE_TON = "TON"
    }
}
