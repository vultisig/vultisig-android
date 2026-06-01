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
         * `meta.txType` discriminator for the Sui signing path. SwapKit returns a base64-encoded
         * pre-built programmable transaction block (PTB), base64-decoded into [txPayload]; the
         * signer hashes `intent_prefix || ptb` with Blake2b-32 and wraps the Ed25519 signature in
         * Sui's submit envelope. Mirrors iOS' `"SUI"`.
         */
        const val TX_TYPE_SUI = "SUI"

        /**
         * Keysign-payload discriminator for the **deposit-only** Cardano flow. SwapKit returns no
         * `tx` (null/absent), so [txPayload] is empty and the only routing info is [targetAddress];
         * the dispatcher builds a plain ADA send to it via the native Cardano path (no CBOR
         * signer). Canonical `CARDANO` case in `swapkit_swap_payload.proto`. Mirrors iOS'
         * deposit-only `.cardano` → `txType="CARDANO"`.
         */
        const val TX_TYPE_CARDANO = "CARDANO"

        /**
         * Keysign-payload discriminator for the **pre-built CBOR** Cardano flow. SwapKit returns a
         * hex-encoded unsigned CBOR transaction envelope, hex-decoded into [txPayload]; the signer
         * hashes `cbor(transaction_body)` with Blake2b-256, then splices the Ed25519 vkey witness
         * back into the envelope. Distinct from the deposit-only [TX_TYPE_CARDANO] so the
         * dispatcher routes independently — and so a vault mixing iOS and Android cosigns the same
         * flow (iOS emits `"CARDANO_PREBUILT"` here too).
         */
        const val TX_TYPE_CARDANO_PREBUILT = "CARDANO_PREBUILT"

        /**
         * Wire-level `meta.txType` alias SwapKit also emits for the Cardano CBOR response (upstream
         * switched from `"CARDANO"` to `"CBOR"` un-versioned). The quote source normalizes it onto
         * [TX_TYPE_CARDANO] / [TX_TYPE_CARDANO_PREBUILT] based on `tx` presence before it reaches
         * the keysign payload, so it is never a keysign discriminator itself.
         */
        const val TX_TYPE_CBOR = "CBOR"
    }
}
