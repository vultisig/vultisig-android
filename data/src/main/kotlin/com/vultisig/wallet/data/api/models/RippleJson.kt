import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class ResponseJson(
    @SerialName("result")
    val result: ResultJson,
)

@Serializable
data class ResultJson(
    @SerialName("engine_result")
    val engineResult: String,
    @SerialName("engine_result_message")
    val engineResultMessage: String?,
    @SerialName("tx_json")
    val tx_json: TXJson?,
)


@Serializable
data class TXJson(
    @SerialName("hash")
    val hash: String?,
)
