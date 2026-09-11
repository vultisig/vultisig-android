package com.vultisig.wallet.data.blockchain.ton

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAmount
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAsset
import com.vultisig.wallet.data.models.transaction_decoding.DecodedCounterparty
import com.vultisig.wallet.data.models.transaction_decoding.DecodedEvidence
import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation
import com.vultisig.wallet.data.models.transaction_decoding.DecodedTransaction
import com.vultisig.wallet.data.models.transaction_decoding.MemoPrecedence
import com.vultisig.wallet.data.models.transaction_decoding.SignedAmount
import com.vultisig.wallet.data.models.transaction_decoding.SignedTransactionContent
import com.vultisig.wallet.data.models.transaction_decoding.TransactionContentDecoder
import javax.inject.Inject

/**
 * Decodes TON nominator-pool operations from the exact text comment a signed transfer carries.
 *
 * A nominator deposit or withdrawal is a plain TON transfer whose comment is a protocol token the
 * pool contract parses — `d`/`w` for the standard pool, `Deposit`/`Withdraw` for Whales. The
 * comment set is [TonNominatorPool]'s own writer table, so the reader can never recognise a word
 * the builder does not send. Tokens are matched exactly: the contract rejects anything else
 * on-chain, so trimming or case-folding here would title a transfer the pool will bounce.
 *
 * Mirrors the iOS `TonTransactionDecoder`. Unlike iOS, only a native-coin transfer is read: a
 * jetton transfer carries its comment in the forward payload and its amount in jetton units, so a
 * pool comment on one names neither a deposit the pool accepts nor a TON figure to show for it.
 */
class TonTransactionDecoder @Inject constructor() : TransactionContentDecoder {

    /** Chain-scoped so bare pool comments cannot collide with another chain's memo grammar. */
    override val handles: Set<Chain> = setOf(Chain.Ton)

    override fun decode(tx: SignedTransactionContent): DecodedTransaction? {
        if (!tx.isNativeCoin) return null

        // A TonConnect BOC makes the outer comment a sidecar; an earlier approve or swap route
        // makes it inert. Both withhold `corroborated` or the memo.
        val content = tx.corroborated ?: return null
        val comment = content.memo(MEMO_PRECEDENCE)?.takeIf { it.isNotEmpty() } ?: return null

        return when (comment) {
            // The deposit amount is the TON moved into the pool.
            in TonNominatorPool.DEPOSIT_COMMENTS ->
                DecodedTransaction(
                    operation = DecodedOperation.Stake,
                    amount = deposited(content.amount),
                    counterparty = DecodedCounterparty.Pool(content.toAddress),
                    evidence = DecodedEvidence.Memo,
                )

            // The transfer carries only the withdraw signal fee; the pool returns the whole
            // position later, and only chain state can say how much that is.
            in TonNominatorPool.WITHDRAW_COMMENTS ->
                DecodedTransaction(
                    operation = DecodedOperation.Unstake,
                    amount = DecodedAmount.Unstated,
                    counterparty = DecodedCounterparty.Pool(content.toAddress),
                    evidence = DecodedEvidence.Memo,
                )

            else -> null
        }
    }

    private companion object {
        /** An earlier approve or swap route makes the sidecar comment inert. */
        val MEMO_PRECEDENCE = MemoPrecedence.MemoIsInertWhenRoutedEarlier

        /** A positive committed deposit moves chain-native TON; anything else states no figure. */
        fun deposited(signed: SignedAmount): DecodedAmount =
            when (signed) {
                is SignedAmount.Committed ->
                    if (signed.value.signum() > 0)
                        DecodedAmount.Units(signed.value, DecodedAsset.ChainNative)
                    else DecodedAmount.Unstated

                SignedAmount.ComputedAtSigning -> DecodedAmount.Unstated
            }
    }
}
