import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RippleAccountResponse(
    val result: Result? = null
) {
    @Serializable
    data class Result(
        @SerialName("account_data")
        val accountData: AccountData? = null,
        @SerialName("ledger_current_index")
        val ledgerCurrentIndex: Int? = null,
        @SerialName("queue_data")
        val queueData: QueueData? = null,
        val status: String? = null,
        val validated: Boolean? = null
    )

    @Serializable
    data class AccountData(
        @SerialName("Account")
        val account: String? = null,
        @SerialName("Balance")
        val balance: String? = null,
        @SerialName("Flags")
        val flags: Int? = null,
        @SerialName("LedgerEntryType")
        val ledgerEntryType: String? = null,
        @SerialName("OwnerCount")
        val ownerCount: Int? = null,
        @SerialName("PreviousTxnID")
        val previousTxnID: String? = null,
        @SerialName("PreviousTxnLgrSeq")
        val previousTxnLgrSeq: Int? = null,
        @SerialName("Sequence")
        val sequence: Int? = null,
        val index: String? = null
    )

    @Serializable
    data class QueueData(
        @SerialName("auth_change_queued")
        val authChangeQueued: Boolean? = null,
        @SerialName("highest_sequence")
        val highestSequence: Int? = null,
        @SerialName("lowest_sequence")
        val lowestSequence: Int? = null,
        @SerialName("max_spend_drops_total")
        val maxSpendDropsTotal: String? = null,
        val transactions: List<Transaction>? = null,
        @SerialName("txn_count") val txnCount: Int? = null
    )

    @Serializable
    data class Transaction(
        @SerialName("auth_change")
        val authChange: Boolean? = null,
        val fee: String? = null,
        @SerialName(
            "fee_level"
        ) val feeLevel: String? = null,
        @SerialName("max_spend_drops")
        val maxSpendDrops: String? = null,
        val seq: Int? = null,
        @SerialName("LastLedgerSequence")
        val lastLedgerSequence: Int? = null
    )
}