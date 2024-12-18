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
