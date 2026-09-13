package com.vultisig.wallet.data.blockchain.tron

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAmount
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAsset
import com.vultisig.wallet.data.models.transaction_decoding.DecodedEvidence
import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation
import com.vultisig.wallet.data.models.transaction_decoding.DecodedTransaction
import com.vultisig.wallet.data.models.transaction_decoding.MemoPrecedence
import com.vultisig.wallet.data.models.transaction_decoding.SignedAmount
import com.vultisig.wallet.data.models.transaction_decoding.SignedTransactionContent
import com.vultisig.wallet.data.models.transaction_decoding.TransactionContentDecoder
import java.math.BigInteger
import javax.inject.Inject

/**
 * Decodes TRON Stake 2.0 resource operations from the routing memo the signer dispatches on.
 *
 * A freeze or unfreeze is a self-addressed native TRX payload whose memo is `FREEZE:<RESOURCE>` or
 * `UNFREEZE:<RESOURCE>`; `TronHelper` turns that into a `FreezeBalanceV2Contract` or
 * `UnfreezeBalanceV2Contract` and the memo itself never reaches the chain. The grammar is
 * [TRON_STAKING_MEMO_REGEX] — the signer's own table — matched exactly, because the signer rejects
 * any other spelling and a reader that trimmed or case-folded would title a payload the signer
 * refuses to build.
 *
 * Mirrors the iOS `TronTransactionDecoder`, with one deliberate gate it does not have: this app's
 * signer routes a staking memo to the contract builders only on a native coin sent to the vault's
 * own address, and builds a plain or TRC20 transfer otherwise. iOS dispatches on the memo prefix
 * alone. The reader has to agree with the signer beside it, so a token-dressed or third-party
 * addressed payload is a transfer here, whatever its memo says.
 */
class TronTransactionDecoder @Inject constructor() : TransactionContentDecoder {

    /** The memo grammar is meaningful only on TRON. */
    override val handles: Set<Chain> = setOf(Chain.Tron)

    override fun decode(tx: SignedTransactionContent): DecodedTransaction? {
        // Typed dApp contracts outrank the memo in `memoIsOutranked`; an earlier approve or swap
        // route makes it inert. Both withhold `corroborated` or the memo.
        val content = tx.corroborated ?: return null
        val memo = content.memo(MEMO_PRECEDENCE) ?: return null

        // The same gate `TronHelper` dispatches on: anything else with this memo is built as a
        // transfer, or refused outright when the memo names an operation the payload cannot be.
        if (!tx.isNativeCoin || content.toAddress != tx.signerAddress) return null

        val match = TRON_STAKING_MEMO_REGEX.matchEntire(memo) ?: return null
        val operation =
            when (match.groupValues[OPERATION_GROUP]) {
                TronStakingOperation.FREEZE.memoPrefix -> DecodedOperation.Stake
                TronStakingOperation.UNFREEZE.memoPrefix -> DecodedOperation.Unstake
                else -> return null
            }

        return DecodedTransaction(
            operation = operation,
            amount = committed(content.amount),
            counterparty = null,
            evidence = DecodedEvidence.Memo,
        )
    }

    private companion object {
        /** An earlier approve or swap route makes the sidecar memo inert. */
        val MEMO_PRECEDENCE = MemoPrecedence.MemoIsInertWhenRoutedEarlier

        /** Capture group of the `OP:RESOURCE` grammar that names the operation. */
        const val OPERATION_GROUP = 1

        /** The widest balance the contract's `int64` field can carry. */
        val INT64_MAX: BigInteger = BigInteger.valueOf(Long.MAX_VALUE)

        /**
         * A freeze or unfreeze moves chain-native TRX. Only a positive amount the wire can encode
         * is stated: the contract's balance field is an `int64`, so a wider figure would name a
         * balance the signed contract never carries.
         */
        fun committed(signed: SignedAmount): DecodedAmount =
            when (signed) {
                is SignedAmount.Committed ->
                    if (signed.value.signum() > 0 && signed.value <= INT64_MAX)
                        DecodedAmount.Units(signed.value, DecodedAsset.ChainNative)
                    else DecodedAmount.Unstated

                SignedAmount.ComputedAtSigning -> DecodedAmount.Unstated
            }
    }
}
