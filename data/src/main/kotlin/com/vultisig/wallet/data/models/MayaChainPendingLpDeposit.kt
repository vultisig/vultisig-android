package com.vultisig.wallet.data.models

import java.math.BigInteger

/**
 * One half of a symmetric MayaChain add-liquidity that MayaChain is still holding.
 *
 * Like THORChain's ([ThorChainPendingLpDeposit]), a paired add mints no LP units until both sides
 * arrive, so the record reads as an empty position until then. If the matching side never arrives,
 * MayaChain refunds the deposit at `lastAddHeight + PendingLiquidityAgeLimit`.
 *
 * [pendingCacao] is in CACAO's 1e10 base units; [pendingAsset] in MayaChain's 1e8 fixed point.
 */
data class MayaChainPendingLpDeposit(
    val pool: String,
    val pendingCacao: BigInteger,
    val pendingAsset: BigInteger,
    /** Inbound hash of the side that already arrived. */
    val pendingTxId: String?,
    /**
     * The address MayaChain expects the missing side to come from — the asset-side address when
     * CACAO is pending, the CACAO address when the asset is pending.
     */
    val pairedAddress: String?,
    /** Blocks left before the pending side is refunded, clamped at zero, or `null` when unknown. */
    val blocksUntilRefund: Long?,
) {
    /** True while MayaChain still holds the CACAO half — the asset half is the one missing. */
    val isCacaoPending: Boolean
        get() = pendingCacao.signum() > 0
}
