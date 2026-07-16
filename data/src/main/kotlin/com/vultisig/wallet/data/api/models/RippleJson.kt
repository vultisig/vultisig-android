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

/**
 * A validated XRPL transaction's `tx_json`. Every field is nullable/defaulted: XRPL only emits the
 * fields a given `TransactionType` actually uses — a `Payment` carries `Destination`/`DeliverMax`,
 * while an `OfferCreate` carries `TakerGets`/`TakerPays` and no `Destination` at all. Making these
 * non-null broke status polling for every non-Payment type (co-signed dApp OfferCreate/TrustSet/…):
 * the `tx` response deserialization threw `MissingFieldException`, which `RippleStatusProvider`
 * swallowed as `Pending`, so a `tesSUCCESS` transaction was reported pending forever. Status only
 * reads `validated` + `meta.TransactionResult`, so the exact `tx_json` shape is display-only.
 */
@Serializable
data class RippleBroadcastSuccessTransactionJson(
    @SerialName("Account") val account: String? = null,
    @SerialName("DeliverMax") val deliverMax: String? = null,
    @SerialName("Destination") val destination: String? = null,
    @SerialName("Fee") val fee: String? = null,
    @SerialName("Flags") val flags: Int? = null,
    @SerialName("LastLedgerSequence") val lastLedgerSequence: Int? = null,
    @SerialName("Sequence") val sequence: Int? = null,
    @SerialName("SigningPubKey") val signingPubKey: String? = null,
    @SerialName("TransactionType") val transactionType: String? = null,
    @SerialName("TxnSignature") val txnSignature: String? = null,
)
