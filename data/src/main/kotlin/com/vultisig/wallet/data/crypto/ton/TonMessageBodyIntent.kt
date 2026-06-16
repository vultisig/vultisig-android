package com.vultisig.wallet.data.crypto.ton

import java.math.BigInteger

/**
 * Structured intent decoded from a TonConnect message body (BOC).
 *
 * Addresses are returned in raw `workchain:hex` form. The UI layer converts them to the
 * user-friendly bounceable form via WalletCore's `TONAddressConverter`, keeping this decoder free
 * of JNI so it stays unit testable on the JVM.
 */
sealed interface TonMessageBodyIntent {

    /** TEP-74 jetton transfer (`0x0f8a7ea5`). */
    data class JettonTransfer(
        val queryId: BigInteger,
        val amount: BigInteger,
        val destination: String,
        val responseDestination: String?,
        val forwardTonAmount: BigInteger,
    ) : TonMessageBodyIntent

    /** TEP-62 NFT transfer (`0x5fcc3d14`). */
    data class NftTransfer(
        val queryId: BigInteger,
        val newOwner: String,
        val responseDestination: String?,
        val forwardAmount: BigInteger,
    ) : TonMessageBodyIntent

    /** TEP-74 excess-gas return notification (`0xd53276db`). */
    data class Excesses(val queryId: BigInteger) : TonMessageBodyIntent

    /**
     * A DEX swap decoded from a STON.fi v2 (`0x6664de2a`) or DeDust native (`0xea06185d`) body.
     *
     * This decoder classifies the swap purely from the signed bytes; it does NOT apply the
     * anti-spoofing router allow-list (that needs WalletCore address normalization, which is kept
     * in the runtime layer to keep this reader JNI-free). The runtime layer must gate this intent
     * before presenting it as a swap:
     * - [Provider.STONFI] + [OfferAsset.JETTON] — gate on [inputRouterAddress] ∈ STON.fi routers.
     * - [Provider.STONFI] + [OfferAsset.TON] (pTON) — gate on the outer message destination ∈
     *   STON.fi pTON wallets.
     * - [Provider.DEDUST] + [OfferAsset.TON] — gate on the outer message destination ∈ DeDust
     *   native vaults.
     *
     * Amounts are base units (nanoton for TON, jetton-decimal units otherwise) as [BigInteger],
     * since jetton supplies exceed `Long`. Addresses are raw `workchain:hex`.
     */
    data class Swap(
        val provider: Provider,
        val offerAsset: OfferAsset,
        val offerAmount: BigInteger,
        val minOut: BigInteger?,
        /**
         * Destination of the swap's output leg — the jetton wallet whose master identifies the
         * output token. Resolved to ticker/decimals/logo by the runtime layer.
         */
        val targetAddress: String?,
        /**
         * For a STON.fi jetton swap, the jetton-transfer destination (the router) that the runtime
         * layer must verify is allow-listed. `null` for pTON/DeDust swaps, which gate on the outer
         * message destination instead.
         */
        val inputRouterAddress: String?,
        val receiverAddress: String?,
        val refundAddress: String?,
        val excessesAddress: String?,
    ) : TonMessageBodyIntent

    enum class Provider {
        STONFI,
        DEDUST,
    }

    enum class OfferAsset {
        TON,
        JETTON,
    }
}
