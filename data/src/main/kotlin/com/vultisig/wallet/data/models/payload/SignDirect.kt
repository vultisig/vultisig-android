package com.vultisig.wallet.data.models.payload

import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


@Serializable
data class SignDirect(
    @SerialName("body_bytes")
    val bodyBytes: String,
    @SerialName("auth_info_bytes")
    val authInfoBytes: String,
    @SerialName("chain_id")
    val chainId: String,
    @SerialName("account_number")
    val accountNumber: String,
)

@Serializable
data class NormalizedSignDirect(
    val chainId: String,
    val accountNumber: String,
    val sequence: String,
    val memo: String = "",
    val messages: List<NormalizedMessage>,
    val fee: NormalizedFee
)

@Serializable
data class NormalizedMessage(
    val typeUrl: String,
    val value: String
)

@Serializable
data class NormalizedFee(
    val amount: List<NormalizedCoin>,
    val gasLimit: String
)

@Serializable
data class NormalizedCoin(
    val denom: String,
    val amount: String
)

fun SignDirectProto.toNormalizedSignDirect(): NormalizedSignDirect {
    // Decode bodyBytes
    val bodyBytesDecoded = android.util.Base64.decode(
        this.bodyBytes,
        android.util.Base64.DEFAULT
    )
    val bodyBytesString = bodyBytesDecoded.toString(Charsets.UTF_8)
    val bodyJson =
        kotlinx.serialization.json.Json.decodeFromString<kotlinx.serialization.json.JsonObject>(bodyBytesString)

    // Decode authInfoBytes
    val authInfoBytesDecoded = android.util.Base64.decode(
        this.authInfoBytes,
        android.util.Base64.DEFAULT
    )
    val authInfoBytesString = authInfoBytesDecoded.toString(Charsets.UTF_8)
    val authInfoJson =
        kotlinx.serialization.json.Json.decodeFromString<kotlinx.serialization.json.JsonObject>(authInfoBytesString)

    // Extract messages
    val messagesJsonArray =
        bodyJson["messages"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
    val messages = messagesJsonArray.map { msgElement ->
        val msgObject = msgElement.jsonObject
        NormalizedMessage(
            typeUrl = msgObject["typeUrl"]?.jsonPrimitive?.content ?: "",
            value = msgObject["value"]?.jsonPrimitive?.content ?: ""
        )
    }

    // Extract fee
    val feeObject = authInfoJson["fee"]?.jsonObject
    val amountJsonArray =
        feeObject?.get("amount")?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
    val amount = amountJsonArray.map { coinElement ->
        val coinObject = coinElement.jsonObject
        NormalizedCoin(
            denom = coinObject["denom"]?.jsonPrimitive?.content ?: "",
            amount = coinObject["amount"]?.jsonPrimitive?.content ?: ""
        )
    }
    val gasLimit = feeObject?.get("gasLimit")?.jsonPrimitive?.content ?: ""

    val fee = NormalizedFee(
        amount = amount,
        gasLimit = gasLimit
    )

    val memo = bodyJson["memo"]?.jsonPrimitive?.content ?: ""
    val sequence = authInfoJson["signerInfos"]?.jsonArray?.getOrNull(0)?.jsonObject
        ?.get("sequence")?.jsonPrimitive?.content ?: ""

    return NormalizedSignDirect(
        chainId = this.chainId,
        accountNumber = this.accountNumber,
        sequence = sequence,
        memo = memo,
        messages = messages,
        fee = fee
    )
}