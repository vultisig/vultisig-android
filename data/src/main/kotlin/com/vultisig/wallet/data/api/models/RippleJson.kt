import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RippleBroadcastResponseResponseJson(
    @SerialName("result") val result: RippleBroadcastResponseResponseResultJson
)

@Serializable
data class RippleBroadcastResponseResponseResultJson(
    @SerialName("engine_result") val engineResult: String,
    @SerialName("engine_result_message") val engineResultMessage: String?,
    @SerialName("tx_json") val txJson: RippleBroadcastResponseResponseTransactionJson?,
)

@Serializable
data class RippleBroadcastResponseResponseTransactionJson(@SerialName("hash") val hash: String?)

@Serializable
data class RippleBroadcastSuccessResponseJson(
    @SerialName("result") val result: RippleBroadcastSuccessResultJson
)

@Serializable
data class RippleBroadcastSuccessResultJson(
    @SerialName("hash") val hash: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("tx_json") val txJson: RippleBroadcastSuccessTransactionJson? = null,
    @SerialName("validated") val validated: Boolean? = null,
    @SerialName("meta") val meta: RippleTxMetaJson? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("error_code") val errorCode: Int? = null,
    @SerialName("error_message") val errorMessage: String? = null,
)

@Serializable
data class RippleTxMetaJson(@SerialName("TransactionResult") val transactionResult: String? = null)

@Serializable
data class RippleBroadcastSuccessTransactionJson(
    @SerialName("Account") val account: String,
    @SerialName("DeliverMax") val deliverMax: String,
    @SerialName("Destination") val destination: String,
    @SerialName("Fee") val fee: String,
    @SerialName("Flags") val flags: Int,
    @SerialName("LastLedgerSequence") val lastLedgerSequence: Int,
    @SerialName("Sequence") val sequence: Int,
    @SerialName("SigningPubKey") val signingPubKey: String,
    @SerialName("TransactionType") val transactionType: String,
    @SerialName("TxnSignature") val txnSignature: String,
)
