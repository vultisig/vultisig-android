package com.vultisig.wallet.data.blockchain.cosmos

import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation

/**
 * The Cosmos memo grammar, read once for both the sidecar memo an initiator holds and the
 * `TxBody.memo` a co-signer receives inside the signed body.
 *
 * The two arrive by different routes but are the same string with the same meaning, and a grammar
 * that lived in only one of them would name an operation on one device and not on the other.
 */
internal object CosmosMemoReader {

    /** What a memo names, and whether the quantity the transaction moves belongs to that verb. */
    data class Reading(val operation: DecodedOperation, val movesTheCarriedAmount: Boolean)

    /**
     * Matches a memo against its whole documented layout. A memo carrying fields this grammar
     * cannot account for is one the reader does not recognise, and naming it anyway would present
     * an ambiguous string as a verified action.
     */
    fun read(memo: String): Reading? {
        val fields = memo.split(":")
        val head = fields.firstOrNull() ?: return null

        // The chain matches these heads case-insensitively.
        return when (head.uppercase()) {
            // `SWITCH:<address>` moves the asset to the sender's THORChain address.
            "SWITCH" -> {
                if (fields.size != SWITCH_FIELDS) return null
                if (fields[SWITCH_ADDRESS_FIELD].isEmpty()) return null
                Reading(DecodedOperation.SwitchChain, movesTheCarriedAmount = true)
            }

            // `<CHAIN>_VOTE:<option>:<proposal>` moves no quantity. `QBTC_VOTE` is what this app
            // builds; `DYDX_VOTE` is the same grammar from an iOS initiator, and a co-signer has
            // to read both.
            "QBTC_VOTE",
            "DYDX_VOTE" -> {
                if (fields.size != VOTE_FIELDS) return null
                if (fields.drop(1).any { it.isEmpty() }) return null
                Reading(DecodedOperation.Vote, movesTheCarriedAmount = false)
            }

            else -> null
        }
    }

    private const val SWITCH_FIELDS = 2
    private const val SWITCH_ADDRESS_FIELD = 1

    /** The head, the option, and the proposal id — `GovernanceViewModel` writes exactly three. */
    private const val VOTE_FIELDS = 3
}
