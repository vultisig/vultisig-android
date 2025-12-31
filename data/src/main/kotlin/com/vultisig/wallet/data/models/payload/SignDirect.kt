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