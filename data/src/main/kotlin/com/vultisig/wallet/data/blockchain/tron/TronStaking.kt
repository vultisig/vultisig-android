package com.vultisig.wallet.data.blockchain.tron

import com.vultisig.wallet.data.models.Coin

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

data class TronStakingIntent(val operation: TronStakingOperation, val resource: TronResourceType)

/**
 * The staking contract TronHelper assembles for this transfer, or null when it assembles a plain
 * transfer instead. Freeze/unfreeze are the only contracts it builds without `setMemo`.
 */
fun tronStakingIntent(coin: Coin, toAddress: String, memo: String?): TronStakingIntent? {
    if (!coin.isNativeToken || coin.address != toAddress) return null
    val (operation, resource) =
        memo?.let { TRON_STAKING_MEMO_REGEX.matchEntire(it) }?.destructured ?: return null
    // The regex alternations are generated from these two enums, so a match always resolves.
    return TronStakingIntent(
        operation = TronStakingOperation.entries.first { it.memoPrefix == operation },
        resource = TronResourceType.valueOf(resource),
    )
}
