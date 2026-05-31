package com.vultisig.wallet.data.blockchain.cosmos.staking

import com.vultisig.wallet.data.models.Chain

/**
 * Per-chain configuration for Cosmos-SDK x/staking + x/distribution flows.
 *
 * The table is the sole gas / fee / valoper-prefix source for the delegate, undelegate, redelegate
 * and withdraw-reward msg encoders — every consumer goes through [entryFor] so we never hand-roll
 * gas budgets at call sites. Adding a chain here also promotes it into the staking-allowlist
 * semantics ([isStakingSupported]).
 *
 * Values mirror iOS `CosmosStakingConfig.swift` (vultisig-ios PR #4432) and the agent app's
 * `COSMOS_CHAIN_CONFIG` table. LUNC gas (1.5M units / 100M uluna) is the empirically-verified floor
 * — smaller budgets OoG on `columbus-5`.
 */
object CosmosStakingConfig {

    data class Entry(
        val chainId: String,
        val bondDenom: String,
        val feeDenom: String,
        val valoperHrp: String,
        val gasLimit: Long,
        val feeAmount: Long,
        val unbondingDays: Int,
    )

    val table: Map<Chain, Entry> =
        mapOf(
            Chain.Terra to
                Entry(
                    chainId = "phoenix-1",
                    bondDenom = "uluna",
                    feeDenom = "uluna",
                    valoperHrp = "terravaloper",
                    gasLimit = 300_000L,
                    feeAmount = 7_500L,
                    unbondingDays = 21,
                ),
            Chain.TerraClassic to
                Entry(
                    chainId = "columbus-5",
                    bondDenom = "uluna",
                    feeDenom = "uluna",
                    valoperHrp = "terravaloper",
                    gasLimit = 1_500_000L,
                    feeAmount = 100_000_000L,
                    unbondingDays = 21,
                ),
        )

    fun entryFor(chain: Chain): Entry = table[chain] ?: throw CosmosStakingConfigException(chain)

    fun isStakingSupported(chain: Chain): Boolean = table.containsKey(chain)

    fun chainIdFor(chain: Chain): String = entryFor(chain).chainId

    fun bondDenomFor(chain: Chain): String = entryFor(chain).bondDenom

    fun feeDenomFor(chain: Chain): String = entryFor(chain).feeDenom

    fun valoperHrpFor(chain: Chain): String = entryFor(chain).valoperHrp

    fun gasLimitFor(chain: Chain): Long = entryFor(chain).gasLimit

    fun feeAmountFor(chain: Chain): Long = entryFor(chain).feeAmount

    fun unbondingDaysFor(chain: Chain): Int = entryFor(chain).unbondingDays
}

class CosmosStakingConfigException(val chain: Chain) :
    IllegalArgumentException("Cosmos staking is not supported on chain ${chain.raw}")
