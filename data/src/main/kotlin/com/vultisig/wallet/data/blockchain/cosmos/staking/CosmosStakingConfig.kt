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
            Chain.Qbtc to
                Entry(
                    // Live on-chain id (the network launched under it). All values verified against
                    // the qbtc-testnet LCD; mirrors iOS PR vultisig-ios#4481.
                    chainId = "qbtc-testnet",
                    // `qbtc` is lowercase and NOT a micro-denom — QBTC has 8 decimals and the
                    // staking bond_denom is the bare `qbtc` (live LCD `staking/params.bond_denom`).
                    bondDenom = "qbtc",
                    feeDenom = "qbtc",
                    valoperHrp = "qbtcvaloper",
                    // Matches Terra's post-OoG floor — a live MsgDelegate simulate burned 278_759;
                    // redelegate (dual-record write + ~2.4 KB ML-DSA sig) is heavier.
                    gasLimit = 400_000L,
                    // The qbtc-testnet `min_tx_fee` floor; `min_gas_price` is 0, so the larger gas
                    // budget does not raise the fee (unlike the QBTC send path's flat 7_500).
                    feeAmount = 800L,
                    // Live LCD `unbonding_time` = 1814400s = 21 days.
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
