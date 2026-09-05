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

        return when (head.uppercase()) {
            "POOL+" ->
                DecodedTransaction(
                    operation = DecodedOperation.Stake,
                    amount = carried(content.amount),
                    evidence = DecodedEvidence.Memo,
                )

            // `POOL-:<basisPoints>[:affiliate:rate]` — later fields may name a single-sided asset.
            "POOL-" -> {
                val bps = fields.getOrNull(1)?.toIntOrNull() ?: return null
                if (bps !in 1..MAX_BASIS_POINTS) return null
                DecodedTransaction(
                    operation = DecodedOperation.Unstake,
                    amount = DecodedAmount.Fraction(bps, DecodedAsset.TransactionCoin),
                    evidence = DecodedEvidence.Memo,
                )
            }

            // `BOND:<asset>:<units>:<node>[:provider]`
            "BOND" -> node(fields)?.let { bonded(DecodedOperation.Bond, it) }

            "UNBOND" -> node(fields)?.let { bonded(DecodedOperation.Unbond, it) }

            "LEAVE" -> {
                val node = fields.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
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

    private fun node(fields: List<String>): String? =
        fields.getOrNull(NODE_FIELD)?.takeIf { it.isNotEmpty() }

    /**
     * Bond and unbond name their size in LP units, which are not base units of any asset and cannot
     * be rendered as a currency amount. Until there is a presentation that says "N units of pool",
     * the verb and the node are the whole truthful reading.
     */
    private fun bonded(operation: DecodedOperation, node: String) =
        DecodedTransaction(
            operation = operation,
            amount = DecodedAmount.Unstated,
            counterparty = DecodedCounterparty.Node(node),
            evidence = DecodedEvidence.Memo,
        )

    private companion object {
        /** An earlier approve or swap route makes the sidecar memo inert. */
        val MEMO_PRECEDENCE = MemoPrecedence.MemoIsInertWhenRoutedEarlier

        /** `BOND` and `UNBOND` carry the node behind the asset and the unit count. */
        const val NODE_FIELD = 3

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
