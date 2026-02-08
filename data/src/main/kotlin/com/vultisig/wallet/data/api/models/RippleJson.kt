import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class RippleBroadcastResponseResponseJson(
    @SerialName("result")
    val result: RippleBroadcastResponseResponseResultJson,
)

@Serializable
data class RippleBroadcastResponseResponseResultJson(
    @SerialName("engine_result")
    val engineResult: String,
    @SerialName("engine_result_message")
    val engineResultMessage: String?,
    @SerialName("tx_json")
    val txJson: RippleBroadcastResponseResponseTransactionJson?,
)


@Serializable
data class RippleBroadcastResponseResponseTransactionJson(
    @SerialName("hash")
    val hash: String?,
)



@Serializable
data class RippleBroadcastSuccessResponseJson(
    @SerialName("result")
    val result: RippleBroadcastSuccessResultJson
)

@Serializable
data class RippleBroadcastSuccessResultJson(
    @SerialName("hash")
    val hash: String,
    @SerialName("status")
    val status: String,
    @SerialName("tx_json")
    val txJson: RippleBroadcastSuccessTransactionJson,
    @SerialName("validated")
    val validated: Boolean
)

@Serializable
data class RippleBroadcastSuccessTransactionJson(
    @SerialName("Account")
    val account: String,
    @SerialName("DeliverMax")
    val deliverMax: String,
    @SerialName("Destination")
    val destination: String,
    @SerialName("Fee")
    val fee: String,
    @SerialName("Flags")
    val flags: Int,
    @SerialName("LastLedgerSequence")
    val lastLedgerSequence: Int,
    @SerialName("Sequence")
    val sequence: Int,
    @SerialName("SigningPubKey")
    val signingPubKey: String,
    @SerialName("TransactionType")
    val transactionType: String,
    @SerialName("TxnSignature")
    val txnSignature: String
)