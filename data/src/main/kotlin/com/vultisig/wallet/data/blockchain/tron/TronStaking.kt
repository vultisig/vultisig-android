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

/**
 * Routing signal for the claim that returns expired unfrozen TRX to the spendable balance
 * (`WithdrawExpireUnfreezeContract`). `UnfreezeBalanceV2` only moves the TRX out of `frozenV2`; the
 * chain hands it back only on this contract, once the 14-day unlock has elapsed.
 *
 * It carries no resource — the contract claims every expired entry at once — so it sits beside the
 * `OP:RESOURCE` grammar rather than stretching it.
 */
const val TRON_WITHDRAW_EXPIRE_UNFREEZE_MEMO: String = "WITHDRAW_EXPIRE_UNFREEZE"

/**
 * True when TronHelper assembles a freeze/unfreeze/withdraw contract for this transfer rather than
 * a plain transfer — the only three it builds without `setMemo`. Mirrors that dispatch for callers
 * that must agree with it; TronHelper itself keeps its own literal checks, since the memo grammar
 * is a cross-device wire format that must not shift with an enum rename.
 */
fun isTronStakingTransfer(coin: Coin, toAddress: String, memo: String?): Boolean =
    coin.isNativeToken &&
        coin.address == toAddress &&
        memo != null &&
        (TRON_STAKING_MEMO_REGEX.matches(memo) || memo == TRON_WITHDRAW_EXPIRE_UNFREEZE_MEMO)
