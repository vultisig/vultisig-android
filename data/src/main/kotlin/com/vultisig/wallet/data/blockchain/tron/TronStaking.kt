package com.vultisig.wallet.data.blockchain.tron

enum class TronResourceType {
    BANDWIDTH,
    ENERGY,
}

enum class TronStakingOperation(val memoPrefix: String) {
    FREEZE("FREEZE"),
    UNFREEZE("UNFREEZE"),
}

val TRON_STAKING_MEMO_REGEX: Regex = run {
    val operations = TronStakingOperation.entries.joinToString("|") { Regex.escape(it.memoPrefix) }
    val resources = TronResourceType.entries.joinToString("|") { Regex.escape(it.name) }
    Regex("^($operations):($resources)$")
}

fun tronStakingMemo(operation: TronStakingOperation, resource: TronResourceType): String =
    "${operation.memoPrefix}:${resource.name}"
