package com.vultisig.wallet.ui.models.limitorder

import com.vultisig.wallet.data.swap.limit.LimitSwapCancelMemo

/**
 * How a limit-order CANCEL reads on the screens that surround signing: Verify and the done screen.
 *
 * Without it a cancel renders through the generic deposit vocabulary — "You're sending 0 RUNE" on
 * the THORChain route, "You're sending 0.0002 BTC" on the L1 one, where that amount is dust that
 * gets donated to the pool. Neither sentence is what the user is doing.
 *
 * Keyed off the MEMO — the same thing THORChain itself reads to decide what the transaction is —
 * rather than off a transaction-type flag. That is deliberate on two counts: the transaction type
 * is generated from the shared `commondata` submodule, and a cancel needs no new wire value (it is
 * an ordinary `MsgDeposit` or memo-bearing transfer whose entire meaning lives in the memo); and a
 * CO-SIGNING device never sees the initiator's transaction, only the payload it is asked to sign,
 * so the memo is all it has to go on.
 */
internal object LimitOrderCancelPresentation {

    fun isCancel(memo: String?): Boolean = LimitSwapCancelMemo.isCancelMemo(memo)

    /**
     * The order's asset pair (`SRC → TGT`) parsed out of a cancel memo, or null when either leg
     * cannot be read.
     *
     * A cancel memo spells each leg as `<amount><ASSET>` (THORNode's `getCoin` splices the two
     * apart), so dropping the leading amount digits leaves the THORChain asset string. Shown
     * verbatim in THORChain's own spelling, because that is the spelling both the initiator and a
     * co-signer derive it from — nothing is translated, so the two screens cannot disagree.
     */
    fun pairCaption(memo: String?): String? {
        if (!isCancel(memo)) return null
        // `m=<:<amount><SRC>:<tradeTarget><TGT>:0` — fields [1] and [2] are the coins.
        val fields = requireNotNull(memo).split(":")
        if (fields.size < 3) return null
        val source = assetAfterAmount(fields[1]) ?: return null
        val target = assetAfterAmount(fields[2]) ?: return null
        return "$source → $target"
    }

    /**
     * The asset portion of a `<amount><ASSET>` coin field: the leading run of ASCII digits is the
     * amount, everything after it the asset. Null when no asset remains.
     */
    private fun assetAfterAmount(field: String): String? =
        field.dropWhile { it in '0'..'9' }.takeIf { it.isNotEmpty() }
}
