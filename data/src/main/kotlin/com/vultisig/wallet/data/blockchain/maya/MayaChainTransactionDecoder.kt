package com.vultisig.wallet.data.blockchain.maya

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
 * Decodes MAYAChain pool and node memos.
 *
 * Mirrors the iOS `MayaChainTransactionDecoder`. It is scoped to MAYAChain alone rather than shared
 * with THORChain because the two use the same memo heads with different field layouts — a Maya
 * `BOND` names its node in field 3 behind an asset and a unit count, where a THORChain `BOND` names
 * it in field 1. Reading one grammar with the other's offsets names the wrong node.
 */
class MayaChainTransactionDecoder @Inject constructor() : TransactionContentDecoder {

    override val handles: Set<Chain> = setOf(Chain.MayaChain)

    override fun decode(tx: SignedTransactionContent): DecodedTransaction? {
        val content = tx.corroborated ?: return null
        val memo = content.memo(MEMO_PRECEDENCE) ?: return null

        val fields = memo.split(":")
        val head = fields.firstOrNull() ?: return null

        // Each head is matched against its whole documented layout, not just the slots this
        // renders. A memo carrying too few fields to be that operation, or surplus fields this
        // grammar cannot account for, is a memo the reader does not recognise — and naming it
        // anyway would present an ambiguous string as a verified action.
        return when (head.uppercase()) {
            // `POOL+` alone; a Cacao pool deposit parameterises nothing.
            "POOL+" -> {
                if (fields.size != POOL_DEPOSIT_FIELDS) return null
                DecodedTransaction(
                    operation = DecodedOperation.Stake,
                    amount = carried(content.amount),
                    evidence = DecodedEvidence.Memo,
                )
            }

            // `POOL-:<basisPoints>[:affiliate:rate]`. iOS builds the bare two-field form and this
            // app appends an affiliate and a rate, so a co-signer has to accept both.
            "POOL-" -> {
                if (fields.size !in POOL_WITHDRAW_FIELDS) return null
                val bps = fields[BASIS_POINTS_FIELD].toIntOrNull() ?: return null
                if (bps !in 1..MAX_BASIS_POINTS) return null
                DecodedTransaction(
                    operation = DecodedOperation.Unstake,
                    amount = DecodedAmount.Fraction(bps, DecodedAsset.TransactionCoin),
                    evidence = DecodedEvidence.Memo,
                )
            }

            "BOND" -> bonded(DecodedOperation.Bond, fields)

            "UNBOND" -> bonded(DecodedOperation.Unbond, fields)

            // `LEAVE:<node>`, and nothing behind it.
            "LEAVE" -> {
                if (fields.size != LEAVE_FIELDS) return null
                val node = fields[LEAVE_NODE_FIELD].takeIf { it.isNotEmpty() } ?: return null
                DecodedTransaction(
                    operation = DecodedOperation.Leave,
                    amount = DecodedAmount.Unstated,
                    counterparty = DecodedCounterparty.Node(node),
                    evidence = DecodedEvidence.Memo,
                )
            }

            else -> null
        }
    }

    /**
     * `BOND:<asset>:<units>:<node>[:provider]`.
     *
     * The unit count is the one slot allowed to be empty — this app writes it empty whenever the
     * bond names no LP units — so what is required here is the layout, and a value in every slot
     * that carries meaning. A provider is optional, but an empty one is a field that says nothing.
     *
     * The size itself is never read: LP units are not base units of any asset and cannot be
     * rendered as a currency amount, and the value the transaction carries is a dust floor rather
     * than the bond. Until there is a presentation that says "N units of pool", the verb and the
     * node are the whole truthful reading.
     */
    private fun bonded(operation: DecodedOperation, fields: List<String>): DecodedTransaction? {
        if (fields.size !in BOND_FIELDS) return null
        if (fields[ASSET_FIELD].isEmpty()) return null
        if (fields.size > PROVIDER_FIELD && fields[PROVIDER_FIELD].isEmpty()) return null
        val node = fields[NODE_FIELD].takeIf { it.isNotEmpty() } ?: return null

        return DecodedTransaction(
            operation = operation,
            amount = DecodedAmount.Unstated,
            counterparty = DecodedCounterparty.Node(node),
            evidence = DecodedEvidence.Memo,
        )
    }

    private companion object {
        /** An earlier approve or swap route makes the sidecar memo inert. */
        val MEMO_PRECEDENCE = MemoPrecedence.MemoIsInertWhenRoutedEarlier

        /** `BOND` and `UNBOND` carry the node behind the asset and the unit count. */
        const val ASSET_FIELD = 1
        const val NODE_FIELD = 3
        const val PROVIDER_FIELD = 4
        val BOND_FIELDS = 4..5

        const val POOL_DEPOSIT_FIELDS = 1
        const val BASIS_POINTS_FIELD = 1
        val POOL_WITHDRAW_FIELDS = 2..4

        const val LEAVE_FIELDS = 2
        const val LEAVE_NODE_FIELD = 1

        const val MAX_BASIS_POINTS = 10_000

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
