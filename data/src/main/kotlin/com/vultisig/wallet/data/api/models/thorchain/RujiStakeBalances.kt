package com.vultisig.wallet.data.api.models.thorchain

import java.math.BigInteger

/**
 * Aggregated RUJI staking balances and rewards for a single address.
 *
 * The bonded and auto-compounding positions are independent — an account may hold either, both or
 * neither — so they are reported side by side rather than collapsed into one amount. [apr] and
 * [rewardsAmount] belong to the bonded position only; the auto-compounding one reinvests its
 * revenue into [autoCompoundAmount] and has nothing separately claimable.
 */
data class RujiStakeBalances(
    val stakeAmount: BigInteger = BigInteger.ZERO,
    val stakeTicker: String = "",
    /** Auto-compounding position valued in RUJI base units (the API's `liquidSize`). */
    val autoCompoundAmount: BigInteger = BigInteger.ZERO,
    /**
     * sRUJI receipt shares backing [autoCompoundAmount]; funds the `liquid.unbond` redemption.
     * `null` when the count could not be read — never zero in that case, or a live position would
     * look empty and its redemption would be silently disabled.
     */
    val autoCompoundShares: BigInteger? = BigInteger.ZERO,
    val rewardsAmount: BigInteger = BigInteger.ZERO,
    val rewardsTicker: String = "USDC",
    val apr: Double = 0.0,
)
