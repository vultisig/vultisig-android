package com.vultisig.wallet.data.api.models.cardano

import com.vultisig.wallet.data.api.models.RpcError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OgmiosTransactionResponse(
    @SerialName("jsonrpc")
    val jsonrpc: String? = null,
    @SerialName("method")
    val method: String? = null,
    @SerialName("result")
    val result: OgmiosTransactionResult? = null,
    @SerialName("error")
    val error: OgmiosError? = null,
)

@Serializable
data class OgmiosTransactionResult(
    @SerialName("transaction")
    val transaction: OgmiosTransaction,
)

@Serializable
data class OgmiosTransaction(
    @SerialName("id")
    val id: String,
)

@Serializable
data class OgmiosError(
    @SerialName("code")
    val code: Int? = null,
    @SerialName("message")
    val message: String? = null,
    @SerialName("data")
    val data: OgmiosErrorData? = null
)

@Serializable
data class OgmiosErrorData(
    @SerialName("unknownOutputReferences")
    val unknownOutputReferences: List<UnknownOutputReference> = emptyList()
)

@Serializable
data class UnknownOutputReference(
    @SerialName("transaction")
    val transaction: UnknownOutputTransaction? = null,
    @SerialName("index")
    val index: Int? = null
)

@Serializable
data class UnknownOutputTransaction(
    @SerialName("id")
    val id: String? = null
)


