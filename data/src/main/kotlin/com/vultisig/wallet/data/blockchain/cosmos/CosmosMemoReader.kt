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

    /**
     * The message a memo-named operation rides on.
     *
     * A memo is not evidence on its own — it is a string beside a message, and only the message
     * says what the chain will do. Naming the carrier is what lets a reader check that the two
     * agree before it presents the memo's verb.
     */
    enum class Carrier {
        /**
         * A `MsgSend`: the memo says what the transfer is for, and the send's own coin is the
         * quantity it moves.
         */
        Transfer,

        /**
         * The memo travels beside a message that states the operation itself — a vote is a
         * `MsgVote` — so the memo describes rather than proves, and moves nothing of its own.
         */
        OwnMessage,
    }

    /** What a memo names, and the message that would have to carry it. */
    data class Reading(val operation: DecodedOperation, val carrier: Carrier)

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
            // `SWITCH:<address>` moves the asset to the sender's THORChain address. The send is
            // the operation; the memo is what makes it a switch rather than a payment.
            "SWITCH" -> {
                if (fields.size != SWITCH_FIELDS) return null
                if (fields[SWITCH_ADDRESS_FIELD].isEmpty()) return null
                Reading(DecodedOperation.SwitchChain, Carrier.Transfer)
            }

            // `<CHAIN>_VOTE:<option>:<proposal>` moves no quantity, and is signed as a `MsgVote`
            // by every path that builds one — `QBTCTransactionHelper` here, `DydxHelperStruct` on
            // iOS. `QBTC_VOTE` is what this app builds; `DYDX_VOTE` is the same grammar from an
            // iOS initiator, and a co-signer has to read both.
            "QBTC_VOTE",
            "DYDX_VOTE" -> {
                if (fields.size != VOTE_FIELDS) return null
                if (fields.drop(1).any { it.isEmpty() }) return null
                Reading(DecodedOperation.Vote, Carrier.OwnMessage)
            }

            else -> null
        }
    }

    private const val SWITCH_FIELDS = 2
    private const val SWITCH_ADDRESS_FIELD = 1

    /** The head, the option, and the proposal id — `GovernanceViewModel` writes exactly three. */
    private const val VOTE_FIELDS = 3
}
