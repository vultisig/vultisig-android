package com.vultisig.wallet.data.models

import java.math.BigInteger

/**
 * One half of a symmetric add-liquidity that THORChain is still holding.
 *
 * A symmetric add (`+:ASSET:<paired-address>`) only mints LP units once both sides arrive. Until
 * then the deposited side sits as pending liquidity: no units, no outbound, and the LP record reads
 * as an empty position — which is why it needs its own type rather than a [ThorChainLpPosition]
 * with zero units. If the matching side never arrives, THORChain refunds the deposit at
 * `lastAddHeight + PendingLiquidityAgeLimit`.
 *
 * Amounts are 1e8 fixed-point integers (thornode's native scale, not asset decimals).
 */
data class ThorChainPendingLpDeposit(
    val pool: String,
    val pendingRune: BigInteger,
    val pendingAsset: BigInteger,
    /** Inbound hash of the side that already arrived. */
    val pendingTxId: String?,
    /**
     * The address THORChain expects the missing side to come from — the asset-side address when
     * RUNE is pending, the RUNE address when the asset is pending.
     */
    val pairedAddress: String?,
    /**
     * Blocks left before the pending side is refunded, or `null` when the age limit or the chain
     * height could not be read. Already clamped at zero: a deposit past its limit is awaiting the
     * refund, not overdue by a negative amount.
     */
    val blocksUntilRefund: Long?,
) {
    /** True while THORChain still holds the RUNE half — the asset half is the one missing. */
    val isRunePending: Boolean
        get() = pendingRune.signum() > 0
}
