package com.vultisig.wallet.data.blockchain.solana.staking

import java.math.BigInteger

/**
 * Static configuration for Solana native staking. Single-chain (Solana is the only chain with
 * native stake accounts), so there is no per-chain table like the Cosmos analog. Mirrors the iOS
 * foundation slice (vultisig-ios #4659). `getMinimumBalanceForRentExemption` is read live (see
 * [SolanaStakingService]) but `getStakeMinimumDelegation` is blocked, so the minimum-delegation
 * floor here is a documented constant rather than an on-chain read.
 */
object SolanaStakingConfig {

    /** On-chain owner (program id) of every native stake account. */
    const val STAKE_PROGRAM_ID = "Stake11111111111111111111111111111111111111"

    /**
     * Byte length of a delegated stake account. Used as the `dataSize` filter on
     * `getProgramAccounts` and as the size passed to `getMinimumBalanceForRentExemption` so the
     * rent-exempt reserve matches the account this app actually creates.
     */
    const val STAKE_ACCOUNT_SPACE = 200

    /**
     * Byte offset of the withdrawer authority inside a stake account's data. A `memcmp` filter at
     * this offset restricts `getProgramAccounts` to the accounts a given wallet controls, so the
     * RPC node does the filtering instead of streaming every stake account on the network.
     */
    const val WITHDRAWER_MEMCMP_OFFSET = 44

    /** Lamports in one SOL (1e9). */
    val LAMPORTS_PER_SOL: BigInteger = BigInteger.valueOf(1_000_000_000L)

    /**
     * Documented minimum delegation floor in lamports (1 SOL). `getStakeMinimumDelegation` is
     * blocked on `https://api.vultisig.com/solana/`, so the delegate flow enforces this constant on
     * top of the live rent-exempt reserve instead of reading the network minimum.
     */
    val MINIMUM_DELEGATION_LAMPORTS: BigInteger = BigInteger.valueOf(1_000_000_000L)

    /**
     * Fallback rent-exempt reserve for a [STAKE_ACCOUNT_SPACE]-byte stake account (~0.00228288
     * SOL), used only when the live `getMinimumBalanceForRentExemption` read fails. Matches the
     * long-standing mainnet value for a 200-byte account.
     */
    val RENT_EXEMPT_RESERVE_FALLBACK_LAMPORTS: BigInteger = BigInteger.valueOf(2_282_880L)
}
