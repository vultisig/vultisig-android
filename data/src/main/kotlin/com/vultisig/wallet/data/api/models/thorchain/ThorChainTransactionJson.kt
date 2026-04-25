package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThorChainTransactionJson(
    @SerialName("code") val code: Int?,
    @SerialName("codespace") val codeSpace: String?,
    @SerialName("rawLog") val rawLog: String,
)
