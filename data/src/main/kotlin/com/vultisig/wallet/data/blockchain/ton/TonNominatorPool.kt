package com.vultisig.wallet.data.blockchain.ton

import java.math.BigInteger

/**
 * Mechanics for native TON nominator-pool staking, verified against live on-chain data.
 *
 * Deposits and withdrawals are plain TON transfers carrying a **text comment**, but the comment
 * word differs per pool implementation. These must be exact — a wrong comment is rejected on-chain
 * (e.g. the stale tonwhales README `"Stake"` fails with exit code 72), and a deposit sent without
 * the bounce flag that the pool rejects is absorbed (lost) rather than returned.
 */
object TonNominatorPool {

    /** tonwhales/ton-nominators implementation. Accessible (min ~50 TON). */
    const val IMPLEMENTATION_WHALES = "whales"

    /** Standard ton-blockchain/nominator-pool implementation (needs ~300k TON, rarely usable). */
    const val IMPLEMENTATION_TF = "tf"

    /** Tonstakers liquid staking — a different jetton/op-code mechanism; not a nominator pool. */
    const val IMPLEMENTATION_LIQUID_TF = "liquidTF"

    /**
     * Processing commission a deposit must clear on top of the pool's `min_stake`; depositing
     * exactly the minimum is rejected. ~1 TON.
     */
    val DEPOSIT_COMMISSION: BigInteger = BigInteger.valueOf(1_000_000_000L)

    /**
     * Amount carried by a withdraw message. The pool returns the full staked balance; this is only
     * the withdraw signal fee (0.2 TON). Sending more (e.g. 1 TON) fails on-chain.
     */
    val WITHDRAW_FEE: BigInteger = BigInteger.valueOf(200_000_000L)

    /** Pools backed by a genuine nominator implementation that this app can stake into. */
    fun isNominatorImplementation(implementation: String?): Boolean =
        implementation == IMPLEMENTATION_WHALES || implementation == IMPLEMENTATION_TF

    /**
     * Deposit comment for [implementation], or `null` for an unknown/unsupported implementation —
     * in which case the action must be blocked rather than guessing a comment.
     */
    fun depositComment(implementation: String?): String? =
        when (implementation) {
            IMPLEMENTATION_WHALES -> "Deposit"
            IMPLEMENTATION_TF -> "d"
            else -> null
        }

    /**
     * Withdraw comment for [implementation], or `null` for an unknown/unsupported implementation.
     */
    fun withdrawComment(implementation: String?): String? =
        when (implementation) {
            IMPLEMENTATION_WHALES -> "Withdraw"
            IMPLEMENTATION_TF -> "w"
            else -> null
        }

    /** Minimum deposit (in nanotons) for a pool whose `min_stake` is [minStakeNano]. */
    fun minimumDeposit(minStakeNano: BigInteger): BigInteger = minStakeNano + DEPOSIT_COMMISSION

    /** Every deposit/withdraw comment across supported implementations. */
    private val TRANSFER_COMMENTS: Set<String> =
        setOfNotNull(
            depositComment(IMPLEMENTATION_WHALES),
            withdrawComment(IMPLEMENTATION_WHALES),
            depositComment(IMPLEMENTATION_TF),
            withdrawComment(IMPLEMENTATION_TF),
        )

    /**
     * Whether [memo] is a nominator-pool deposit/withdraw comment. Such messages MUST be sent
     * bounceable (see the class docs) so a message the pool rejects returns instead of being
     * absorbed; the signing layer forces the bounce flag for these — matching the initiating device
     * — so every co-signer reproduces an identical pre-image hash.
     */
    fun isTransferComment(memo: String?): Boolean = memo != null && memo in TRANSFER_COMMENTS
}
