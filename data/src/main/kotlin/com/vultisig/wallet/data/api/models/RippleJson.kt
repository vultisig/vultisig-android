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
data class RippleErrorResponseJson(
    @SerialName("result")
    val result: RippleErrorResultJson
)

@Serializable
data class RippleErrorResultJson(
    @SerialName("error")
    val error: String,
    @SerialName("error_code")
    val errorCode: Int,
    @SerialName("error_message")
    val errorMessage: String,
    @SerialName("request")
    val request: RippleErrorRequestJson,
    @SerialName("status")
    val status: String
)

@Serializable
data class RippleErrorRequestJson(
    @SerialName("binary")
    val binary: Boolean,
    @SerialName("command")
    val command: String,
    @SerialName("transaction")
    val transaction: String
)





