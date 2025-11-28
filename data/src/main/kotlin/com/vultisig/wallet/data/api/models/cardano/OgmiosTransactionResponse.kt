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
    @SerialName("id")
    val id: Int? = null,
    @SerialName("result")
    val result: OgmiosTransactionResult? = null,
    @SerialName("error")
    val error: RpcError? = null,
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