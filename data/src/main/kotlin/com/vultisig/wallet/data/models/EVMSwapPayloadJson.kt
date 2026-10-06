package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.utils.isUnsignedDecimal
import java.math.BigDecimal
import java.math.BigInteger

data class EVMSwapPayloadJson(
    val fromCoin: Coin,
    val toCoin: Coin,
    val fromAmount: BigInteger,
    val toAmountDecimal: BigDecimal,
    val quote: EVMSwapQuoteJson,
    val provider: String,
    // SwapKit `/v3/swap` swap id for EVM/Solana routes — persisted on the tx-history row so a
    // cross-chain swap's Success is gated on the destination-leg `/track` settlement. Null for
    // direct EVM aggregators (1inch / Kyber / LiFi), which settle on the source chain.
    val swapId: String? = null,
    /**
     * SwapKit sub-provider (Chainflip / NEAR / Garden) used so the done screen renders `SwapKit
     * (<sub-provider>)` instead of the collapsed canonical [provider] `"SwapKit"`. Null for 1inch /
     * Kyber / LI.FI, whose [provider] is already specific.
     *
     * In-memory only for EVM/Solana SwapKit routes: these ride on the `OneInchSwapPayload` proto,
     * which has no `sub_provider` field, so the value is NOT round-tripped through the keysign
     * proto. Peer devices in a multi-device keysign reconstruct it as `null` (their done screen
     * shows plain `SwapKit`), and any reload from the persisted proto loses it too. Native
     * (non-EVM) SwapKit routes persist correctly via `swapkit_swap_payload.proto`'s `sub_provider`
     * field. Full EVM parity needs a `sub_provider` field on `OneInchSwapPayload` (a commondata
     * proto change).
     */
    val subProvider: String? = null,
)

private const val UINT256_BITS = 256

/**
 * Reads a SwapKit EVM wire amount as vultisig-sdk's `parseNonNegativeBigInt` does: ASCII decimal
 * digits only ([isUnsignedDecimal]), and at most 2^256 - 1, the EVM word, as iOS
 * `EVMSwapTxGuard.check` requires of `tx.value`.
 *
 * @throws IllegalArgumentException when [value] is anything else.
 */
internal fun parseSwapKitDecimal(value: String, field: String): BigInteger {
    require(value.isUnsignedDecimal()) {
        "SwapKit EVM swap $field '$value' is not a plain decimal integer"
    }
    val amount = BigInteger(value)
    require(amount.bitLength() <= UINT256_BITS) {
        "SwapKit EVM swap $field '$value' exceeds uint256"
    }
    return amount
}
