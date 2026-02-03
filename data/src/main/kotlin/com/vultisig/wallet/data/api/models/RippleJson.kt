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
data class RippleTxResponseJson(
    @SerialName("result")
    val result: RippleTxResult? = null,
    @SerialName("status")
    val status: String? = null,
    @SerialName("validated")
    val validated: Boolean? = null
)

@Serializable
data class RippleTxResult(
    @SerialName("engine_result")
    val engineResult: String? = null,
    @SerialName("engine_result_code")
    val engineResultCode: Int? = null,
    @SerialName("engine_result_message")
    val engineResultMessage: String? = null,
    @SerialName("tx_json")
    val txJson: RippleTxJson? = null,
    @SerialName("ledger_index")
    val ledgerIndex: Long? = null
)

@Serializable
data class RippleTxJson(
    @SerialName("hash")
    val hash: String? = null,
    @SerialName("Account")
    val account: String? = null,
    @SerialName("Fee")
    val fee: String? = null,
    @SerialName("TransactionType")
    val transactionType: String? = null,
    @SerialName("Sequence")
    val sequence: Int? = null
)





