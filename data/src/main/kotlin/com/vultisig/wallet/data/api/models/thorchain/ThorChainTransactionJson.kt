package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Parsed transaction result from a THORChain broadcast or query response. */
@Serializable
data class ThorChainTransactionJson(
    @SerialName("code") val code: Int?,
    @SerialName("codespace") val codeSpace: String?,
    @SerialName("raw_log") val rawLog: String,
)
