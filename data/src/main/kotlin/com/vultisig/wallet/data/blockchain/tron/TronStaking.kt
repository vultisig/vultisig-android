package com.vultisig.wallet.data.blockchain.tron

/** Matches exactly FREEZE:BANDWIDTH, FREEZE:ENERGY, UNFREEZE:BANDWIDTH, UNFREEZE:ENERGY */
val TRON_STAKING_MEMO_REGEX = Regex("^(FREEZE|UNFREEZE):(BANDWIDTH|ENERGY)$")

enum class TronResourceType {
    BANDWIDTH,
    ENERGY,
}

enum class TronStakingOperation(val memoPrefix: String) {
    FREEZE("FREEZE"),
    UNFREEZE("UNFREEZE"),
}

fun tronStakingMemo(operation: TronStakingOperation, resource: TronResourceType): String =
    "${operation.memoPrefix}:${resource.name}"

/**
 * Strip Tron freeze/unfreeze UI-only memos before fee estimation.
 *
 * `FREEZE:BANDWIDTH` / `UNFREEZE:ENERGY` etc are internal markers —
 * `TronHelper.buildFreezeBalanceV2` and `buildUnfreezeBalanceV2` never call `setMemo` on the
 * broadcast tx, but `TronFeeService` adds a flat 1 TRX memo fee whenever `Transfer.memo` is
 * non-empty. Callers that construct a `Transfer` purely for fee estimation must drop the staking
 * memo so the quoted fee matches what the user actually pays on-chain.
 */
fun String?.stripTronStakingMemoForFeeEstimation(): String? =
    this?.takeUnless { TRON_STAKING_MEMO_REGEX.matches(it) }
