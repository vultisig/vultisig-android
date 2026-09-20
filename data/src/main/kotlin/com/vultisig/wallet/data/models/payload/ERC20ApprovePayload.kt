package com.vultisig.wallet.data.models.payload

import java.math.BigInteger

/**
 * The ERC-20 `approve` a keysign sends before its main transaction so [spender] can pull [amount].
 *
 * @property resetAllowanceFirst When true, every signer emits `approve(spender, 0)` at the payload
 *   nonce before `approve(spender, amount)` at nonce + 1, and the main transaction moves to
 *   nonce + 2. Set by the initiator only for tokens such as USDT that revert on a non-zero ->
 *   non-zero approve while a stale allowance remains. Defaults to false so payloads from older
 *   senders keep the two-message shape.
 */
data class ERC20ApprovePayload(
    val amount: BigInteger,
    val spender: String,
    val resetAllowanceFirst: Boolean = false,
) {
    /**
     * The amount each approve leg sets, in nonce order: the zero reset first when
     * [resetAllowanceFirst], then [amount]. Every leg consumes one nonce, so the transaction that
     * depends on the approval starts `legAmounts.size` nonces after the payload's.
     */
    val legAmounts: List<BigInteger>
        get() = if (resetAllowanceFirst) listOf(BigInteger.ZERO, amount) else listOf(amount)
}
