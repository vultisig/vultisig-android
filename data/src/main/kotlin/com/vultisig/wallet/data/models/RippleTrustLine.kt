package com.vultisig.wallet.data.models

import java.math.BigInteger

/**
 * Limit signed when opening a trust line. XRPL has no "unlimited", so this is one quadrillion
 * tokens: one significant digit, so it stays exactly representable.
 */
val RIPPLE_TRUST_LINE_LIMIT: BigInteger = BigInteger.TEN.pow(RIPPLE_TOKEN_DECIMALS + 15)

/** Current mainnet `reserve_inc`, used only when the live value cannot be read. */
val RIPPLE_SEED_OWNER_RESERVE_DROPS: BigInteger = BigInteger.valueOf(200_000)

/**
 * What opening one trust line costs. [spendableDrops] is the XRP balance, which
 * `RippleApi.getBalance` has already netted the account's current reserve out of.
 */
data class RippleTrustLineQuote(
    val ownerReserveDrops: BigInteger,
    val feeDrops: BigInteger,
    val spendableDrops: BigInteger,
) {
    /** The reserve is immobilised rather than spent, but it leaves the balance as the fee does. */
    val totalCostDrops: BigInteger
        get() = ownerReserveDrops + feeDrops

    val remainingSpendableDrops: BigInteger
        get() = (spendableDrops - totalCostDrops).max(BigInteger.ZERO)

    /** Below this the ledger answers `tecNO_LINE_INSUF_RESERVE` with the fee already burned. */
    val isAffordable: Boolean
        get() = spendableDrops >= totalCostDrops
}
