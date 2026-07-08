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
 * - [SolanaStakingOpType.Delegate] → [votePubkey], [lamports]
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
    /** Source stake account for [SolanaStakingOpType.Unstake] / [SolanaStakingOpType.Withdraw]. */
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
