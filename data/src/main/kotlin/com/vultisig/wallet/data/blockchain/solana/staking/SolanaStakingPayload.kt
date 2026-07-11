package com.vultisig.wallet.data.blockchain.solana.staking

import java.math.BigInteger

/**
 * Carries the Solana native-staking operation intent from the build-keysign step into the signing
 * layer (wallet-core's Solana stake proto: delegate / deactivate / withdraw). Analog of
 * `CosmosStakingPayload`.
 *
 * This intent is LOCAL-ONLY — it is never relayed to the co-signing peer. The initiating device
 * turns it into byte-identical unsigned transaction bytes once (via
 * [com.vultisig.wallet.data.chains.helpers.SolanaHelper.buildStakingUnsignedTransaction], pinning
 * the recent blockhash and — for delegate — the wallet-core-derived stake-account address) and
 * relays those raw bytes through `SignSolana.rawTransactions`. Every device signs the same bytes,
 * which is the MPC byte-parity guarantee.
 *
 * Discriminated by [opType]. Field set per op:
 * - [SolanaStakingOpType.Delegate] → [votePubkey], [lamports]; [stakeAccount] null for a fresh
 *   stake (wallet-core derives + creates the account) or set to re-delegate an EXISTING cooled-down
 *   account to a new validator (move-stake step 2 / "Finish Move")
 * - [SolanaStakingOpType.Unstake] → [stakeAccount] (deactivate; no amount)
 * - [SolanaStakingOpType.Withdraw] → [stakeAccount], [lamports]
 */
enum class SolanaStakingOpType {
    Delegate,
    Unstake,
    Withdraw,
}

data class SolanaStakingPayload(
    val opType: SolanaStakingOpType,
    /** Vote account to delegate to ([SolanaStakingOpType.Delegate]). */
    val votePubkey: String? = null,
    /**
     * Source stake account for [SolanaStakingOpType.Unstake] / [SolanaStakingOpType.Withdraw], or —
     * on a [SolanaStakingOpType.Delegate] — the existing account to re-delegate ("Finish Move").
     */
    val stakeAccount: String? = null,
    /**
     * Lamports for [SolanaStakingOpType.Delegate] / [SolanaStakingOpType.Withdraw]. Null for
     * [SolanaStakingOpType.Unstake] — deactivate carries no amount, the whole account cools down.
     */
    val lamports: BigInteger? = null,
) {
    companion object {
        fun delegate(votePubkey: String, lamports: BigInteger): SolanaStakingPayload =
            SolanaStakingPayload(
                opType = SolanaStakingOpType.Delegate,
                votePubkey = votePubkey,
                lamports = lamports,
            )

        /**
         * Move-stake step 2 ("Finish Move"): re-delegate an existing, fully-inactive [stakeAccount]
         * to a new validator. Same delegate instruction as [delegate] but with [stakeAccount] set
         * so wallet-core delegates the existing account instead of deriving/creating a fresh one.
         */
        fun finishMove(
            stakeAccount: String,
            votePubkey: String,
            lamports: BigInteger,
        ): SolanaStakingPayload =
            SolanaStakingPayload(
                opType = SolanaStakingOpType.Delegate,
                votePubkey = votePubkey,
                stakeAccount = stakeAccount,
                lamports = lamports,
            )

        fun unstake(stakeAccount: String): SolanaStakingPayload =
            SolanaStakingPayload(opType = SolanaStakingOpType.Unstake, stakeAccount = stakeAccount)

        fun withdraw(stakeAccount: String, lamports: BigInteger): SolanaStakingPayload =
            SolanaStakingPayload(
                opType = SolanaStakingOpType.Withdraw,
                stakeAccount = stakeAccount,
                lamports = lamports,
            )
    }
}
