package com.vultisig.wallet.data.blockchain.cosmos

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.transaction_decoding.CorroboratedContent
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAmount
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAsset
import com.vultisig.wallet.data.models.transaction_decoding.DecodedEvidence
import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation
import com.vultisig.wallet.data.models.transaction_decoding.DecodedTransaction
import com.vultisig.wallet.data.models.transaction_decoding.MemoPrecedence
import com.vultisig.wallet.data.models.transaction_decoding.SignedAmount
import com.vultisig.wallet.data.models.transaction_decoding.SignedTransactionContent
import com.vultisig.wallet.data.models.transaction_decoding.TransactionContentDecoder
import javax.inject.Inject
import vultisig.keysign.v1.TransactionType

/**
 * Reads Cosmos operations carried by an active memo or by the wire discriminator that selects the
 * signing shape. SignDoc-contained operations belong to [CosmosSignDocDecoder], which is registered
 * ahead of this one and consumes the body before it can be reached here.
 *
 * Mirrors the iOS `CosmosTransactionDecoder`.
 */
class CosmosTransactionDecoder @Inject constructor() : TransactionContentDecoder {

    /**
     * The whole Cosmos family rather than iOS's hand-listed subset. The grammar below is
     * chain-agnostic within the family, and Android's producers do not line up with that list:
     * there is no Kujira here, governance votes are built for QBTC, and IBC transfers are offered
     * from several of these chains. Narrowing further would silently drop readings the app itself
     * emits. THORChain and MAYAChain are a different standard and keep their own decoders.
     */
    override val handles: Set<Chain> =
        Chain.entries.filter { it.standard == TokenStandard.COSMOS }.toSet()

    override fun decode(tx: SignedTransactionContent): DecodedTransaction? {
        val content = tx.corroborated ?: return null

        content.memo(MEMO_PRECEDENCE)?.let { memo ->
            decodeMemo(memo, content)?.let {
                return it
            }
        }
        return decodeWireType(content)
    }

    private fun decodeMemo(memo: String, content: CorroboratedContent): DecodedTransaction? {
        val fields = memo.split(":")
        val head = fields.firstOrNull() ?: return null

        // The chain matches these heads case-insensitively. Each is matched against its whole
        // layout: a memo with fields this grammar cannot account for is one the reader does not
        // recognise, and naming it anyway would present an ambiguous string as a verified action.
        return when (head.uppercase()) {
            // `SWITCH:<address>` moves the asset to the sender's THORChain address.
            "SWITCH" -> {
                if (fields.size != SWITCH_FIELDS) return null
                if (fields[SWITCH_ADDRESS_FIELD].isEmpty()) return null
                DecodedTransaction(
                    operation = DecodedOperation.SwitchChain,
                    amount = carried(content.amount),
                    evidence = DecodedEvidence.Memo,
                )
            }

            // `<CHAIN>_VOTE:<option>:<proposal>` moves no quantity. `QBTC_VOTE` is what this app
            // builds; `DYDX_VOTE` is the same grammar from an iOS initiator, and a co-signer has
            // to read both.
            "QBTC_VOTE",
            "DYDX_VOTE" -> {
                if (fields.size != VOTE_FIELDS) return null
                if (fields.drop(1).any { it.isEmpty() }) return null
                DecodedTransaction(
                    operation = DecodedOperation.Vote,
                    amount = DecodedAmount.Unstated,
                    evidence = DecodedEvidence.Memo,
                )
            }

            else -> null
        }
    }

    private fun decodeWireType(content: CorroboratedContent): DecodedTransaction? =
        when (content.transactionType) {
            TransactionType.TRANSACTION_TYPE_IBC_TRANSFER ->
                DecodedTransaction(
                    operation = DecodedOperation.IbcTransfer,
                    amount = carried(content.amount),
                    evidence = DecodedEvidence.WireTransactionType,
                )

            TransactionType.TRANSACTION_TYPE_VOTE ->
                DecodedTransaction(
                    operation = DecodedOperation.Vote,
                    amount = DecodedAmount.Unstated,
                    evidence = DecodedEvidence.WireTransactionType,
                )

            else -> null
        }

    private companion object {
        /** An earlier approve or swap route makes the sidecar memo inert. */
        val MEMO_PRECEDENCE = MemoPrecedence.MemoIsInertWhenRoutedEarlier

        const val SWITCH_FIELDS = 2
        const val SWITCH_ADDRESS_FIELD = 1

        /**
         * The head, the option, and the proposal id — `GovernanceViewModel` writes exactly three.
         */
        const val VOTE_FIELDS = 3

        /** What the transaction carries, when it carries a figure at all. */
        fun carried(signed: SignedAmount): DecodedAmount =
            when (signed) {
                is SignedAmount.Committed ->
                    if (signed.value.signum() > 0)
                        DecodedAmount.Units(signed.value, DecodedAsset.TransactionCoin)
                    else DecodedAmount.Unstated

                SignedAmount.ComputedAtSigning -> DecodedAmount.Unstated
            }
    }
}
