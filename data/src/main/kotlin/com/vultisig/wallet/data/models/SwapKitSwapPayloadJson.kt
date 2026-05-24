package com.vultisig.wallet.data.models

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Kotlin-side mirror of `vultisig.keysign.v1.SwapKitSwapPayload` (proto field 26 in the
 * `KeysignPayload.swap_payload` oneof, landed via commondata #86). Carries SwapKit-routed swaps
 * whose wire shape doesn't fit [EVMSwapPayloadJson] — Phase 2 ships BTC PSBT here; later phases add
 * TON / TRON / SUI / Cardano.
 *
 * The flexibility lives in [txPayload] (raw bytes, opaque to commondata) and [txType] (a string
 * discriminator the keysign-side dispatcher uses to pick the right per-chain signer). New SwapKit
 * chains land without a proto bump — just a new [txType] value plus a per-chain signer on each
 * client. Peers that don't recognise a [txType] MUST fall through to an "unsupported provider" UI
 * rather than crash decode.
 */
data class SwapKitSwapPayloadJson(
    val fromCoin: Coin,
    val toCoin: Coin,
    val fromAmount: BigInteger,
    val toAmountDecimal: BigDecimal,

    /**
     * SwapKit's `meta.txType` verbatim. Drives the peer-side dispatcher:
     * - `"PSBT"` — [txPayload] is the base64-decoded BTC PSBT (BIP-174 SegWit).
     * - `"PSBT_DOGE"` / `"PSBT_BCH"` / `"PSBT_DASH"` / `"PSBT_ZEC"` — legacy / Sapling variants.
     * - `"TRON"` — [txPayload] is UTF-8 bytes of the canonical JSON of the TronWeb tx.
     * - `"TON"` — [txPayload] is UTF-8 bytes of the canonical JSON of the transfer array.
     * - `"SUI"` — [txPayload] is the base64-decoded Sui PTB.
     * - `"CARDANO"` — [txPayload] is empty; routing via [targetAddress] + [fromAmount].
     * - `"CARDANO_PREBUILT"` — [txPayload] is the raw CBOR transaction envelope.
     */
    val txType: String,

    /**
     * Raw unsigned-transaction bytes returned by `POST /v3/swap`. Bytes (not string) so binary
     * payloads (PSBT, PTB, CBOR) round-trip via protobuf without re-encoding. Object-shaped
     * payloads (TRON, TON) are JSON-encoded on the initiator and decoded on the peer.
     */
    val txPayload: ByteArray,

    /**
     * Deposit address on the source chain. For PSBT this duplicates info also encoded inside
     * [txPayload]; for deposit-only chains (Cardano) this is the only routing info available.
     */
    val targetAddress: String,

    /**
     * THORChain-style inbound vault address. Populated only for routes that go through TC-style
     * inbound monitoring. Rare in SwapKit since we filter THORChain/Maya client-side; kept for
     * forward compatibility.
     */
    val inboundAddress: String? = null,

    /**
     * Optional memo. SwapKit V3 returned null for every chain observed; kept for forward compat.
     */
    val memo: String? = null,

    /**
     * Sub-provider tag for the verify screen (`"CHAINFLIP"`, `"NEAR"`, `"GARDEN"`, `"FLASHNET"`,
     * `"HARBOR"`). Verbatim from `route.providers[0]` in the `/v3/quote` response.
     */
    val subProvider: String = "",

    /**
     * SwapKit swap identifier — persisted for tracking and analytics. Does not participate in
     * signing. NOT accepted by `POST /track` — tracking still keys off the broadcast hash + source
     * chain id.
     */
    val swapId: String = "",
) {
    // Data class auto-generates equals/hashCode/copy, but the default equals on ByteArray uses
    // reference equality. Override so two payloads with identical bytes compare equal — matters
    // for tests pinning round-trip behaviour and for the no-op write detection in caches.
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
}
