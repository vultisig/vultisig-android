import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class ReultJosn(
    @SerialName("engine_result")
    val engineResult: String,
    @SerialName("engine_result_message")
    val engineResultMessage: EngineResultMessage?,
    @SerialName("tx_json")
    val tx_json: TXJson?,
)

@Serializable
data class EngineResultMessage(
    @SerialName("engine_result_message")
    val description: String?,
)

@Serializable
data class TXJson(
    @SerialName("hash")
    val hash: String?,
)
