package com.vultisig.wallet.data.blockchain.ton

import com.vultisig.wallet.data.crypto.ton.TonMessageBodyDecoder
import com.vultisig.wallet.data.crypto.ton.TonMessageBodyIntent
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAmount
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAsset
import com.vultisig.wallet.data.models.transaction_decoding.DecodedCounterparty
import com.vultisig.wallet.data.models.transaction_decoding.DecodedEvidence
import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation
import com.vultisig.wallet.data.models.transaction_decoding.DecodedTransaction
import com.vultisig.wallet.data.models.transaction_decoding.MemoPrecedence
import com.vultisig.wallet.data.models.transaction_decoding.OpaqueSignedContent
import com.vultisig.wallet.data.models.transaction_decoding.SignedAmount
import com.vultisig.wallet.data.models.transaction_decoding.SignedTransactionContent
import com.vultisig.wallet.data.models.transaction_decoding.TransactionContentDecoder
import javax.inject.Inject
import vultisig.keysign.v1.SignTon

/**
 * Decodes TON nominator-pool operations from the exact text comment a signed transfer carries, and
 * Tonstakers liquid-staking operations from the message bodies a [SignTon] batch carries.
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

        // A message batch is the signed object; the sidecar amount and destination only echo its
        // first message. Read the bodies rather than the echo.
        if (tx.signedDataBodyIsActive) {
            val batch = tx.signedData as? OpaqueSignedContent.TonTransaction ?: return null
            return decodeLiquidStaking(batch.signTon)
        }

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

            // A withdrawal request is the comment AND the fixed 0.2 TON signal fee it rides on:
            // that is what both apps build, and what the pool accepts. A transfer commented `w`
            // that carries any other amount is not a request this app made, and naming it an
            // unstake would present its real figure as a fee and then hide it behind "your whole
            // stake" — so it stays a send, with its amount and its memo in plain view.
            in TonNominatorPool.WITHDRAW_COMMENTS -> {
                if (content.amount != WITHDRAW_SIGNAL) return null
                // The pool returns the whole position later; only chain state can say how much.
                DecodedTransaction(
                    operation = DecodedOperation.Unstake,
                    amount = DecodedAmount.Unstated,
                    counterparty = DecodedCounterparty.Pool(content.toAddress),
                    evidence = DecodedEvidence.Memo,
                )
            }

            else -> null
        }
    }

    /**
     * A single-message batch is a Tonstakers operation when its body says so — and, for a deposit,
     * when it is addressed to the pool, since `pool::deposit` is an op any contract could claim. A
     * burn is named an unstake only when it carries the pool's withdrawal-flags cell, which is what
     * turns a TEP-74 burn into a withdrawal request, AND rides on the fixed TON value this app
     * attaches to one: a flagged burn carrying any other value is not a request this app made, and
     * naming it an unstake would hide that value behind the burn. The burned jetton is addressed by
     * a wallet only chain state can resolve, but that shape is only ever built for tsTON, so its
     * amount is stated in tsTON — a partial unstake must never read as the whole position. A
     * multi-message batch is a dApp request this reader has no grammar for.
     */
    private fun decodeLiquidStaking(signTon: SignTon): DecodedTransaction? {
        val message = signTon.tonMessages.filterNotNull().singleOrNull() ?: return null
        return when (val body = TonMessageBodyDecoder.decode(message.payload)) {
            is TonMessageBodyIntent.LiquidStakingDeposit -> {
                if (!Tonstakers.isPool(message.to)) return null
                val value = message.amount.toBigIntegerOrNull() ?: return null
                DecodedTransaction(
                    operation = DecodedOperation.Stake,
                    amount = deposited(SignedAmount.Committed(value)),
                    counterparty = DecodedCounterparty.Pool(message.to),
                    evidence = DecodedEvidence.SignedData,
                )
            }

            is TonMessageBodyIntent.JettonBurn -> {
                if (body.liquidStakingWithdrawal == null) return null
                if (message.amount.toBigIntegerOrNull() != Tonstakers.UNSTAKE_ATTACHED_VALUE) {
                    return null
                }
                DecodedTransaction(
                    operation = DecodedOperation.Unstake,
                    amount =
                        DecodedAmount.Units(
                            body.amount,
                            DecodedAsset.Denom(Tonstakers.TSTON_MASTER_ADDRESS),
                        ),
                    counterparty = DecodedCounterparty.Contract(message.to),
                    evidence = DecodedEvidence.SignedData,
                )
            }

            else -> null
        }
    }

    private companion object {
        /** An earlier approve or swap route makes the sidecar comment inert. */
        val MEMO_PRECEDENCE = MemoPrecedence.MemoIsInertWhenRoutedEarlier

        /** The carrier a withdrawal request is sent with, on this app and on iOS alike. */
        val WITHDRAW_SIGNAL: SignedAmount = SignedAmount.Committed(TonNominatorPool.WITHDRAW_FEE)

        /** A positive committed deposit moves chain-native TON; anything else states no figure. */
        fun deposited(signed: SignedAmount): DecodedAmount =
            when (signed) {
                is SignedAmount.Committed if signed.value.signum() > 0 ->
                    DecodedAmount.Units(signed.value, DecodedAsset.ChainNative)
                is SignedAmount.Committed -> DecodedAmount.Unstated

                SignedAmount.ComputedAtSigning -> DecodedAmount.Unstated
            }
    }
}
