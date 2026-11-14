package com.vultisig.wallet.data.models

import java.math.BigInteger

/**
 * One half of a symmetric add-liquidity that THORChain is still holding.
 *
 * A symmetric add (`+:ASSET:<paired-address>`) only mints LP units once both sides arrive. Until
 * then the deposited side sits as pending liquidity: no units, no outbound, and the LP record reads
 * as an empty position — which is why it needs its own type rather than a [ThorChainLpPosition]
 * with zero units. If the matching side never arrives, THORChain does not refund it: once the
 * deposit is older than `PendingLiquidityAgeLimit`, the pool's next cleanup slot commits it to the
 * pool on its own.
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
     * Estimated blocks until the pool's cleanup slot adds the pending side to the pool on its own,
     * or `null` when the age limit, the chain height or the pool's slot could not be worked out.
     */
    val blocksUntilAddedAlone: Long?,
) {
    /** True while THORChain still holds the RUNE half — the asset half is the one missing. */
    val isRunePending: Boolean
        get() = pendingRune.signum() > 0
}
