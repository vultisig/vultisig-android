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
 * `COSMOS_CHAIN_CONFIG` table. The gas budgets are empirically-verified floors with headroom for
 * the heaviest single-msg path (MsgBeginRedelegate, which mutates BOTH src + dst validator records
 * and costs more gas than a single-validator delegate/undelegate). Phoenix-1 redelegate was
 * observed at 300_140 gas in mainnet tx 44A3CE6C...EAF31 (OoG against the prior 300_000 floor) so
 * the Terra floor was raised to 400_000 with proportional fee.
 */
object CosmosStakingConfig {

    /**
     * cosmos-sdk x/staking `MaxEntries` — the cap on concurrent unbonding entries per (delegator,
     * validator) pair AND on concurrent redelegation entries per (delegator, src, dst) triple. Both
     * Terra (phoenix-1) and TerraClassic (columbus-5) run the SDK default of 7. Once the cap is
     * reached the chain rejects further `MsgUndelegate` / `MsgBeginRedelegate` with
     * `ErrMaxUnbondingDelegationEntries` / `ErrMaxRedelegationEntries` — so the UI preflights it to
     * avoid burning an MPC ceremony on a guaranteed-reject tx.
     */
    const val MAX_ENTRIES = 7

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
                    // 300_000 was the prior floor; observed OoG on redelegate at 300_140 gasUsed
                    // (phoenix-1 tx 44A3CE6C...EAF31). Raised to 400_000 to absorb the dual-record
                    // x/staking write that MsgBeginRedelegate performs. Fee scaled 1:1.
                    gasLimit = 400_000L,
                    feeAmount = 10_000L,
                    unbondingDays = 21,
                ),
            Chain.TerraClassic to
                Entry(
                    chainId = "columbus-5",
                    bondDenom = "uluna",
                    feeDenom = "uluna",
                    valoperHrp = "terravaloper",
                    // LUNC redelegate hits the same dual-record path. Bumping 1.5M -> 2M for
                    // headroom (8-validator claim batch = 16M total gas, still inside
                    // columbus-5's per-block budget). Fee scaled to preserve the prior gas-price
                    // ratio: old `100M / 1.5M = 66.6667 uluna/gas`. New: `133_333_334 / 2M =
                    // 66.6667 uluna/gas` (rounded up by 1).
                    gasLimit = 2_000_000L,
                    feeAmount = 133_333_334L,
                    unbondingDays = 21,
                ),
            // Chain identity (chainId / denom / valoper / unbonding) mirrors iOS #4481; the gas
            // and fee are tuned to the live qbtc-testnet instead — iOS's 300_000 gas OoGs on
            // redelegate (see below) and its 7_500 fee overpays the verified 800 min_tx_fee.
            Chain.Qbtc to
                Entry(
                    chainId = "qbtc",
                    // Lowercase, not a micro-denom — QBTC's bond_denom is the bare `qbtc` (8 dp).
                    bondDenom = "qbtc",
                    feeDenom = "qbtc",
                    valoperHrp = "qbtcvaloper",
                    // Redelegate OoG'd at the borrowed 400_000 Terra floor: on-chain
                    // MsgBeginRedelegate burned 400_832 gas (qbtc-testnet tx 67B85E1C…, sdk code
                    // 11 "ReadPerByte"). It's the heaviest single-msg path — the dual-record
                    // x/staking write plus the per-byte read of QBTC's ~2.4 KB ML-DSA signature
                    // outruns the floor a single MsgDelegate (simulate burned 278_759) fits in.
                    // Raised to 800_000 (~2x the observed burn). Free here: qbtc-testnet has no
                    // minimum gas price and block.max_gas is -1, so a larger budget moves neither
                    // the fee nor the block-limit risk. Batched claims scale this per-msg in the
                    // resolver, keeping the same headroom.
                    gasLimit = 800_000L,
                    // qbtc-testnet min_tx_fee floor (min_gas_price is 0, so gas can't raise it).
                    feeAmount = 800L,
                    unbondingDays = 21,
                ),
        )

    fun entryFor(chain: Chain): Entry = table[chain] ?: throw CosmosStakingConfigException(chain)

    fun isStakingSupported(chain: Chain): Boolean = table.containsKey(chain)

    fun valoperHrpFor(chain: Chain): String = entryFor(chain).valoperHrp

    fun feeAmountFor(chain: Chain): Long = entryFor(chain).feeAmount

    fun unbondingDaysFor(chain: Chain): Int = entryFor(chain).unbondingDays
}

class CosmosStakingConfigException(val chain: Chain) :
    IllegalArgumentException("Cosmos staking is not supported on chain ${chain.raw}")
