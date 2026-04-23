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
